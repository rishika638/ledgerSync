package com.ledgersync.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ledgersync.ingestion.model.PaymentProvider;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StandardWebhookEvent(
        String eventId,
        PaymentProvider provider,
        String eventType,
        String providerPaymentId,
        String idempotencyKey,
        String customerId,
        String currency,
        Long amountCents,
        Long capturedAmountCents,
        String refundId,
        Long refundAmountCents,
        String refundReason,
        Instant providerCreatedAt,
        String rawJson
) {
}
