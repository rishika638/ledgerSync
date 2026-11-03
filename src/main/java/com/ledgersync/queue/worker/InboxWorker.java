package com.ledgersync.queue.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.ingestion.dto.StandardWebhookEvent;
import com.ledgersync.observability.MetricsConfig;
import com.ledgersync.payment.service.PaymentProcessingService;
import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;
import com.ledgersync.queue.repository.InboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "ledgersync.inbox.enabled", havingValue = "true", matchIfMissing = true)
public class InboxWorker {

    private static final Logger log = LoggerFactory.getLogger(InboxWorker.class);

    private final InboxMessageRepository inboxRepository;
    private final PaymentProcessingService paymentProcessingService;
    private final MetricsConfig metrics;
    private final ObjectMapper objectMapper;
    private final OptionalRedisCoordinator redisCoordinator;
    private final String workerId;
    private final int batchSize;

    public InboxWorker(
            InboxMessageRepository inboxRepository,
            PaymentProcessingService paymentProcessingService,
            MetricsConfig metrics,
            ObjectMapper objectMapper,
            OptionalRedisCoordinator redisCoordinator,
            @Value("${ledgersync.inbox.batch-size:25}") int batchSize) {
        this.inboxRepository = inboxRepository;
        this.paymentProcessingService = paymentProcessingService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.redisCoordinator = redisCoordinator;
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ledgersync.inbox.poll-interval-ms:2000}")
    public void pollAndProcess() {
        // Redis coordination is optional; if Redis fails or is down, worker proceeds directly with DB SKIP LOCKED
        boolean acquiredCoordination = redisCoordinator.tryAcquireCoordination("ledgersync:inbox:lock", workerId, Duration.ofSeconds(5));
        if (!acquiredCoordination) {
            log.trace("Inbox poll skipped; coordination held by another worker or Redis unavailable fallback active");
        }

        try {
            processBatch();
        } catch (Exception e) {
            log.error("Unhandled error during inbox polling batch", e);
        }
    }

    public int processBatch() {
        List<InboxMessageEntity> messages = claimBatch();
        if (messages.isEmpty()) {
            return 0;
        }

        log.debug("Worker {} claimed {} inbox messages using SKIP LOCKED", workerId, messages.size());

        for (InboxMessageEntity msg : messages) {
            processSingleMessage(msg);
        }

        return messages.size();
    }

    @Transactional
    public List<InboxMessageEntity> claimBatch() {
        Instant now = Instant.now();
        return inboxRepository.claimPendingBatch(now, batchSize);
    }

    private void processSingleMessage(InboxMessageEntity msg) {
        String correlationId = "inb-" + msg.getId();
        MDC.put("correlationId", correlationId);

        try {
            msg.setStatus(InboxStatus.PROCESSING);
            msg.setLockedAt(Instant.now());
            msg.setLockedBy(workerId);
            msg.setUpdatedAt(Instant.now());
            inboxRepository.saveAndFlush(msg);

            StandardWebhookEvent event = objectMapper.readValue(msg.getPayload(), StandardWebhookEvent.class);
            paymentProcessingService.processEvent(event);

            msg.setStatus(InboxStatus.COMPLETED);
            msg.setLastError(null);
            msg.setUpdatedAt(Instant.now());
            inboxRepository.saveAndFlush(msg);
            metrics.incrementInboxProcessed();

            log.info("Successfully processed inbox message {} (event={})", msg.getId(), msg.getEventId());
        } catch (Exception ex) {
            handleProcessingFailure(msg, ex);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void handleProcessingFailure(InboxMessageEntity msg, Exception ex) {
        int nextAttempt = msg.getAttemptCount() + 1;
        msg.setAttemptCount(nextAttempt);
        msg.setLockedAt(null);
        msg.setLockedBy(null);
        msg.setUpdatedAt(Instant.now());

        String errorMsg = ex.getClass().getSimpleName() + ": " + (ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        msg.setLastError(errorMsg);

        if (nextAttempt >= msg.getMaxAttempts()) {
            msg.setStatus(InboxStatus.DEAD_LETTER);
            metrics.incrementInboxDeadLetters();
            log.error("Inbox message {} exceeded max retries ({}/{}). Moved to DEAD_LETTER. Error: {}",
                    msg.getId(), nextAttempt, msg.getMaxAttempts(), errorMsg);
        } else {
            long backoffMillis = calculateBackoffMillisWithJitter(nextAttempt);
            msg.setStatus(InboxStatus.PENDING);
            msg.setScheduledAt(Instant.now().plusMillis(backoffMillis));
            log.warn("Inbox message {} failed (attempt {}/{}). Rescheduled in {}ms. Error: {}",
                    msg.getId(), nextAttempt, msg.getMaxAttempts(), backoffMillis, errorMsg);
        }

        inboxRepository.saveAndFlush(msg);
    }

    /**
     * Exponential backoff with full jitter:
     * base = 1000ms, backoff = min(60000ms, base * 2^attempt) + jitter[0, 500ms]
     */
    public static long calculateBackoffMillisWithJitter(int attempt) {
        long base = 1000L;
        long max = 60000L;
        long exp = base * (1L << Math.min(attempt, 6));
        long bounded = Math.min(exp, max);
        long jitter = ThreadLocalRandom.current().nextLong(100, 500);
        return bounded + jitter;
    }
}
