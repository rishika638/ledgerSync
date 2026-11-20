package com.ledgersync.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ledgersync.audit.service.AuditService;
import com.ledgersync.common.id.IdGenerator;
import com.ledgersync.finance.model.*;
import com.ledgersync.finance.repository.FinanceImportBatchRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.finance.repository.ReconciliationMatchRepository;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.reconciliation.model.*;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ThreeWayReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ThreeWayReconciliationService.class);

    private final FinanceRecordRepository recordRepository;
    private final FinanceImportBatchRepository batchRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationMatchRepository matchRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ThreeWayReconciliationService(FinanceRecordRepository recordRepository,
                                        FinanceImportBatchRepository batchRepository,
                                        ReconciliationRunRepository runRepository,
                                        ReconciliationMatchRepository matchRepository,
                                        DiscrepancyRepository discrepancyRepository,
                                        AuditService auditService,
                                        ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.batchRepository = batchRepository;
        this.runRepository = runRepository;
        this.matchRepository = matchRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReconciliationRunEntity executeThreeWayReconciliation() {
        List<FinanceImportBatchEntity> inputBatches = Arrays.stream(FinanceSourceType.values())
                .map(source -> batchRepository.findFirstBySourceTypeAndStatusOrderByCreatedAtDesc(source, "COMPLETED")
                        .orElseThrow(() -> new IllegalStateException(
                                "No completed " + source + " import batch is available for reconciliation.")))
                .toList();
        return executeThreeWayReconciliation(inputBatches);
    }

    @Transactional
    public ReconciliationRunEntity executeThreeWayReconciliation(List<FinanceImportBatchEntity> inputBatches) {
        Map<FinanceSourceType, FinanceImportBatchEntity> batchesBySource = new EnumMap<>(FinanceSourceType.class);
        for (FinanceImportBatchEntity batch : inputBatches) {
            if (!"COMPLETED".equals(batch.getStatus()) ||
                    batchesBySource.putIfAbsent(batch.getSourceType(), batch) != null) {
                throw new IllegalArgumentException("Select exactly one completed batch for each finance source.");
            }
        }
        for (FinanceSourceType source : FinanceSourceType.values()) {
            if (!batchesBySource.containsKey(source)) {
                throw new IllegalArgumentException("A completed " + source + " batch is required.");
            }
        }

        long startTime = System.currentTimeMillis();
        String runId = IdGenerator.reconciliationRunId();
        Instant startInstant = Instant.now();

        log.info("Starting deterministic 3-way financial reconciliation run: {}", runId);

        ReconciliationRunEntity run = new ReconciliationRunEntity(
                runId, PaymentProvider.RAZORPAY, ReconciliationRunEntity.RunStatus.STARTED, startInstant
        );
        run.setRunType("THREE_WAY_SETTLEMENT");
        try {
            run.setInputBatchIds(objectMapper.writeValueAsString(
                    inputBatches.stream().map(FinanceImportBatchEntity::getId).toList()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not record reconciliation input batches", e);
        }
        run = runRepository.saveAndFlush(run);

        List<FinanceRecordEntity> ledgerRecords = recordRepository.findByBatch(batchesBySource.get(FinanceSourceType.LEDGER));
        List<FinanceRecordEntity> gatewayRecords = recordRepository.findByBatch(batchesBySource.get(FinanceSourceType.RAZORPAY));
        List<FinanceRecordEntity> bankRecords = recordRepository.findByBatch(batchesBySource.get(FinanceSourceType.BANK));

        ledgerRecords.forEach(record -> record.setStatus(FinanceRecordStatus.UNRECONCILED));
        gatewayRecords.forEach(record -> record.setStatus(FinanceRecordStatus.UNRECONCILED));
        bankRecords.forEach(record -> record.setStatus(FinanceRecordStatus.UNRECONCILED));

        int totalLedger = ledgerRecords.size();
        int totalGateway = gatewayRecords.size();
        int totalBank = bankRecords.size();

        run.setTotalLedgerRecords(totalLedger);
        run.setTotalGatewayRecords(totalGateway);
        run.setTotalBankRecords(totalBank);
        run.setTotalLocalChecked(totalLedger);
        run.setTotalRemoteChecked(totalGateway + totalBank);

        // Index Gateway records by order_reference, payment_id, and check duplicates
        Map<String, List<FinanceRecordEntity>> gatewayByOrder = new HashMap<>();
        Map<Long, List<FinanceRecordEntity>> gatewayByAmount = new HashMap<>();
        for (FinanceRecordEntity rzp : gatewayRecords) {
            if (rzp.getOrderReference() != null) {
                gatewayByOrder.computeIfAbsent(rzp.getOrderReference(), k -> new ArrayList<>()).add(rzp);
            }
            gatewayByAmount.computeIfAbsent(rzp.getAmountMinor(), k -> new ArrayList<>()).add(rzp);
        }

        // Index Bank records by UTR/reference, order_reference, and amount
        Map<String, List<FinanceRecordEntity>> bankByUtr = new HashMap<>();
        Map<String, List<FinanceRecordEntity>> bankByOrder = new HashMap<>();
        Map<Long, List<FinanceRecordEntity>> bankByAmount = new HashMap<>();
        for (FinanceRecordEntity bnk : bankRecords) {
            if (bnk.getSecondaryReference() != null) {
                bankByUtr.computeIfAbsent(bnk.getSecondaryReference(), k -> new ArrayList<>()).add(bnk);
            }
            if (bnk.getOrderReference() != null) {
                bankByOrder.computeIfAbsent(bnk.getOrderReference(), k -> new ArrayList<>()).add(bnk);
            }
            bankByAmount.computeIfAbsent(bnk.getAmountMinor(), k -> new ArrayList<>()).add(bnk);
        }

        Set<String> matchedGatewayIds = new HashSet<>();
        Set<String> matchedBankIds = new HashSet<>();

        List<ReconciliationMatchEntity> matchesToSave = new ArrayList<>();
        List<DiscrepancyEntity> discrepanciesToSave = new ArrayList<>();

        long unmatchedExposurePaise = 0L;

        // Iterate through all Ledger records
        for (FinanceRecordEntity ledger : ledgerRecords) {
            String orderId = ledger.getOrderReference();
            List<FinanceRecordEntity> rzpCandidates = gatewayByOrder.get(orderId);

            if (rzpCandidates == null || rzpCandidates.isEmpty()) {
                List<FinanceRecordEntity> amountCandidates = gatewayByAmount.get(ledger.getAmountMinor());
                boolean ambiguous = amountCandidates != null && amountCandidates.size() > 1;

                if (ambiguous) {
                    DiscrepancyEntity d = createDiscrepancy(
                            run, ledger.getId(), null, PaymentProvider.RAZORPAY,
                            DiscrepancyType.AMBIGUOUS_MATCH, DiscrepancySeverity.MEDIUM,
                            ledger.getAmountMinor(), ledger.getAmountMinor(), 0L,
                            buildEvidenceJson("AMBIGUOUS_MATCH", ledger, null, null,
                                    "Multiple identical transactions share exact amount ₹" + formatAmount(ledger.getAmountMinor()) +
                                    " without unique counterparty identifier. Matching engine refused to force match.")
                    );
                    d.setLedgerRecordId(ledger.getId());
                    discrepanciesToSave.add(d);
                    unmatchedExposurePaise += ledger.getAmountMinor();
                    ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                } else {
                    // MISSING_GATEWAY
                    DiscrepancyEntity d = createDiscrepancy(
                            run, ledger.getId(), null, PaymentProvider.RAZORPAY,
                            DiscrepancyType.MISSING_GATEWAY, DiscrepancySeverity.HIGH,
                            ledger.getAmountMinor(), ledger.getAmountMinor(), 0L,
                            buildEvidenceJson("MISSING_GATEWAY", ledger, null, null,
                                    "Merchant ledger captured order " + orderId + " (" + formatAmount(ledger.getAmountMinor()) +
                                    " INR), but no corresponding Razorpay settlement record exists.")
                    );
                    d.setLedgerRecordId(ledger.getId());
                    discrepanciesToSave.add(d);
                    unmatchedExposurePaise += ledger.getAmountMinor();
                    ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                }
                continue;
            }

            // Check DUPLICATE_RECORD in gateway
            if (rzpCandidates.size() > 1) {
                FinanceRecordEntity rzp1 = rzpCandidates.get(0);
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp1.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.DUPLICATE_RECORD, DiscrepancySeverity.HIGH,
                        rzp1.getAmountMinor(), ledger.getAmountMinor(), rzp1.getAmountMinor() * rzpCandidates.size(),
                        buildEvidenceJson("DUPLICATE_RECORD", ledger, rzp1, null,
                                "Multiple duplicate settlement records found for Order " + orderId +
                                " in Razorpay settlement report (" + rzpCandidates.size() + " occurrences).")
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp1.getId());
                discrepanciesToSave.add(d);
                unmatchedExposurePaise += rzp1.getAmountMinor();
                ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                for (FinanceRecordEntity r : rzpCandidates) {
                    r.setStatus(FinanceRecordStatus.DISCREPANCY);
                    matchedGatewayIds.add(r.getId());
                }
                continue;
            }

            FinanceRecordEntity rzp = rzpCandidates.get(0);
            if (!matchedGatewayIds.add(rzp.getId())) {
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.AMBIGUOUS_MATCH, DiscrepancySeverity.MEDIUM,
                        ledger.getAmountMinor(), ledger.getAmountMinor(), rzp.getAmountMinor(),
                        buildEvidenceJson("AMBIGUOUS_MATCH", ledger, rzp, null,
                                "A previously matched gateway record also references order " + orderId +
                                ". The engine refused to reuse one settlement for multiple ledger entries.")
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp.getId());
                discrepanciesToSave.add(d);
                ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                continue;
            }

            // 1. Amount Check
            long ledgerGross = ledger.getAmountMinor();
            long rzpGross = rzp.getAmountMinor();

            if (ledgerGross != rzpGross) {
                // Check if Partial Settlement
                if (rzpGross < ledgerGross) {
                    DiscrepancyEntity d = createDiscrepancy(
                            run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                            DiscrepancyType.PARTIAL_SETTLEMENT, DiscrepancySeverity.HIGH,
                            ledgerGross - rzpGross, ledgerGross, rzpGross,
                            buildEvidenceJson("PARTIAL_SETTLEMENT", ledger, rzp, null,
                                    "Razorpay settled partial tranche of ₹" + formatAmount(rzpGross) +
                                    " against ledger gross amount of ₹" + formatAmount(ledgerGross) +
                                    ". Unsettled balance: ₹" + formatAmount(ledgerGross - rzpGross))
                    );
                    d.setLedgerRecordId(ledger.getId());
                    d.setGatewayRecordId(rzp.getId());
                    discrepanciesToSave.add(d);
                    unmatchedExposurePaise += (ledgerGross - rzpGross);
                    ledger.setStatus(FinanceRecordStatus.PARTIALLY_MATCHED);
                    rzp.setStatus(FinanceRecordStatus.PARTIALLY_MATCHED);
                } else {
                    // AMOUNT_MISMATCH
                    DiscrepancyEntity d = createDiscrepancy(
                            run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                            DiscrepancyType.AMOUNT_MISMATCH, DiscrepancySeverity.HIGH,
                            Math.abs(rzpGross - ledgerGross), ledgerGross, rzpGross,
                            buildEvidenceJson("AMOUNT_MISMATCH", ledger, rzp, null,
                                    "Ledger amount ₹" + formatAmount(ledgerGross) +
                                    " does not equal Razorpay gross amount ₹" + formatAmount(rzpGross) +
                                    ". Difference: ₹" + formatAmount(Math.abs(rzpGross - ledgerGross)))
                    );
                    d.setLedgerRecordId(ledger.getId());
                    d.setGatewayRecordId(rzp.getId());
                    discrepanciesToSave.add(d);
                    unmatchedExposurePaise += Math.abs(rzpGross - ledgerGross);
                    ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                    rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                }
                continue;
            }

            // 2. Fee / Tax Invariant: Net = Gross - Fee - Tax
            long expectedContractualFee = BigDecimal.valueOf(rzpGross)
                    .multiply(new BigDecimal("0.02"))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            long feeVariance = Math.abs(rzp.getFeeMinor() - expectedContractualFee);
            if (feeVariance > 0) {
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.FEE_MISMATCH, DiscrepancySeverity.MEDIUM,
                        feeVariance, expectedContractualFee, rzp.getFeeMinor(),
                        buildEvidenceJson("FEE_MISMATCH", ledger, rzp, null,
                                "Razorpay deducted fee ₹" + formatAmount(rzp.getFeeMinor()) +
                                " exceeding contracted fee schedule ₹" + formatAmount(expectedContractualFee) +
                                ". Net settlement reduced by ₹" + formatAmount(feeVariance))
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp.getId());
                discrepanciesToSave.add(d);
                unmatchedExposurePaise += feeVariance;
                ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                continue;
            }

            long expectedTax = BigDecimal.valueOf(rzp.getFeeMinor())
                    .multiply(new BigDecimal("0.18"))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            if (rzp.getTaxMinor() != expectedTax) {
                long taxVariance = Math.abs(rzp.getTaxMinor() - expectedTax);
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.TAX_MISMATCH, DiscrepancySeverity.MEDIUM,
                        taxVariance, expectedTax, rzp.getTaxMinor(),
                        buildEvidenceJson("TAX_MISMATCH", ledger, rzp, null,
                                "GST on the Razorpay fee should be ₹" + formatAmount(expectedTax) +
                                " at 18%; the report records ₹" + formatAmount(rzp.getTaxMinor()) + ".")
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp.getId());
                discrepanciesToSave.add(d);
                unmatchedExposurePaise += taxVariance;
                ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                continue;
            }

            long expectedNet = Math.subtractExact(Math.subtractExact(rzpGross, rzp.getFeeMinor()), rzp.getTaxMinor());
            if (rzp.getNetAmountMinor() != expectedNet) {
                long netVariance = Math.abs(rzp.getNetAmountMinor() - expectedNet);
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.AMOUNT_MISMATCH, DiscrepancySeverity.HIGH,
                        netVariance, expectedNet, rzp.getNetAmountMinor(),
                        buildEvidenceJson("AMOUNT_MISMATCH", ledger, rzp, null,
                                "Razorpay net amount must equal gross less fee and GST: expected ₹" +
                                formatAmount(expectedNet) + ", reported ₹" + formatAmount(rzp.getNetAmountMinor()) + ".")
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp.getId());
                discrepanciesToSave.add(d);
                unmatchedExposurePaise += netVariance;
                ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                continue;
            }

            // 3. Bank Statement Matching
            String utr = rzp.getSecondaryReference();
            FinanceRecordEntity bankRecord = null;
            List<FinanceRecordEntity> referenceCandidates = utr != null ? bankByUtr.get(utr) : null;
            if (referenceCandidates == null && orderId != null) {
                referenceCandidates = bankByOrder.get(orderId);
            }
            if (referenceCandidates != null) {
                List<FinanceRecordEntity> availableCandidates = referenceCandidates.stream()
                        .filter(candidate -> !matchedBankIds.contains(candidate.getId()))
                        .toList();
                if (availableCandidates.size() == 1) {
                    bankRecord = availableCandidates.get(0);
                } else if (referenceCandidates.size() > 1 || availableCandidates.isEmpty()) {
                    DiscrepancyEntity d = createDiscrepancy(
                            run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                            DiscrepancyType.AMBIGUOUS_MATCH, DiscrepancySeverity.MEDIUM,
                            rzp.getNetAmountMinor(), rzp.getNetAmountMinor(), 0L,
                            buildEvidenceJson("AMBIGUOUS_MATCH", ledger, rzp, null,
                                    "The bank reference does not identify one unused credit uniquely. The engine refused to reuse or arbitrarily select a bank transaction.")
                    );
                    d.setLedgerRecordId(ledger.getId());
                    d.setGatewayRecordId(rzp.getId());
                    discrepanciesToSave.add(d);
                    ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                    rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                    continue;
                }
            }

            if (bankRecord == null) {
                // Check if bank has amount credit under corrupted reference
                List<FinanceRecordEntity> amountCandidates = bankByAmount.get(rzp.getNetAmountMinor());
                if (amountCandidates != null && !amountCandidates.isEmpty()) {
                    List<FinanceRecordEntity> availableCandidates = amountCandidates.stream()
                            .filter(candidate -> !matchedBankIds.contains(candidate.getId()))
                            .toList();

                    if (availableCandidates.size() == 1) {
                        FinanceRecordEntity candidate = availableCandidates.get(0);
                        // REFERENCE_MISMATCH
                        matchedBankIds.add(candidate.getId());
                        DiscrepancyEntity d = createDiscrepancy(
                                run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                                DiscrepancyType.REFERENCE_MISMATCH, DiscrepancySeverity.LOW,
                                0L, rzp.getNetAmountMinor(), candidate.getAmountMinor(),
                                buildEvidenceJson("REFERENCE_MISMATCH", ledger, rzp, candidate,
                                        "Bank credit of ₹" + formatAmount(candidate.getAmountMinor()) +
                                        " matched net settlement amount, but bank statement narration/UTR is missing order reference " + orderId)
                        );
                        d.setLedgerRecordId(ledger.getId());
                        d.setGatewayRecordId(rzp.getId());
                        d.setBankRecordId(candidate.getId());
                        discrepanciesToSave.add(d);
                        ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                        rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                        candidate.setStatus(FinanceRecordStatus.DISCREPANCY);
                        continue;
                    } else if (availableCandidates.size() > 1) {
                        DiscrepancyEntity d = createDiscrepancy(
                                run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                                DiscrepancyType.AMBIGUOUS_MATCH, DiscrepancySeverity.MEDIUM,
                                rzp.getNetAmountMinor(), rzp.getNetAmountMinor(), 0L,
                                buildEvidenceJson("AMBIGUOUS_MATCH", ledger, rzp, null,
                                        "Multiple unused bank credits share the settlement amount but have no unique reference. The engine refused to force a match.")
                        );
                        d.setLedgerRecordId(ledger.getId());
                        d.setGatewayRecordId(rzp.getId());
                        discrepanciesToSave.add(d);
                        ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                        rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                        continue;
                    }
                }

                // MISSING_BANK
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.MISSING_BANK, DiscrepancySeverity.CRITICAL,
                        rzp.getNetAmountMinor(), rzp.getNetAmountMinor(), 0L,
                        buildEvidenceJson("MISSING_BANK", ledger, rzp, null,
                                "Gateway confirmed settlement of ₹" + formatAmount(rzp.getNetAmountMinor()) +
                                " for order " + orderId + " (UTR: " + utr + "), but zero corresponding credit appears on bank statement.")
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp.getId());
                discrepanciesToSave.add(d);
                unmatchedExposurePaise += rzp.getNetAmountMinor();
                ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                continue;
            }

            matchedBankIds.add(bankRecord.getId());

            if (!ledger.getCurrency().equals(rzp.getCurrency()) ||
                    !rzp.getCurrency().equals(bankRecord.getCurrency())) {
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.AMOUNT_MISMATCH, DiscrepancySeverity.HIGH,
                        rzp.getNetAmountMinor(), rzp.getNetAmountMinor(), bankRecord.getAmountMinor(),
                        buildEvidenceJson("AMOUNT_MISMATCH", ledger, rzp, bankRecord,
                                "Source currencies do not match (Ledger " + ledger.getCurrency() +
                                ", Razorpay " + rzp.getCurrency() + ", Bank " + bankRecord.getCurrency() + ").")
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp.getId());
                d.setBankRecordId(bankRecord.getId());
                discrepanciesToSave.add(d);
                ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                bankRecord.setStatus(FinanceRecordStatus.DISCREPANCY);
                continue;
            }

            if (bankRecord.getAmountMinor() != rzp.getNetAmountMinor()) {
                long amountVariance = Math.abs(bankRecord.getAmountMinor() - rzp.getNetAmountMinor());
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.AMOUNT_MISMATCH, DiscrepancySeverity.HIGH,
                        amountVariance, rzp.getNetAmountMinor(), bankRecord.getAmountMinor(),
                        buildEvidenceJson("AMOUNT_MISMATCH", ledger, rzp, bankRecord,
                                "Bank credit ₹" + formatAmount(bankRecord.getAmountMinor()) +
                                " does not equal Razorpay net settlement ₹" + formatAmount(rzp.getNetAmountMinor()) + ".")
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp.getId());
                d.setBankRecordId(bankRecord.getId());
                discrepanciesToSave.add(d);
                unmatchedExposurePaise += amountVariance;
                ledger.setStatus(FinanceRecordStatus.DISCREPANCY);
                rzp.setStatus(FinanceRecordStatus.DISCREPANCY);
                bankRecord.setStatus(FinanceRecordStatus.DISCREPANCY);
                continue;
            }

            // 4. Check Settlement SLA Window
            Duration settleDuration = Duration.between(ledger.getTransactionTimestamp(), rzp.getSettlementTimestamp());
            if (settleDuration.toHours() > 72) { // > T+2
                DiscrepancyEntity d = createDiscrepancy(
                        run, ledger.getId(), rzp.getRecordReference(), PaymentProvider.RAZORPAY,
                        DiscrepancyType.DELAYED_SETTLEMENT, DiscrepancySeverity.LOW,
                        0L, rzp.getNetAmountMinor(), bankRecord.getAmountMinor(),
                        buildEvidenceJson("DELAYED_SETTLEMENT", ledger, rzp, bankRecord,
                                "Settlement took " + (settleDuration.toHours() / 24) + " days (" +
                                settleDuration.toHours() + " hours), exceeding contractual T+2 SLA window.")
                );
                d.setLedgerRecordId(ledger.getId());
                d.setGatewayRecordId(rzp.getId());
                d.setBankRecordId(bankRecord.getId());
                discrepanciesToSave.add(d);
                // Also create match since funds arrived
                ReconciliationMatchEntity match = new ReconciliationMatchEntity(
                        IdGenerator.reconciliationMatchId(), run.getId(), MatchType.EXACT_THREE_WAY,
                        MatchStatus.AUTOMATIC_CONFIRMED, ledger, rzp, bankRecord,
                        ledgerGross, rzpGross, bankRecord.getAmountMinor(),
                        rzp.getFeeMinor(), rzp.getTaxMinor(), new BigDecimal("92.00"),
                        "Matched with delayed settlement SLA violation"
                );
                matchesToSave.add(match);
                ledger.setStatus(FinanceRecordStatus.MATCHED);
                rzp.setStatus(FinanceRecordStatus.MATCHED);
                bankRecord.setStatus(FinanceRecordStatus.MATCHED);
                continue;
            }

            // CLEAN EXACT THREE-WAY MATCH!
            ReconciliationMatchEntity match = new ReconciliationMatchEntity(
                    IdGenerator.reconciliationMatchId(), run.getId(), MatchType.EXACT_THREE_WAY,
                    MatchStatus.AUTOMATIC_CONFIRMED, ledger, rzp, bankRecord,
                    ledgerGross, rzpGross, bankRecord.getAmountMinor(),
                    rzp.getFeeMinor(), rzp.getTaxMinor(), new BigDecimal("100.00"),
                    "Deterministic 3-way exact match verified: Ledger = Gateway Gross = Net + Fee + Tax = Bank Credit"
            );
            matchesToSave.add(match);
            ledger.setStatus(FinanceRecordStatus.MATCHED);
            rzp.setStatus(FinanceRecordStatus.MATCHED);
            bankRecord.setStatus(FinanceRecordStatus.MATCHED);
        }

        // Save records and matches
        recordRepository.saveAll(ledgerRecords);
        recordRepository.saveAll(gatewayRecords);
        recordRepository.saveAll(bankRecords);
        matchRepository.saveAll(matchesToSave);
        discrepancyRepository.saveAll(discrepanciesToSave);

        // Compute metrics
        int matchedCount = matchesToSave.size();
        int discrepanciesCount = discrepanciesToSave.size();
        long durationMs = System.currentTimeMillis() - startTime;

        BigDecimal matchRate = totalLedger > 0
                ? BigDecimal.valueOf(matchedCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalLedger), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        run.setStatus(ReconciliationRunEntity.RunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        run.setMatchedRecordsCount(matchedCount);
        run.setUnmatchedRecordsCount(discrepanciesCount);
        run.setDiscrepanciesCount(discrepanciesCount);
        run.setMatchRate(matchRate);
        run.setPrecisionRate(null);
        run.setRecallRate(null);
        run.setUnmatchedAmountMinor(unmatchedExposurePaise);
        run.setProcessingDurationMs(durationMs);

        run = runRepository.saveAndFlush(run);

        // Record Audit Event
        auditService.record(
                "RECONCILIATION_RUN_COMPLETED",
                "system",
                "RECONCILIATION_RUN",
                run.getId(),
                "status=STARTED",
                "status=COMPLETED,matched=" + matchedCount + ",discrepancies=" + discrepanciesCount +
                ",matchRate=" + matchRate + "%,duration=" + durationMs + "ms"
        );

        log.info("Reconciliation run {} finished: {} matches, {} discrepancies, matchRate {}%, duration {}ms",
                runId, matchedCount, discrepanciesCount, matchRate, durationMs);

        return run;
    }

    private DiscrepancyEntity createDiscrepancy(ReconciliationRunEntity run, String paymentId,
                                               String providerPaymentId, PaymentProvider provider,
                                               DiscrepancyType type, DiscrepancySeverity severity,
                                               long amountMinor, long expectedAmountMinor, long actualAmountMinor,
                                               String evidenceJson) {
        DiscrepancyEntity d = new DiscrepancyEntity(
                IdGenerator.discrepancyId(), run, paymentId, providerPaymentId,
                provider, type, severity, evidenceJson, DiscrepancyStatus.OPEN,
                Instant.now(), Instant.now()
        );
        d.setAmountMinor(amountMinor);
        d.setExpectedAmountMinor(expectedAmountMinor);
        d.setActualAmountMinor(actualAmountMinor);
        d.setCurrency("INR");
        return d;
    }

    private String buildEvidenceJson(String title, FinanceRecordEntity ledger,
                                    FinanceRecordEntity gateway, FinanceRecordEntity bank,
                                    String rootCause) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("title", title);
        evidence.put("root_cause_explanation", rootCause);

        if (ledger != null) {
            Map<String, Object> lMap = new LinkedHashMap<>();
            lMap.put("record_id", ledger.getId());
            lMap.put("ledger_reference", ledger.getRecordReference());
            lMap.put("order_id", ledger.getOrderReference());
            lMap.put("amount_inr", formatAmount(ledger.getAmountMinor()));
            lMap.put("amount_paise", ledger.getAmountMinor());
            lMap.put("timestamp", ledger.getTransactionTimestamp().toString());
            evidence.put("ledger_evidence", lMap);
        }

        if (gateway != null) {
            Map<String, Object> gMap = new LinkedHashMap<>();
            gMap.put("record_id", gateway.getId());
            gMap.put("payment_id", gateway.getRecordReference());
            gMap.put("order_id", gateway.getOrderReference());
            gMap.put("gross_amount_inr", formatAmount(gateway.getAmountMinor()));
            gMap.put("fee_inr", formatAmount(gateway.getFeeMinor()));
            gMap.put("tax_inr", formatAmount(gateway.getTaxMinor()));
            gMap.put("net_settled_inr", formatAmount(gateway.getNetAmountMinor()));
            gMap.put("arn_utr", gateway.getSecondaryReference());
            gMap.put("settled_at", gateway.getSettlementTimestamp() != null ? gateway.getSettlementTimestamp().toString() : null);
            evidence.put("gateway_evidence", gMap);
        }

        if (bank != null) {
            Map<String, Object> bMap = new LinkedHashMap<>();
            bMap.put("record_id", bank.getId());
            bMap.put("bank_txn_id", bank.getRecordReference());
            bMap.put("credit_amount_inr", formatAmount(bank.getAmountMinor()));
            bMap.put("reference_utr", bank.getSecondaryReference());
            bMap.put("value_date", bank.getTransactionTimestamp().toString());
            bMap.put("description", bank.getRawMetadata());
            evidence.put("bank_evidence", bMap);
        }

        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize reconciliation evidence.", e);
        }
    }

    private String formatAmount(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, 2).toPlainString();
    }
}
