package com.ledgersync.payment.dto;

import com.ledgersync.payment.model.RefundEntity;
import com.ledgersync.payment.model.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(
        String id,
        String providerRefundId,
        long amountCents,
        BigDecimal amountMajor,
        String currency,
        String reason,
        RefundStatus status,
        Instant createdAt
) {
    public static RefundResponse fromEntity(RefundEntity entity) {
        return new RefundResponse(
                entity.getId(),
                entity.getProviderRefundId(),
                entity.getAmountCents(),
                BigDecimal.valueOf(entity.getAmountCents()).movePointLeft(2),
                entity.getCurrency(),
                entity.getReason(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
