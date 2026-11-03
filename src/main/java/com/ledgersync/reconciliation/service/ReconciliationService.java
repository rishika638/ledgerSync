package com.ledgersync.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.common.id.IdGenerator;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.observability.MetricsConfig;
import com.ledgersync.payment.model.PaymentTransactionEntity;
import com.ledgersync.payment.repository.PaymentTransactionRepository;
import com.ledgersync.reconciliation.model.*;
import com.ledgersync.reconciliation.provider.PaymentProviderClient;
import com.ledgersync.reconciliation.provider.RemotePaymentRecord;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final Map<PaymentProvider, PaymentProviderClient> providerClients;
    private final PaymentTransactionRepository paymentRepository;
    private final ReconciliationRunRepository runRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final MetricsConfig metrics;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public ReconciliationService(
            List<PaymentProviderClient> clientList,
            PaymentTransactionRepository paymentRepository,
            ReconciliationRunRepository runRepository,
            DiscrepancyRepository discrepancyRepository,
            MetricsConfig metrics,
            ObjectMapper objectMapper,
            @Value("${ledgersync.reconciliation.batch-size:50}") int batchSize) {
        this.providerClients = clientList.stream()
                .collect(Collectors.toMap(PaymentProviderClient::getProvider, Function.identity()));
        this.paymentRepository = paymentRepository;
        this.runRepository = runRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${ledgersync.reconciliation.cron:0 */15 * * * *}")
    public void scheduledReconciliation() {
        log.info("Starting scheduled reconciliation for all configured providers");
        for (PaymentProvider provider : providerClients.keySet()) {
            try {
                runReconciliation(provider);
            } catch (Exception e) {
                log.error("Error running reconciliation for provider {}", provider, e);
            }
        }
    }

    @Transactional
    public ReconciliationRunEntity runReconciliation(PaymentProvider provider) {
        log.info("Triggering reconciliation run for provider: {}", provider);

        PaymentProviderClient client = providerClients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("No reconciliation client configured for provider: " + provider);
        }

        ReconciliationRunEntity run = new ReconciliationRunEntity(
                IdGenerator.reconciliationRunId(),
                provider,
                ReconciliationRunEntity.RunStatus.STARTED,
                Instant.now()
        );
        run = runRepository.saveAndFlush(run);

        int remoteCount = 0;
        int localCount = 0;
        int discrepancyCount = 0;

        try {
            Instant to = Instant.now();
            Instant from = to.minusSeconds(86400 * 7); // Last 7 days
            List<RemotePaymentRecord> remoteRecords = client.fetchSettledTransactions(from, to, batchSize);
            remoteCount = remoteRecords.size();

            for (RemotePaymentRecord remote : remoteRecords) {
                Optional<PaymentTransactionEntity> localOpt = paymentRepository
                        .findByProviderAndProviderPaymentId(provider, remote.providerPaymentId());

                if (localOpt.isEmpty()) {
                    // MISSING IN LOCAL: provider has payment settled, but local DB never ingested or processed webhook!
                    boolean recorded = recordDiscrepancyIfAbsent(
                            run,
                            null,
                            remote.providerPaymentId(),
                            provider,
                            DiscrepancyType.MISSING_IN_LOCAL,
                            DiscrepancySeverity.CRITICAL,
                            Map.of(
                                    "reason", "Payment exists and settled on remote provider, but missing in local LedgerSync database",
                                    "remotePaymentId", remote.providerPaymentId(),
                                    "remoteStatus", remote.status().name(),
                                    "remoteAmountCents", remote.amountCents(),
                                    "currency", remote.currency()
                            )
                    );
                    if (recorded) discrepancyCount++;
                } else {
                    localCount++;
                    PaymentTransactionEntity local = localOpt.get();

                    // STATUS MISMATCH CHECK
                    if (local.getStatus() != remote.status()) {
                        boolean recorded = recordDiscrepancyIfAbsent(
                                run,
                                local.getId(),
                                remote.providerPaymentId(),
                                provider,
                                DiscrepancyType.STATUS_MISMATCH,
                                DiscrepancySeverity.HIGH,
                                Map.of(
                                    "reason", "Status mismatch between local database and authoritative provider",
                                    "localStatus", local.getStatus().name(),
                                    "remoteStatus", remote.status().name()
                                )
                        );
                        if (recorded) discrepancyCount++;
                    }

                    // AMOUNT MISMATCH CHECK
                    if (local.getCapturedAmountCents() != remote.capturedAmountCents()) {
                        boolean recorded = recordDiscrepancyIfAbsent(
                                run,
                                local.getId(),
                                remote.providerPaymentId(),
                                provider,
                                DiscrepancyType.AMOUNT_MISMATCH,
                                DiscrepancySeverity.CRITICAL,
                                Map.of(
                                    "reason", "Captured amount mismatch between local database and remote provider",
                                    "localCapturedCents", local.getCapturedAmountCents(),
                                    "remoteCapturedCents", remote.capturedAmountCents()
                                )
                        );
                        if (recorded) discrepancyCount++;
                    }

                    // UNEXPECTED REFUND CHECK
                    if (remote.refundedAmountCents() > local.getRefundedAmountCents()) {
                        boolean recorded = recordDiscrepancyIfAbsent(
                                run,
                                local.getId(),
                                remote.providerPaymentId(),
                                provider,
                                DiscrepancyType.UNEXPECTED_REFUND,
                                DiscrepancySeverity.HIGH,
                                Map.of(
                                    "reason", "Provider reports refunds not reflected in local database",
                                    "localRefundedCents", local.getRefundedAmountCents(),
                                    "remoteRefundedCents", remote.refundedAmountCents()
                                )
                        );
                        if (recorded) discrepancyCount++;
                    }
                }
            }

            // STALE / UNRESOLVED TRANSACTION CHECK: Local payments in CREATED or AUTHORIZED > 24 hours
            Instant staleCutoff = Instant.now().minusSeconds(86400);
            List<PaymentTransactionEntity> stalePayments = paymentRepository.findStaleUnresolved(
                    provider, staleCutoff, PageRequest.of(0, batchSize));
            for (PaymentTransactionEntity stale : stalePayments) {
                boolean recorded = recordDiscrepancyIfAbsent(
                        run,
                        stale.getId(),
                        stale.getProviderPaymentId(),
                        provider,
                        DiscrepancyType.STALE_UNRESOLVED,
                        DiscrepancySeverity.MEDIUM,
                        Map.of(
                                "reason", "Payment remains in non-final status without settlement for over 24 hours",
                                "paymentId", stale.getId(),
                                "status", stale.getStatus().name(),
                                "createdAt", stale.getCreatedAt().toString()
                        )
                );
                if (recorded) discrepancyCount++;
            }

            run.setStatus(ReconciliationRunEntity.RunStatus.COMPLETED);
            run.setTotalRemoteChecked(remoteCount);
            run.setTotalLocalChecked(localCount);
            run.setDiscrepanciesCount(discrepancyCount);
            run.setCompletedAt(Instant.now());

            log.info("Reconciliation run {} finished: provider={}, remote={}, local={}, discrepancies={}",
                    run.getId(), provider, remoteCount, localCount, discrepancyCount);

        } catch (Exception ex) {
            log.error("Reconciliation run {} failed: {}", run.getId(), ex.getMessage(), ex);
            run.setStatus(ReconciliationRunEntity.RunStatus.FAILED);
            run.setErrorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            run.setCompletedAt(Instant.now());
        }

        return runRepository.saveAndFlush(run);
    }

    private boolean recordDiscrepancyIfAbsent(
            ReconciliationRunEntity run,
            String paymentId,
            String providerPaymentId,
            PaymentProvider provider,
            DiscrepancyType type,
            DiscrepancySeverity severity,
            Map<String, Object> evidenceMap) {

        // Idempotency check: Do not create duplicate if an OPEN or INVESTIGATING discrepancy already exists
        Optional<DiscrepancyEntity> existing = discrepancyRepository.findActiveDiscrepancy(providerPaymentId, type);
        if (existing.isPresent()) {
            log.debug("Active discrepancy already exists for providerPaymentId={} and type={}. Skipping duplicate.",
                    providerPaymentId, type);
            return false;
        }

        String evidenceJson;
        try {
            evidenceJson = objectMapper.writeValueAsString(evidenceMap);
        } catch (JsonProcessingException e) {
            evidenceJson = evidenceMap.toString();
        }

        DiscrepancyEntity discrepancy = new DiscrepancyEntity(
                IdGenerator.discrepancyId(),
                run,
                paymentId,
                providerPaymentId,
                provider,
                type,
                severity,
                evidenceJson,
                DiscrepancyStatus.OPEN,
                Instant.now(),
                Instant.now()
        );

        discrepancyRepository.save(discrepancy);
        metrics.incrementReconciliationDiscrepancies();

        log.warn("Reconciliation discrepancy detected: type={}, severity={}, providerPaymentId={}",
                type, severity, providerPaymentId);
        return true;
    }
}
