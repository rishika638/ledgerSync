package com.ledgersync.reconciliation.dto;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancySeverity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.model.DiscrepancyType;

import java.time.Instant;

public record DiscrepancyResponse(
        String id,
        String runId,
        String paymentId,
        String providerPaymentId,
        PaymentProvider provider,
        DiscrepancyType type,
        DiscrepancySeverity severity,
        String evidence,
        DiscrepancyStatus status,
        String resolutionNotes,
        String resolvedBy,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static DiscrepancyResponse fromEntity(DiscrepancyEntity entity) {
        return new DiscrepancyResponse(
                entity.getId(),
                entity.getRun().getId(),
                entity.getPaymentId(),
                entity.getProviderPaymentId(),
                entity.getProvider(),
                entity.getType(),
                entity.getSeverity(),
                entity.getEvidence(),
                entity.getStatus(),
                entity.getResolutionNotes(),
                entity.getResolvedBy(),
                entity.getResolvedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
