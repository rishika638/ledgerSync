package com.ledgersync.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.audit.service.AuditService;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.observability.MetricsConfig;
import com.ledgersync.payment.model.PaymentStatus;
import com.ledgersync.payment.model.PaymentTransactionEntity;
import com.ledgersync.payment.repository.PaymentTransactionRepository;
import com.ledgersync.reconciliation.model.*;
import com.ledgersync.reconciliation.provider.MockStripeClient;
import com.ledgersync.reconciliation.provider.RemotePaymentRecord;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import com.ledgersync.reconciliation.service.DiscrepancyResolutionService;
import com.ledgersync.reconciliation.service.ReconciliationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private MockStripeClient mockStripeClient;

    @Mock
    private PaymentTransactionRepository paymentRepository;

    @Mock
    private ReconciliationRunRepository runRepository;

    @Mock
    private DiscrepancyRepository discrepancyRepository;

    @Mock
    private AuditService auditService;

    private ReconciliationService reconciliationService;
    private DiscrepancyResolutionService resolutionService;

    @BeforeEach
    void setUp() {
        mockStripeClient = new MockStripeClient();
        mockStripeClient.clear();

        MetricsConfig metrics = new MetricsConfig(new SimpleMeterRegistry());
        ObjectMapper objectMapper = new ObjectMapper();

        reconciliationService = new ReconciliationService(
                List.of(mockStripeClient),
                paymentRepository,
                runRepository,
                discrepancyRepository,
                metrics,
                objectMapper,
                50
        );

        resolutionService = new DiscrepancyResolutionService(discrepancyRepository, auditService);

        lenient().when(runRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Reconciliation: detects MISSING_IN_LOCAL when remote payment exists but missing in local DB")
    void testDetectMissingInLocal() {
        mockStripeClient.registerRemoteRecord(new RemotePaymentRecord(
                "pi_missing_123",
                PaymentProvider.STRIPE,
                5000,
                5000,
                0,
                "USD",
                PaymentStatus.CAPTURED,
                Instant.now()
        ));

        when(paymentRepository.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_missing_123"))
                .thenReturn(Optional.empty());

        when(discrepancyRepository.findActiveDiscrepancy("pi_missing_123", DiscrepancyType.MISSING_IN_LOCAL))
                .thenReturn(Optional.empty());

        ReconciliationRunEntity run = reconciliationService.runReconciliation(PaymentProvider.STRIPE);

        assertEquals(ReconciliationRunEntity.RunStatus.COMPLETED, run.getStatus());
        assertEquals(1, run.getTotalRemoteChecked());
        assertEquals(0, run.getTotalLocalChecked());
        assertEquals(1, run.getDiscrepanciesCount());

        verify(discrepancyRepository).save(argThat(d ->
                d.getType() == DiscrepancyType.MISSING_IN_LOCAL &&
                d.getSeverity() == DiscrepancySeverity.CRITICAL &&
                "pi_missing_123".equals(d.getProviderPaymentId())
        ));
    }

    @Test
    @DisplayName("Reconciliation: detects STATUS_MISMATCH when local is AUTHORIZED and provider is CAPTURED")
    void testDetectStatusMismatch() {
        mockStripeClient.registerRemoteRecord(new RemotePaymentRecord(
                "pi_mismatch_456",
                PaymentProvider.STRIPE,
                5000,
                5000,
                0,
                "USD",
                PaymentStatus.CAPTURED,
                Instant.now()
        ));

        PaymentTransactionEntity localTx = new PaymentTransactionEntity(
                "pay_local_1",
                PaymentProvider.STRIPE,
                "pi_mismatch_456",
                "idem_1",
                "cus_1",
                "USD",
                5000,
                5000,
                0,
                PaymentStatus.AUTHORIZED, // Local is only AUTHORIZED
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(paymentRepository.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_mismatch_456"))
                .thenReturn(Optional.of(localTx));

        when(discrepancyRepository.findActiveDiscrepancy("pi_mismatch_456", DiscrepancyType.STATUS_MISMATCH))
                .thenReturn(Optional.empty());

        ReconciliationRunEntity run = reconciliationService.runReconciliation(PaymentProvider.STRIPE);

        assertEquals(1, run.getDiscrepanciesCount());
        verify(discrepancyRepository).save(argThat(d ->
                d.getType() == DiscrepancyType.STATUS_MISMATCH &&
                d.getSeverity() == DiscrepancySeverity.HIGH
        ));
    }

    @Test
    @DisplayName("Reconciliation: repeated runs are idempotent and do not create duplicate active discrepancies")
    void testRepeatedRunsIdempotent() {
        mockStripeClient.registerRemoteRecord(new RemotePaymentRecord(
                "pi_dup_check",
                PaymentProvider.STRIPE,
                5000,
                5000,
                0,
                "USD",
                PaymentStatus.CAPTURED,
                Instant.now()
        ));

        when(paymentRepository.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_dup_check"))
                .thenReturn(Optional.empty());

        // Simulate an existing OPEN discrepancy
        DiscrepancyEntity existing = new DiscrepancyEntity();
        existing.setStatus(DiscrepancyStatus.OPEN);

        when(discrepancyRepository.findActiveDiscrepancy("pi_dup_check", DiscrepancyType.MISSING_IN_LOCAL))
                .thenReturn(Optional.of(existing));

        ReconciliationRunEntity run = reconciliationService.runReconciliation(PaymentProvider.STRIPE);

        assertEquals(0, run.getDiscrepanciesCount());
        verify(discrepancyRepository, never()).save(any());
    }

    @Test
    @DisplayName("DiscrepancyResolutionService: resolving discrepancy saves notes, operator, and audit event")
    void testDiscrepancyResolution() {
        ReconciliationRunEntity run = new ReconciliationRunEntity("rec_1", PaymentProvider.STRIPE, ReconciliationRunEntity.RunStatus.COMPLETED, Instant.now());
        DiscrepancyEntity discrepancy = new DiscrepancyEntity(
                "dsc_1",
                run,
                null,
                "pi_missing_123",
                PaymentProvider.STRIPE,
                DiscrepancyType.MISSING_IN_LOCAL,
                DiscrepancySeverity.CRITICAL,
                "{}",
                DiscrepancyStatus.OPEN,
                Instant.now(),
                Instant.now()
        );

        when(discrepancyRepository.findById("dsc_1")).thenReturn(Optional.of(discrepancy));
        when(discrepancyRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        DiscrepancyEntity resolved = resolutionService.resolve(
                "dsc_1",
                DiscrepancyStatus.RESOLVED,
                "Manually synchronized with Stripe dashboard invoice #9876",
                "admin-user"
        );

        assertEquals(DiscrepancyStatus.RESOLVED, resolved.getStatus());
        assertEquals("admin-user", resolved.getResolvedBy());
        assertNotNull(resolved.getResolvedAt());
        verify(auditService).record(eq("RESOLVE_DISCREPANCY"), eq("admin-user"), eq("DISCREPANCY"), eq("dsc_1"), any(), any());
    }

    @Test
    @DisplayName("Reconciliation: detects STALE_UNRESOLVED transactions created >24h ago that never settled")
    void testDetectStaleUnresolvedTransaction() {
        PaymentTransactionEntity stale = new PaymentTransactionEntity(
                "pay_stale_1",
                PaymentProvider.STRIPE,
                "pi_stale_999",
                "idem_stale",
                "cus_stale",
                "USD",
                10000,
                0,
                0,
                PaymentStatus.CREATED,
                Instant.now().minusSeconds(100000),
                Instant.now().minusSeconds(100000),
                Instant.now().minusSeconds(100000)
        );

        when(paymentRepository.findStaleUnresolved(eq(PaymentProvider.STRIPE), any(), any()))
                .thenReturn(List.of(stale));

        when(discrepancyRepository.findActiveDiscrepancy("pi_stale_999", DiscrepancyType.STALE_UNRESOLVED))
                .thenReturn(Optional.empty());

        ReconciliationRunEntity run = reconciliationService.runReconciliation(PaymentProvider.STRIPE);

        assertEquals(1, run.getDiscrepanciesCount());
        verify(discrepancyRepository).save(argThat(d ->
                d.getType() == DiscrepancyType.STALE_UNRESOLVED &&
                d.getSeverity() == DiscrepancySeverity.MEDIUM &&
                "pi_stale_999".equals(d.getProviderPaymentId())
        ));
    }
}
