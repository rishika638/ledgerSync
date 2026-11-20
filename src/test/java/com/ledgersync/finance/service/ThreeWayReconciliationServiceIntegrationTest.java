package com.ledgersync.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.finance.model.*;
import com.ledgersync.finance.repository.FinanceImportBatchRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.finance.repository.ReconciliationMatchRepository;
import com.ledgersync.reconciliation.model.DiscrepancyType;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ThreeWayReconciliationServiceIntegrationTest {

    @Autowired private ThreeWayReconciliationService reconciliationService;
    @Autowired private FinanceImportBatchRepository batchRepository;
    @Autowired private FinanceRecordRepository recordRepository;
    @Autowired private ReconciliationMatchRepository matchRepository;
    @Autowired private DiscrepancyRepository discrepancyRepository;
    @Autowired private ReconciliationRunRepository runRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void reconcilesOnlyLatestSourceBatchesAndPersistsTheirSnapshot() throws Exception {
        createBatch(FinanceSourceType.LEDGER, "old-ledger", List.of(
                record("old-ledger-row", FinanceSourceType.LEDGER, "PAY-OLD", "ORD-OLD", null, 5000, 0, 0, 5000)));
        createBatch(FinanceSourceType.RAZORPAY, "old-gateway", List.of(
                record("old-gateway-row", FinanceSourceType.RAZORPAY, "RZP-OLD", "ORD-OLD", "UTR-OLD", 5000, 100, 18, 4882)));
        createBatch(FinanceSourceType.BANK, "old-bank", List.of(
                record("old-bank-row", FinanceSourceType.BANK, "BANK-OLD", "ORD-OLD", "UTR-OLD", 4882, 0, 0, 4882)));

        FinanceImportBatchEntity ledgerBatch = createBatch(FinanceSourceType.LEDGER, "current-ledger", List.of(
                record("current-ledger-row", FinanceSourceType.LEDGER, "PAY-1", "ORD-0001", null, 10000, 0, 0, 10000)));
        FinanceImportBatchEntity gatewayBatch = createBatch(FinanceSourceType.RAZORPAY, "current-gateway", List.of(
                record("current-gateway-row", FinanceSourceType.RAZORPAY, "RZP-1", "ORD-0001", "UTR-1", 10000, 200, 36, 9764)));
        FinanceImportBatchEntity bankBatch = createBatch(FinanceSourceType.BANK, "current-bank", List.of(
                record("current-bank-row", FinanceSourceType.BANK, "BANK-1", null, "UTR-1", 9764, 0, 0, 9764)));

        ReconciliationRunEntity run = reconciliationService.executeThreeWayReconciliation();

        assertEquals(1, run.getTotalLedgerRecords());
        assertEquals(1, run.getTotalGatewayRecords());
        assertEquals(1, run.getTotalBankRecords());
        assertEquals(1, run.getMatchedRecordsCount());
        assertEquals(0, run.getDiscrepanciesCount());
        assertEquals(List.of(ledgerBatch.getId(), gatewayBatch.getId(), bankBatch.getId()),
                objectMapper.readValue(run.getInputBatchIds(), new TypeReference<List<String>>() {}));
        assertEquals(1, matchRepository.findByRunId(run.getId()).size());
        assertEquals(0, discrepancyRepository.findByRunId(run.getId()).size());
    }

    @Test
    void refusesToReuseAmbiguousBankCredits() {
        createBatch(FinanceSourceType.LEDGER, "ambiguous-ledger", List.of(
                record("ambiguous-ledger-row", FinanceSourceType.LEDGER, "PAY-2", "ORD-0002", null, 10000, 0, 0, 10000)));
        createBatch(FinanceSourceType.RAZORPAY, "ambiguous-gateway", List.of(
                record("ambiguous-gateway-row", FinanceSourceType.RAZORPAY, "RZP-2", "ORD-0002", "UTR-2", 10000, 200, 36, 9764)));
        createBatch(FinanceSourceType.BANK, "ambiguous-bank", List.of(
                record("ambiguous-bank-row-1", FinanceSourceType.BANK, "BANK-2A", null, "UTR-2", 9764, 0, 0, 9764),
                record("ambiguous-bank-row-2", FinanceSourceType.BANK, "BANK-2B", null, "UTR-2", 9764, 0, 0, 9764)));

        ReconciliationRunEntity run = reconciliationService.executeThreeWayReconciliation();

        assertEquals(0, run.getMatchedRecordsCount());
        assertEquals(1, run.getDiscrepanciesCount());
        assertEquals(DiscrepancyType.AMBIGUOUS_MATCH,
                discrepancyRepository.findByRunId(run.getId()).get(0).getType());
        assertEquals(0, matchRepository.findByRunId(run.getId()).size());
    }

    @Test
    void classifiesGrossAmountMismatchWithoutCreatingAMatch() {
        createBatch(FinanceSourceType.LEDGER, "amount-ledger", List.of(
                record("amount-ledger-row", FinanceSourceType.LEDGER, "PAY-3", "ORD-0003", null, 10000, 0, 0, 10000)));
        createBatch(FinanceSourceType.RAZORPAY, "amount-gateway", List.of(
                record("amount-gateway-row", FinanceSourceType.RAZORPAY, "RZP-3", "ORD-0003", "UTR-3", 11000, 220, 40, 10740)));
        createBatch(FinanceSourceType.BANK, "amount-bank", List.of(
                record("amount-bank-row", FinanceSourceType.BANK, "BANK-3", null, "UTR-3", 10740, 0, 0, 10740)));

        ReconciliationRunEntity run = reconciliationService.executeThreeWayReconciliation();

        var exception = discrepancyRepository.findByRunId(run.getId()).get(0);
        assertEquals(DiscrepancyType.AMOUNT_MISMATCH, exception.getType());
        assertEquals(1000L, exception.getAmountMinor());
        assertEquals(0, run.getMatchedRecordsCount());
        assertTrue(matchRepository.findByRunId(run.getId()).isEmpty());
    }

    @Test
    void distinguishesMissingGatewayAndMissingBankRecords() {
        createBatch(FinanceSourceType.LEDGER, "missing-ledger", List.of(
                record("missing-gateway-ledger", FinanceSourceType.LEDGER, "PAY-4", "ORD-0004", null, 10000, 0, 0, 10000),
                record("missing-bank-ledger", FinanceSourceType.LEDGER, "PAY-5", "ORD-0005", null, 20000, 0, 0, 20000)));
        createBatch(FinanceSourceType.RAZORPAY, "missing-gateway", List.of(
                record("missing-bank-gateway", FinanceSourceType.RAZORPAY, "RZP-5", "ORD-0005", "UTR-5", 20000, 400, 72, 19528)));
        createBatch(FinanceSourceType.BANK, "missing-bank", List.of());

        ReconciliationRunEntity run = reconciliationService.executeThreeWayReconciliation();

        assertEquals(2, run.getDiscrepanciesCount());
        assertEquals(DiscrepancyType.MISSING_GATEWAY,
                discrepancyRepository.findByRunId(run.getId()).stream()
                        .filter(item -> "missing-gateway-ledger".equals(item.getLedgerRecordId()))
                        .findFirst().orElseThrow().getType());
        assertEquals(DiscrepancyType.MISSING_BANK,
                discrepancyRepository.findByRunId(run.getId()).stream()
                        .filter(item -> "missing-bank-ledger".equals(item.getLedgerRecordId()))
                        .findFirst().orElseThrow().getType());
    }

    @Test
    void identifiesDuplicateGatewayTransactionsInsteadOfMatchingOne() {
        createBatch(FinanceSourceType.LEDGER, "duplicate-ledger", List.of(
                record("duplicate-ledger-row", FinanceSourceType.LEDGER, "PAY-6", "ORD-0006", null, 10000, 0, 0, 10000)));
        createBatch(FinanceSourceType.RAZORPAY, "duplicate-gateway", List.of(
                record("duplicate-gateway-row-1", FinanceSourceType.RAZORPAY, "RZP-6A", "ORD-0006", "UTR-6A", 10000, 200, 36, 9764),
                record("duplicate-gateway-row-2", FinanceSourceType.RAZORPAY, "RZP-6B", "ORD-0006", "UTR-6B", 10000, 200, 36, 9764)));
        createBatch(FinanceSourceType.BANK, "duplicate-bank", List.of());

        ReconciliationRunEntity run = reconciliationService.executeThreeWayReconciliation();

        assertEquals(DiscrepancyType.DUPLICATE_RECORD,
                discrepancyRepository.findByRunId(run.getId()).get(0).getType());
        assertEquals(0, run.getMatchedRecordsCount());
        assertTrue(matchRepository.findByRunId(run.getId()).isEmpty());
    }

    @Test
    void classifiesPartialAndDelayedSettlementCases() {
        createBatch(FinanceSourceType.LEDGER, "partial-ledger", List.of(
                record("partial-ledger-row", FinanceSourceType.LEDGER, "PAY-7", "ORD-0007", null, 10000, 0, 0, 10000)));
        createBatch(FinanceSourceType.RAZORPAY, "partial-gateway", List.of(
                record("partial-gateway-row", FinanceSourceType.RAZORPAY, "RZP-7", "ORD-0007", "UTR-7", 6000, 120, 22, 5858)));
        createBatch(FinanceSourceType.BANK, "partial-bank", List.of());

        ReconciliationRunEntity partialRun = reconciliationService.executeThreeWayReconciliation();
        assertEquals(DiscrepancyType.PARTIAL_SETTLEMENT,
                discrepancyRepository.findByRunId(partialRun.getId()).get(0).getType());
        assertEquals(4000L, discrepancyRepository.findByRunId(partialRun.getId()).get(0).getAmountMinor());

        FinanceRecordEntity delayedGateway = record(
                "delayed-gateway-row", FinanceSourceType.RAZORPAY, "RZP-8", "ORD-0008", "UTR-8", 10000, 200, 36, 9764);
        delayedGateway.setSettlementTimestamp(delayedGateway.getTransactionTimestamp().plusSeconds(73 * 3600L));
        createBatch(FinanceSourceType.LEDGER, "delayed-ledger", List.of(
                record("delayed-ledger-row", FinanceSourceType.LEDGER, "PAY-8", "ORD-0008", null, 10000, 0, 0, 10000)));
        createBatch(FinanceSourceType.RAZORPAY, "delayed-gateway", List.of(delayedGateway));
        createBatch(FinanceSourceType.BANK, "delayed-bank", List.of(
                record("delayed-bank-row", FinanceSourceType.BANK, "BANK-8", null, "UTR-8", 9764, 0, 0, 9764)));

        ReconciliationRunEntity delayedRun = reconciliationService.executeThreeWayReconciliation();
        assertEquals(DiscrepancyType.DELAYED_SETTLEMENT,
                discrepancyRepository.findByRunId(delayedRun.getId()).get(0).getType());
        assertEquals(1, delayedRun.getMatchedRecordsCount());
        assertEquals(1, matchRepository.findByRunId(delayedRun.getId()).size());
    }

    @Test
    void repeatedRunsKeepSeparateMatchRowsAndPreserveEarlierRunResults() {
        createBatch(FinanceSourceType.LEDGER, "repeat-ledger", List.of(
                record("repeat-ledger-row", FinanceSourceType.LEDGER, "PAY-9", "ORD-0009", null, 10000, 0, 0, 10000)));
        createBatch(FinanceSourceType.RAZORPAY, "repeat-gateway", List.of(
                record("repeat-gateway-row", FinanceSourceType.RAZORPAY, "RZP-9", "ORD-0009", "UTR-9", 10000, 200, 36, 9764)));
        createBatch(FinanceSourceType.BANK, "repeat-bank", List.of(
                record("repeat-bank-row", FinanceSourceType.BANK, "BANK-9", null, "UTR-9", 9764, 0, 0, 9764)));

        ReconciliationRunEntity firstRun = reconciliationService.executeThreeWayReconciliation();
        ReconciliationRunEntity secondRun = reconciliationService.executeThreeWayReconciliation();

        assertNotEquals(firstRun.getId(), secondRun.getId());
        assertEquals(1, matchRepository.findByRunId(firstRun.getId()).size());
        assertEquals(1, matchRepository.findByRunId(secondRun.getId()).size());
        assertEquals(1, runRepository.findById(firstRun.getId()).orElseThrow().getMatchedRecordsCount());
        assertEquals(1, runRepository.findById(secondRun.getId()).orElseThrow().getMatchedRecordsCount());
    }

    private FinanceImportBatchEntity createBatch(FinanceSourceType source, String suffix,
                                                 List<FinanceRecordEntity> records) {
        FinanceImportBatchEntity batch = new FinanceImportBatchEntity(
                "batch-" + suffix, suffix, source, suffix + ".csv",
                records.size(), records.size(), 0, "COMPLETED", null);
        batchRepository.saveAndFlush(batch);
        for (FinanceRecordEntity record : records) {
            record.setBatch(batch);
        }
        recordRepository.saveAll(records);
        recordRepository.flush();
        return batch;
    }

    private FinanceRecordEntity record(String id, FinanceSourceType source, String reference,
                                       String order, String secondary, long amount, long fee,
                                       long tax, long net) {
        Instant timestamp = Instant.parse("2026-11-15T10:00:00Z");
        return new FinanceRecordEntity(id, null, source, reference, order, secondary,
                "INR", amount, fee, tax, net, timestamp,
                source == FinanceSourceType.RAZORPAY ? timestamp.plusSeconds(3600) : null,
                FinanceRecordStatus.UNRECONCILED, "{}");
    }
}
