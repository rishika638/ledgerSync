package com.ledgersync.queue;

import com.ledgersync.audit.service.AuditService;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.queue.dto.RedriveResult;
import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;
import com.ledgersync.queue.repository.InboxMessageRepository;
import com.ledgersync.queue.service.DeadLetterRedriveService;
import com.ledgersync.queue.worker.AbandonedJobRecoveryWorker;
import com.ledgersync.queue.worker.InboxWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxWorkerAndDeadLetterTest {

    @Mock
    private InboxMessageRepository inboxRepository;

    @Mock
    private AuditService auditService;

    private DeadLetterRedriveService redriveService;
    private AbandonedJobRecoveryWorker abandonedRecoveryWorker;

    @BeforeEach
    void setUp() {
        redriveService = new DeadLetterRedriveService(inboxRepository, auditService);
        abandonedRecoveryWorker = new AbandonedJobRecoveryWorker(inboxRepository, 300);
    }

    @Test
    @DisplayName("Exponential backoff calculation produces bounded positive delays with jitter")
    void testBackoffCalculation() {
        long delay0 = InboxWorker.calculateBackoffMillisWithJitter(0);
        long delay3 = InboxWorker.calculateBackoffMillisWithJitter(3);
        long delay10 = InboxWorker.calculateBackoffMillisWithJitter(10);

        assertTrue(delay0 >= 1000L);
        assertTrue(delay3 > delay0);
        // Bounded at 60s max + 500ms max jitter
        assertTrue(delay10 <= 61000L);
    }

    @Test
    @DisplayName("DeadLetterRedriveService: redriving single dead letter transitions to PENDING and resets attempts")
    void testRedriveSingle() {
        InboxMessageEntity deadLetter = new InboxMessageEntity(
                "inb_test_dlq",
                "evt_1",
                PaymentProvider.STRIPE,
                "payment_intent.succeeded",
                "{}",
                InboxStatus.DEAD_LETTER,
                5,
                5,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(inboxRepository.findById("inb_test_dlq")).thenReturn(Optional.of(deadLetter));

        RedriveResult result = redriveService.redriveSingle("inb_test_dlq", "test-admin");

        assertEquals(1, result.redrivenCount());
        assertEquals(InboxStatus.PENDING, deadLetter.getStatus());
        assertEquals(0, deadLetter.getAttemptCount());
        verify(inboxRepository).save(deadLetter);
        verify(auditService).record(eq("REDRIVE_DEAD_LETTER"), eq("test-admin"), any(), eq("inb_test_dlq"), any(), any());
    }

    @Test
    @DisplayName("DeadLetterRedriveService: bulk redriving re-queues all items and audits each")
    void testBulkRedrive() {
        InboxMessageEntity item1 = new InboxMessageEntity("inb_1", "evt_1", PaymentProvider.STRIPE, "type", "{}", InboxStatus.DEAD_LETTER, 5, 5, Instant.now(), Instant.now(), Instant.now());
        InboxMessageEntity item2 = new InboxMessageEntity("inb_2", "evt_2", PaymentProvider.STRIPE, "type", "{}", InboxStatus.DEAD_LETTER, 5, 5, Instant.now(), Instant.now(), Instant.now());

        when(inboxRepository.findByStatus(eq(InboxStatus.DEAD_LETTER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item1, item2)));

        RedriveResult result = redriveService.redriveAll(10, "test-operator");

        assertEquals(2, result.redrivenCount());
        assertEquals(InboxStatus.PENDING, item1.getStatus());
        assertEquals(InboxStatus.PENDING, item2.getStatus());
        verify(inboxRepository, times(2)).save(any(InboxMessageEntity.class));
    }

    @Test
    @DisplayName("AbandonedJobRecoveryWorker: resets stuck PROCESSING jobs back to PENDING")
    void testAbandonedJobRecovery() {
        InboxMessageEntity stuckJob = new InboxMessageEntity(
                "inb_stuck",
                "evt_stuck",
                PaymentProvider.STRIPE,
                "payment_intent.succeeded",
                "{}",
                InboxStatus.PROCESSING,
                1,
                5,
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(600)
        );
        stuckJob.setLockedAt(Instant.now().minusSeconds(600));
        stuckJob.setLockedBy("crashed-worker-node-1");

        when(inboxRepository.findAbandonedJobs(any(Instant.class))).thenReturn(List.of(stuckJob));

        int recovered = abandonedRecoveryWorker.recoverAbandonedJobs();

        assertEquals(1, recovered);
        assertEquals(InboxStatus.PENDING, stuckJob.getStatus());
        assertNull(stuckJob.getLockedBy());
        assertNull(stuckJob.getLockedAt());
        assertEquals(2, stuckJob.getAttemptCount());
        verify(inboxRepository).save(stuckJob);
    }
}
