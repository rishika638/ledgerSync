package com.ledgersync.reconciliation.dto;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;

import java.time.Instant;

public record ReconciliationRunResponse(
        String id,
        PaymentProvider provider,
        ReconciliationRunEntity.RunStatus status,
        int totalRemoteChecked,
        int totalLocalChecked,
        int discrepanciesCount,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
    public static ReconciliationRunResponse fromEntity(ReconciliationRunEntity entity) {
        return new ReconciliationRunResponse(
                entity.getId(),
                entity.getProvider(),
                entity.getStatus(),
                entity.getTotalRemoteChecked(),
                entity.getTotalLocalChecked(),
                entity.getDiscrepanciesCount(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getErrorMessage()
        );
    }
}
