package com.ledgersync.finance.service;

import com.ledgersync.audit.repository.AuditEventRepository;
import com.ledgersync.common.error.ConflictOperationException;
import com.ledgersync.finance.model.ResolutionActionType;
import com.ledgersync.finance.repository.ResolutionActionRepository;
import com.ledgersync.reconciliation.model.*;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import com.ledgersync.ingestion.model.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ResolutionServiceIntegrationTest {

    @Autowired private ResolutionService resolutionService;
    @Autowired private ReconciliationRunRepository runRepository;
    @Autowired private DiscrepancyRepository discrepancyRepository;
    @Autowired private ResolutionActionRepository actionRepository;
    @Autowired private AuditEventRepository auditRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void recordsOneHumanApprovalAndRejectsARepeatedTerminalAction() {
        String discrepancyId = createDiscrepancy("approval-test", DiscrepancyStatus.AWAITING_APPROVAL);
        ResolutionService.ActionRequest request = new ResolutionService.ActionRequest();
        request.actionType = ResolutionActionType.APPROVE_ADJUSTMENT;
        request.financialAdjustmentMinor = 1250L;
        request.notes = "Controller verified the settlement evidence.";

        var savedAction = resolutionService.executeAction(discrepancyId, request, "operator");

        assertEquals(ResolutionActionType.APPROVE_ADJUSTMENT, savedAction.getActionType());
        assertEquals(1250L, savedAction.getFinancialAdjustmentMinor());
        assertEquals(DiscrepancyStatus.RESOLVED,
                discrepancyRepository.findById(discrepancyId).orElseThrow().getStatus());
        DiscrepancyEntity discrepancy = discrepancyRepository.findById(discrepancyId).orElseThrow();
        assertEquals(1, actionRepository.findByDiscrepancyOrderByCreatedAtDesc(discrepancy).size());
        assertEquals(1, auditRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
                "DISCREPANCY", discrepancyId).size());

        assertThrows(ConflictOperationException.class,
                () -> resolutionService.executeAction(discrepancyId, request, "operator"));
        assertEquals(1, actionRepository.findByDiscrepancyOrderByCreatedAtDesc(discrepancy).size());
    }

    @Test
    void neverAllowsForceMatchToResolveAnException() {
        String discrepancyId = createDiscrepancy("force-test", DiscrepancyStatus.OPEN);
        ResolutionService.ActionRequest request = new ResolutionService.ActionRequest();
        request.actionType = ResolutionActionType.FORCE_MATCH;

        assertThrows(ConflictOperationException.class,
                () -> resolutionService.executeAction(discrepancyId, request, "admin"));
        DiscrepancyEntity discrepancy = discrepancyRepository.findById(discrepancyId).orElseThrow();
        assertEquals(DiscrepancyStatus.OPEN,
                discrepancy.getStatus());
        assertTrue(actionRepository.findByDiscrepancyOrderByCreatedAtDesc(discrepancy).isEmpty());
    }

    @Test
    void concurrentApprovalsCreateOnlyOneActionAndAuditEvent() throws Exception {
        String discrepancyId = createDiscrepancy("concurrent-test", DiscrepancyStatus.AWAITING_APPROVAL);
        ResolutionService.ActionRequest request = new ResolutionService.ActionRequest();
        request.actionType = ResolutionActionType.APPROVE_ADJUSTMENT;
        request.financialAdjustmentMinor = 1250L;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> executeAfter(start, discrepancyId, request, "operator-1"));
            Future<Boolean> second = executor.submit(() -> executeAfter(start, discrepancyId, request, "operator-2"));
            start.countDown();

            assertNotEquals(first.get(), second.get(), "Exactly one concurrent approval should succeed.");
            DiscrepancyEntity discrepancy = discrepancyRepository.findById(discrepancyId).orElseThrow();
            assertEquals(1, actionRepository.findByDiscrepancyOrderByCreatedAtDesc(discrepancy).size());
            assertEquals(1, auditRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
                    "DISCREPANCY", discrepancyId).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean executeAfter(CountDownLatch start, String discrepancyId,
                                 ResolutionService.ActionRequest request, String actor) throws Exception {
        start.await();
        try {
            resolutionService.executeAction(discrepancyId, request, actor);
            return true;
        } catch (ConflictOperationException expected) {
            return false;
        }
    }

    private String createDiscrepancy(String suffix, DiscrepancyStatus status) {
        return new TransactionTemplate(transactionManager).execute(transactionStatus -> {
            Instant now = Instant.now();
            ReconciliationRunEntity run = new ReconciliationRunEntity(
                    "run-" + suffix, PaymentProvider.RAZORPAY, ReconciliationRunEntity.RunStatus.COMPLETED, now);
            run.setRunType("THREE_WAY_SETTLEMENT");
            run.setCompletedAt(now);
            run = runRepository.saveAndFlush(run);
            DiscrepancyEntity discrepancy = new DiscrepancyEntity(
                    "discrepancy-" + suffix, run, "payment-" + suffix, "provider-" + suffix,
                    PaymentProvider.RAZORPAY, DiscrepancyType.MISSING_BANK, DiscrepancySeverity.HIGH,
                    "{\"root_cause_explanation\":\"bank credit missing\"}", status, now, now);
            discrepancy.setAmountMinor(1250);
            discrepancy.setCurrency("INR");
            return discrepancyRepository.saveAndFlush(discrepancy).getId();
        });
    }
}
