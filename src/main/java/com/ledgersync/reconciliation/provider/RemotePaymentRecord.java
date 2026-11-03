package com.ledgersync.reconciliation.provider;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.payment.model.PaymentStatus;

import java.time.Instant;

public record RemotePaymentRecord(
        String providerPaymentId,
        PaymentProvider provider,
        long amountCents,
        long capturedAmountCents,
        long refundedAmountCents,
        String currency,
        PaymentStatus status,
        Instant providerCreatedAt
) {
}
