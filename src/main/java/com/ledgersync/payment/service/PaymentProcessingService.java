package com.ledgersync.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.audit.service.AuditService;
import com.ledgersync.common.error.InvariantViolationException;
import com.ledgersync.common.id.IdGenerator;
import com.ledgersync.common.money.MonetaryAmount;
import com.ledgersync.ingestion.dto.StandardWebhookEvent;
import com.ledgersync.payment.model.PaymentStatus;
import com.ledgersync.payment.model.PaymentTransactionEntity;
import com.ledgersync.payment.model.RefundEntity;
import com.ledgersync.payment.model.RefundStatus;
import com.ledgersync.payment.repository.PaymentTransactionRepository;
import com.ledgersync.payment.repository.RefundRepository;
import com.ledgersync.queue.model.OutboxMessageEntity;
import com.ledgersync.queue.model.OutboxStatus;
import com.ledgersync.queue.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Propagation;

@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final PaymentTransactionRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OutboxMessageRepository outboxRepository;
    private final AuditService auditService;
    private final PaymentStateMachine stateMachine;
    private final ObjectMapper objectMapper;

    public PaymentProcessingService(
            PaymentTransactionRepository paymentRepository,
            RefundRepository refundRepository,
            OutboxMessageRepository outboxRepository,
            AuditService auditService,
            PaymentStateMachine stateMachine,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.stateMachine = stateMachine;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public PaymentTransactionEntity processEvent(StandardWebhookEvent event) {
        log.info("Processing financial event: provider={}, eventId={}, type={}, paymentId={}",
                event.provider(), event.eventId(), event.eventType(), event.providerPaymentId());

        String providerPaymentId = event.providerPaymentId();
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("Event must have an associated providerPaymentId");
        }

        // Lock existing payment row for update if present
        Optional<PaymentTransactionEntity> existingOpt = paymentRepository
                .findByProviderAndProviderPaymentIdForUpdate(event.provider(), providerPaymentId);

        PaymentTransactionEntity payment;
        String beforeStateJson = null;

        if (existingOpt.isPresent()) {
            payment = existingOpt.get();
            beforeStateJson = serializeState(payment);

            // Validate currency consistency
            if (event.currency() != null && !event.currency().equalsIgnoreCase(payment.getCurrency())) {
                throw new InvariantViolationException(
                        "Currency mismatch for payment %s: existing is %s, event specified %s"
                                .formatted(providerPaymentId, payment.getCurrency(), event.currency()));
            }

            // Check if this is a refund event
            if (isRefundEvent(event)) {
                processRefund(payment, event);
            } else {
                processStateAndAmountUpdate(payment, event);
            }
            payment.setUpdatedAt(Instant.now());
        } else {
            // New transaction creation
            payment = createNewPayment(event);
        }

        PaymentTransactionEntity savedPayment = paymentRepository.saveAndFlush(payment);
        String afterStateJson = serializeState(savedPayment);

        // Record Audit Trail atomically in same transaction
        auditService.record(
                "PROCESS_PAYMENT_EVENT",
                "worker",
                "PAYMENT",
                savedPayment.getId(),
                beforeStateJson,
                afterStateJson
        );

        // Record Outbox Event for downstream consumers
        createOutboxMessage(savedPayment, event.eventType());

        return savedPayment;
    }

    private PaymentTransactionEntity createNewPayment(StandardWebhookEvent event) {
        PaymentStatus initialStatus = stateMachine.mapEventTypeToStatus(event.eventType());

        long amount = Optional.ofNullable(event.amountCents()).orElse(0L);
        long capturedAmount = Optional.ofNullable(event.capturedAmountCents()).orElse(0L);
        String currency = Optional.ofNullable(event.currency()).orElse("USD").toUpperCase();

        // Enforce invariants on creation
        MonetaryAmount totalAmount = MonetaryAmount.ofCents(amount, currency);
        MonetaryAmount initialCaptured = MonetaryAmount.ofCents(capturedAmount, currency);

        if (initialCaptured.isGreaterThan(totalAmount)) {
            throw new InvariantViolationException(
                    "Captured amount %s cannot exceed total amount %s".formatted(initialCaptured, totalAmount));
        }

        String internalId = IdGenerator.paymentId();
        PaymentTransactionEntity newPayment = new PaymentTransactionEntity(
                internalId,
                event.provider(),
                event.providerPaymentId(),
                event.idempotencyKey(),
                event.customerId(),
                currency,
                amount,
                capturedAmount,
                0,
                initialStatus,
                event.providerCreatedAt() != null ? event.providerCreatedAt() : Instant.now(),
                Instant.now(),
                Instant.now()
        );

        log.info("Creating new payment record: internalId={}, providerPaymentId={}, status={}",
                internalId, event.providerPaymentId(), initialStatus);

        return newPayment;
    }

    private void processStateAndAmountUpdate(PaymentTransactionEntity payment, StandardWebhookEvent event) {
        PaymentStatus desiredStatus = stateMachine.mapEventTypeToStatus(event.eventType());
        PaymentStateMachine.TransitionResult result = stateMachine.evaluateTransition(payment.getStatus(), desiredStatus);

        if (!result.isNoop()) {
            log.info("Transitioning payment {} from {} to {}", payment.getId(), payment.getStatus(), result.targetStatus());
            payment.setStatus(result.targetStatus());
        } else {
            log.info("No state transition applied for payment {}: {}", payment.getId(), result.reason());
        }

        // Update amounts if event contains updated captured amount
        if (event.capturedAmountCents() != null && event.capturedAmountCents() > payment.getCapturedAmountCents()) {
            MonetaryAmount newCaptured = MonetaryAmount.ofCents(event.capturedAmountCents(), payment.getCurrency());
            MonetaryAmount totalAmount = MonetaryAmount.ofCents(payment.getAmountCents(), payment.getCurrency());

            if (newCaptured.isGreaterThan(totalAmount)) {
                throw new InvariantViolationException(
                        "New captured amount %s exceeds authorized total amount %s".formatted(newCaptured, totalAmount));
            }
            payment.setCapturedAmountCents(newCaptured.amountCents());
        }
    }

    private void processRefund(PaymentTransactionEntity payment, StandardWebhookEvent event) {
        String providerRefundId = event.refundId();
        if (providerRefundId == null || providerRefundId.isBlank()) {
            providerRefundId = "ref_" + event.eventId();
        }

        // Check if refund already applied idempotently
        if (refundRepository.existsByPaymentAndProviderRefundId(payment, providerRefundId)) {
            log.info("Duplicate refund event {} already applied to payment {}. Skipping.",
                    providerRefundId, payment.getId());
            return;
        }

        long refundAmount = Optional.ofNullable(event.refundAmountCents()).orElse(0L);
        if (refundAmount <= 0) {
            log.warn("Refund event had non-positive amount {}. Skipping.", refundAmount);
            return;
        }

        MonetaryAmount incomingRefund = MonetaryAmount.ofCents(refundAmount, payment.getCurrency());
        MonetaryAmount currentRefunded = MonetaryAmount.ofCents(payment.getRefundedAmountCents(), payment.getCurrency());
        MonetaryAmount captured = MonetaryAmount.ofCents(payment.getCapturedAmountCents(), payment.getCurrency());

        MonetaryAmount newTotalRefunded = currentRefunded.add(incomingRefund);
        if (newTotalRefunded.isGreaterThan(captured)) {
            throw new InvariantViolationException(
                    "Total refunded amount %s would exceed captured amount %s on payment %s"
                            .formatted(newTotalRefunded, captured, payment.getId()));
        }

        // Apply refund
        payment.setRefundedAmountCents(newTotalRefunded.amountCents());
        if (newTotalRefunded.compareTo(captured) == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }

        RefundEntity refundEntity = new RefundEntity(
                IdGenerator.refundId(),
                payment,
                providerRefundId,
                refundAmount,
                payment.getCurrency(),
                event.refundReason(),
                RefundStatus.SUCCEEDED,
                Instant.now()
        );
        refundRepository.save(refundEntity);

        log.info("Refund {} of {} successfully applied to payment {}. New status={}",
                providerRefundId, incomingRefund, payment.getId(), payment.getStatus());
    }

    private boolean isRefundEvent(StandardWebhookEvent event) {
        String type = event.eventType() != null ? event.eventType().toLowerCase() : "";
        return type.contains("refund") || event.refundAmountCents() != null;
    }

    private void createOutboxMessage(PaymentTransactionEntity payment, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(payment);
            OutboxMessageEntity outbox = new OutboxMessageEntity(
                    IdGenerator.outboxMessageId(),
                    "PAYMENT",
                    payment.getId(),
                    "payment." + payment.getStatus().name().toLowerCase(),
                    payload,
                    OutboxStatus.PENDING,
                    Instant.now()
            );
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize outbox message for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    private String serializeState(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
