package com.ledgersync.queue.service;

import com.ledgersync.audit.service.AuditService;
import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.queue.dto.RedriveResult;
import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;
import com.ledgersync.queue.repository.InboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeadLetterRedriveService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRedriveService.class);

    private final InboxMessageRepository inboxRepository;
    private final AuditService auditService;

    public DeadLetterRedriveService(
            InboxMessageRepository inboxRepository,
            AuditService auditService) {
        this.inboxRepository = inboxRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RedriveResult redriveSingle(String messageId, String operator) {
        InboxMessageEntity msg = inboxRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbox message not found: " + messageId));

        if (msg.getStatus() != InboxStatus.DEAD_LETTER) {
            throw new IllegalStateException("Only messages in DEAD_LETTER status can be redriven. Current status: " + msg.getStatus());
        }

        String beforeState = "status=" + msg.getStatus() + ",attempts=" + msg.getAttemptCount();

        msg.setStatus(InboxStatus.PENDING);
        msg.setAttemptCount(0);
        msg.setScheduledAt(Instant.now());
        msg.setLockedAt(null);
        msg.setLockedBy(null);
        msg.setUpdatedAt(Instant.now());
        inboxRepository.save(msg);

        String afterState = "status=" + msg.getStatus() + ",attempts=" + msg.getAttemptCount();

        auditService.record(
                "REDRIVE_DEAD_LETTER",
                operator,
                "INBOX_MESSAGE",
                msg.getId(),
                beforeState,
                afterState
        );

        log.info("Operator {} redrove dead letter message {}", operator, messageId);
        return new RedriveResult(1, List.of(messageId), "Message successfully re-queued for processing");
    }

    @Transactional
    public RedriveResult redriveAll(int maxBatchSize, String operator) {
        Page<InboxMessageEntity> deadLetters = inboxRepository.findByStatus(
                InboxStatus.DEAD_LETTER, PageRequest.of(0, maxBatchSize));

        List<String> ids = new ArrayList<>();
        for (InboxMessageEntity msg : deadLetters.getContent()) {
            msg.setStatus(InboxStatus.PENDING);
            msg.setAttemptCount(0);
            msg.setScheduledAt(Instant.now());
            msg.setLockedAt(null);
            msg.setLockedBy(null);
            msg.setUpdatedAt(Instant.now());
            inboxRepository.save(msg);
            ids.add(msg.getId());

            auditService.record(
                    "BULK_REDRIVE_DEAD_LETTER",
                    operator,
                    "INBOX_MESSAGE",
                    msg.getId(),
                    "status=DEAD_LETTER",
                    "status=PENDING"
            );
        }

        log.info("Operator {} bulk-redrove {} dead letter messages", operator, ids.size());
        return new RedriveResult(ids.size(), ids, "Successfully re-queued " + ids.size() + " messages");
    }
}
