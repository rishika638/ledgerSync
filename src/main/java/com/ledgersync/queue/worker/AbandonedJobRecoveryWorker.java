package com.ledgersync.queue.worker;

import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;
import com.ledgersync.queue.repository.InboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "ledgersync.inbox.enabled", havingValue = "true", matchIfMissing = true)
public class AbandonedJobRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(AbandonedJobRecoveryWorker.class);

    private final InboxMessageRepository inboxRepository;
    private final long timeoutSeconds;

    public AbandonedJobRecoveryWorker(
            InboxMessageRepository inboxRepository,
            @Value("${ledgersync.inbox.abandoned-timeout-seconds:300}") long timeoutSeconds) {
        this.inboxRepository = inboxRepository;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${ledgersync.inbox.abandoned-check-interval-ms:30000}")
    @Transactional
    public int recoverAbandonedJobs() {
        Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
        List<InboxMessageEntity> abandoned = inboxRepository.findAbandonedJobs(cutoff);

        if (abandoned.isEmpty()) {
            return 0;
        }

        log.warn("Detected {} abandoned jobs stuck in PROCESSING longer than {}s. Recovering...",
                abandoned.size(), timeoutSeconds);

        for (InboxMessageEntity msg : abandoned) {
            log.warn("Recovering abandoned job {}: lockedBy={}, lockedAt={}",
                    msg.getId(), msg.getLockedBy(), msg.getLockedAt());

            msg.setStatus(InboxStatus.PENDING);
            msg.setLockedAt(null);
            msg.setLockedBy(null);
            msg.setAttemptCount(msg.getAttemptCount() + 1);
            msg.setScheduledAt(Instant.now());
            msg.setUpdatedAt(Instant.now());
            msg.setLastError("Recovered after worker abandoned processing lock");
            inboxRepository.save(msg);
        }

        return abandoned.size();
    }
}
