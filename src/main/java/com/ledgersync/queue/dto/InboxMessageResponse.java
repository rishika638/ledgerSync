package com.ledgersync.queue.dto;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;

import java.time.Instant;

public record InboxMessageResponse(
        String id,
        String eventId,
        PaymentProvider provider,
        String eventType,
        InboxStatus status,
        int attemptCount,
        int maxAttempts,
        Instant scheduledAt,
        Instant lockedAt,
        String lockedBy,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static InboxMessageResponse fromEntity(InboxMessageEntity entity) {
        return new InboxMessageResponse(
                entity.getId(),
                entity.getEventId(),
                entity.getProvider(),
                entity.getEventType(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getScheduledAt(),
                entity.getLockedAt(),
                entity.getLockedBy(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
