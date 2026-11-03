package com.ledgersync.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.common.error.BusinessException;
import com.ledgersync.ingestion.dto.StandardWebhookEvent;
import com.ledgersync.ingestion.model.PaymentProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WebhookParser {

    private final ObjectMapper objectMapper;

    public WebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StandardWebhookEvent parse(PaymentProvider provider, byte[] rawPayloadBytes) {
        try {
            JsonNode root = objectMapper.readTree(rawPayloadBytes);
            String rawJson = new String(rawPayloadBytes);

            return switch (provider) {
                case STRIPE -> parseStripe(root, rawJson);
                case RAZORPAY -> parseRazorpay(root, rawJson);
            };
        } catch (Exception e) {
            throw new WebhookParseException("Failed to parse " + provider + " webhook JSON payload: " + e.getMessage(), e);
        }
    }

    private StandardWebhookEvent parseStripe(JsonNode root, String rawJson) {
        String eventId = root.path("id").asText();
        String eventType = root.path("type").asText();
        long createdEpoch = root.path("created").asLong(Instant.now().getEpochSecond());
        Instant createdAt = Instant.ofEpochSecond(createdEpoch);

        JsonNode dataObj = root.path("data").path("object");
        String providerPaymentId = dataObj.path("id").asText();
        if (eventType.startsWith("charge.") && dataObj.has("payment_intent") && !dataObj.path("payment_intent").isNull()) {
            providerPaymentId = dataObj.path("payment_intent").asText();
        }

        String customerId = dataObj.path("customer").asText(null);
        String currency = dataObj.path("currency").asText("USD").toUpperCase();
        long amountCents = dataObj.path("amount").asLong(0);

        long capturedAmountCents = 0;
        if (dataObj.has("amount_received")) {
            capturedAmountCents = dataObj.path("amount_received").asLong();
        } else if (dataObj.has("amount_captured")) {
            capturedAmountCents = dataObj.path("amount_captured").asLong();
        } else if ("payment_intent.succeeded".equalsIgnoreCase(eventType) || "charge.captured".equalsIgnoreCase(eventType)) {
            capturedAmountCents = amountCents;
        }

        String refundId = null;
        Long refundAmountCents = null;
        String refundReason = null;

        if (eventType.contains("refund")) {
            JsonNode refundsNode = dataObj.path("refunds").path("data");
            if (refundsNode.isArray() && !refundsNode.isEmpty()) {
                JsonNode firstRefund = refundsNode.get(0);
                refundId = firstRefund.path("id").asText(null);
                refundAmountCents = firstRefund.path("amount").asLong(0);
                refundReason = firstRefund.path("reason").asText(null);
            } else if (dataObj.has("amount_refunded")) {
                refundId = "ref_" + eventId;
                refundAmountCents = dataObj.path("amount_refunded").asLong();
            }
        }

        String idempotencyKey = root.path("request").path("idempotency_key").asText(null);

        return new StandardWebhookEvent(
                eventId,
                PaymentProvider.STRIPE,
                eventType,
                providerPaymentId,
                idempotencyKey,
                customerId,
                currency,
                amountCents,
                capturedAmountCents,
                refundId,
                refundAmountCents,
                refundReason,
                createdAt,
                rawJson
        );
    }

    private StandardWebhookEvent parseRazorpay(JsonNode root, String rawJson) {
        String eventId = root.path("id").asText();
        if (eventId == null || eventId.isBlank()) {
            eventId = root.path("event_id").asText();
        }
        String eventType = root.path("event").asText();
        long createdEpoch = root.path("created_at").asLong(Instant.now().getEpochSecond());
        Instant createdAt = Instant.ofEpochSecond(createdEpoch);

        JsonNode payload = root.path("payload");
        JsonNode paymentEntity = payload.path("payment").path("entity");
        JsonNode refundEntity = payload.path("refund").path("entity");

        String providerPaymentId = paymentEntity.path("id").asText(null);
        if (providerPaymentId == null && !refundEntity.isMissingNode()) {
            providerPaymentId = refundEntity.path("payment_id").asText(null);
        }

        String currency = paymentEntity.path("currency").asText("INR").toUpperCase();
        long amountCents = paymentEntity.path("amount").asLong(0);

        long capturedAmountCents = 0;
        if ("payment.captured".equalsIgnoreCase(eventType)) {
            capturedAmountCents = amountCents;
        }

        String refundId = null;
        Long refundAmountCents = null;
        String refundReason = null;

        if (!refundEntity.isMissingNode() && !refundEntity.isNull()) {
            refundId = refundEntity.path("id").asText(null);
            refundAmountCents = refundEntity.path("amount").asLong(0);
            refundReason = refundEntity.path("notes").path("reason").asText(null);
        }

        return new StandardWebhookEvent(
                eventId,
                PaymentProvider.RAZORPAY,
                eventType,
                providerPaymentId,
                null,
                paymentEntity.path("customer_id").asText(null),
                currency,
                amountCents,
                capturedAmountCents,
                refundId,
                refundAmountCents,
                refundReason,
                createdAt,
                rawJson
        );
    }

    public static class WebhookParseException extends BusinessException {
        public WebhookParseException(String message, Throwable cause) {
            super(message, cause, HttpStatus.BAD_REQUEST, "WEBHOOK_PARSE_ERROR");
        }
    }
}
