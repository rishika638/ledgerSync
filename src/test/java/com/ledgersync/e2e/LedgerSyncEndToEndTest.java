package com.ledgersync.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.audit.repository.AuditEventRepository;
import com.ledgersync.common.error.InvariantViolationException;
import com.ledgersync.ingestion.dto.StandardWebhookEvent;
import com.ledgersync.ingestion.dto.WebhookIngestionResponse;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.ingestion.model.WebhookEventEntity;
import com.ledgersync.ingestion.model.WebhookEventStatus;
import com.ledgersync.ingestion.repository.WebhookEventRepository;
import com.ledgersync.ingestion.service.WebhookIngestionService;
import com.ledgersync.ingestion.signature.HmacUtils;
import com.ledgersync.payment.model.PaymentStatus;
import com.ledgersync.payment.model.PaymentTransactionEntity;
import com.ledgersync.payment.model.RefundEntity;
import com.ledgersync.payment.repository.PaymentTransactionRepository;
import com.ledgersync.payment.repository.RefundRepository;
import com.ledgersync.payment.service.PaymentProcessingService;
import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;
import com.ledgersync.queue.repository.InboxMessageRepository;
import com.ledgersync.queue.repository.OutboxMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerSyncEndToEndTest {

    @Autowired
    private WebhookIngestionService ingestionService;

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private PaymentTransactionRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String STRIPE_SECRET = "whsec_test_secret_for_tests_123";

    private String createStripeSignatureHeader(byte[] payload, long timestamp) {
        byte[] timestampBytes = String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8);
        byte[] signedPayload = new byte[timestampBytes.length + 1 + payload.length];
        System.arraycopy(timestampBytes, 0, signedPayload, 0, timestampBytes.length);
        signedPayload[timestampBytes.length] = '.';
        System.arraycopy(payload, 0, signedPayload, timestampBytes.length + 1, payload.length);

        String signatureHex = HmacUtils.computeHmacSha256Hex(signedPayload, STRIPE_SECRET);
        return "t=" + timestamp + ",v1=" + signatureHex;
    }

    @Test
    @DisplayName("End-to-End: Webhook ingestion -> Inbox -> Payment processing -> Audit log -> Idempotent duplicate check -> Refund")
    void testCompleteLifecycle() throws Exception {
        // Step 1: Ingest signed payment_intent.succeeded webhook
        String providerEventId = "evt_e2e_test_999";
        String providerPaymentId = "pi_e2e_test_999";
        String payloadJson = """
                {
                  "id": "%s",
                  "type": "payment_intent.succeeded",
                  "created": 1700000000,
                  "data": {
                    "object": {
                      "id": "%s",
                      "amount": 10000,
                      "amount_received": 10000,
                      "currency": "usd",
                      "customer": "cus_e2e_123"
                    }
                  }
                }
                """.formatted(providerEventId, providerPaymentId);

        byte[] payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
        long now = Instant.now().getEpochSecond();
        String signatureHeader = createStripeSignatureHeader(payloadBytes, now);

        WebhookIngestionResponse ingestResp = ingestionService.ingest(
                PaymentProvider.STRIPE, payloadBytes, signatureHeader);

        assertNotNull(ingestResp.internalEventId());
        assertFalse(ingestResp.duplicate());

        // Verify Webhook event persisted in DB
        WebhookEventEntity eventEntity = webhookEventRepository.findById(ingestResp.internalEventId()).orElseThrow();
        assertEquals(WebhookEventStatus.INGESTED, eventEntity.getStatus());

        // Step 2: Idempotent duplicate delivery test
        WebhookIngestionResponse duplicateResp = ingestionService.ingest(
                PaymentProvider.STRIPE, payloadBytes, signatureHeader);
        assertTrue(duplicateResp.duplicate(), "Duplicate event must be acknowledged as duplicate");
        assertEquals(ingestResp.internalEventId(), duplicateResp.internalEventId());

        // Step 3: Process the event through the payment engine
        StandardWebhookEvent eventDto = new StandardWebhookEvent(
                providerEventId,
                PaymentProvider.STRIPE,
                "payment_intent.succeeded",
                providerPaymentId,
                null,
                "cus_e2e_123",
                "USD",
                10000L,
                10000L,
                null,
                null,
                null,
                Instant.now(),
                payloadJson
        );

        PaymentTransactionEntity payment = paymentProcessingService.processEvent(eventDto);
        assertNotNull(payment);
        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
        assertEquals(10000L, payment.getAmountCents());
        assertEquals(10000L, payment.getCapturedAmountCents());
        assertEquals(0L, payment.getRefundedAmountCents());

        // Step 4: Verify atomic audit event persisted
        var auditHistory = auditRepository.findByResourceTypeAndResourceId("PAYMENT", payment.getId(), null);
        assertFalse(auditHistory.isEmpty(), "Audit history must contain event");

        // Step 5: Process a partial refund of 3000 cents ($30.00)
        StandardWebhookEvent refundEvent = new StandardWebhookEvent(
                "evt_ref_1",
                PaymentProvider.STRIPE,
                "charge.refunded",
                providerPaymentId,
                null,
                "cus_e2e_123",
                "USD",
                10000L,
                10000L,
                "re_test_001",
                3000L,
                "Customer return",
                Instant.now(),
                "{}"
        );

        PaymentTransactionEntity partiallyRefunded = paymentProcessingService.processEvent(refundEvent);
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, partiallyRefunded.getStatus());
        assertEquals(3000L, partiallyRefunded.getRefundedAmountCents());

        List<RefundEntity> refunds = refundRepository.findByPaymentOrderByCreatedAtDesc(partiallyRefunded);
        assertEquals(1, refunds.size());
        assertEquals(3000L, refunds.get(0).getAmountCents());

        // Step 6: Over-refund invariant violation check
        StandardWebhookEvent excessiveRefundEvent = new StandardWebhookEvent(
                "evt_ref_excessive",
                PaymentProvider.STRIPE,
                "charge.refunded",
                providerPaymentId,
                null,
                "cus_e2e_123",
                "USD",
                10000L,
                10000L,
                "re_test_002",
                8000L, // 3000 already refunded + 8000 = 11000 > 10000 captured!
                "Fraudulent claim",
                Instant.now(),
                "{}"
        );

        assertThrows(InvariantViolationException.class, () ->
                paymentProcessingService.processEvent(excessiveRefundEvent));

        // State remains intact
        PaymentTransactionEntity preservedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, preservedPayment.getStatus());
        assertEquals(3000L, preservedPayment.getRefundedAmountCents());
    }
}
