package com.ledgersync.payment.dto;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.payment.model.PaymentStatus;
import com.ledgersync.payment.model.PaymentTransactionEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentResponse(
        String id,
        PaymentProvider provider,
        String providerPaymentId,
        String idempotencyKey,
        String customerId,
        String currency,
        long amountCents,
        BigDecimal amountMajor,
        long capturedAmountCents,
        BigDecimal capturedAmountMajor,
        long refundedAmountCents,
        BigDecimal refundedAmountMajor,
        PaymentStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<RefundResponse> refunds
) {
    public static PaymentResponse fromEntity(PaymentTransactionEntity entity, List<RefundResponse> refunds) {
        return new PaymentResponse(
                entity.getId(),
                entity.getProvider(),
                entity.getProviderPaymentId(),
                entity.getIdempotencyKey(),
                entity.getCustomerId(),
                entity.getCurrency(),
                entity.getAmountCents(),
                BigDecimal.valueOf(entity.getAmountCents()).movePointLeft(2),
                entity.getCapturedAmountCents(),
                BigDecimal.valueOf(entity.getCapturedAmountCents()).movePointLeft(2),
                entity.getRefundedAmountCents(),
                BigDecimal.valueOf(entity.getRefundedAmountCents()).movePointLeft(2),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                refunds != null ? refunds : List.of()
        );
    }
}
