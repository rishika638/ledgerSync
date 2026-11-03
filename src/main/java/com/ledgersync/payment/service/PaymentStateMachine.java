package com.ledgersync.payment.service;

import com.ledgersync.common.error.InvalidStateTransitionException;
import com.ledgersync.payment.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentStateMachine {

    private static final Logger log = LoggerFactory.getLogger(PaymentStateMachine.class);

    public record TransitionResult(
            PaymentStatus targetStatus,
            boolean isNoop,
            String reason
    ) {
        public static TransitionResult transition(PaymentStatus targetStatus) {
            return new TransitionResult(targetStatus, false, "Transition accepted");
        }

        public static TransitionResult noop(PaymentStatus currentStatus, String reason) {
            return new TransitionResult(currentStatus, true, reason);
        }
    }

    /**
     * Determines target state given current state and target requested by incoming event.
     * Prevents stale, duplicate, or out-of-order events from corrupting current state.
     */
    public TransitionResult evaluateTransition(PaymentStatus currentStatus, PaymentStatus desiredStatus) {
        if (currentStatus == desiredStatus) {
            return TransitionResult.noop(currentStatus, "Identical status: already in " + currentStatus);
        }

        // Terminal states cannot be transitioned away from
        if (currentStatus.isTerminal()) {
            log.warn("Attempted transition from terminal state {} to {}. Stale event ignored.",
                    currentStatus, desiredStatus);
            return TransitionResult.noop(currentStatus,
                    "Terminal state " + currentStatus + " reached. Stale event ignored.");
        }

        // If currently CAPTURED or PARTIALLY_REFUNDED, ignore late CREATED, AUTHORIZED, FAILED, or CANCELLED events
        if ((currentStatus == PaymentStatus.CAPTURED || currentStatus == PaymentStatus.PARTIALLY_REFUNDED)
                && (desiredStatus == PaymentStatus.CREATED || desiredStatus == PaymentStatus.AUTHORIZED
                || desiredStatus == PaymentStatus.FAILED || desiredStatus == PaymentStatus.CANCELLED)) {
            log.info("Ignoring out-of-order/stale event requesting {} while transaction is already {}",
                    desiredStatus, currentStatus);
            return TransitionResult.noop(currentStatus, "Ignoring stale event (" + desiredStatus + ") for already captured transaction");
        }

        // If target is in allowed next states:
        if (currentStatus.getAllowedNextStates().contains(desiredStatus)) {
            return TransitionResult.transition(desiredStatus);
        }

        // Out-of-order fast-forward: CREATED -> CAPTURED (direct capture without separate auth event)
        if (currentStatus == PaymentStatus.CREATED && desiredStatus == PaymentStatus.CAPTURED) {
            return TransitionResult.transition(PaymentStatus.CAPTURED);
        }

        throw new InvalidStateTransitionException(
                "Illegal payment state transition from %s to %s".formatted(currentStatus, desiredStatus));
    }

    /**
     * Maps provider event types to payment status
     */
    public PaymentStatus mapEventTypeToStatus(String eventType) {
        if (eventType == null) {
            return PaymentStatus.CREATED;
        }

        String lower = eventType.toLowerCase();
        if (lower.contains("succeeded") || lower.contains("captured") || lower.equals("payment.captured")) {
            return PaymentStatus.CAPTURED;
        } else if (lower.contains("refund")) {
            return PaymentStatus.REFUNDED;
        } else if (lower.contains("failed") || lower.contains("payment_failed")) {
            return PaymentStatus.FAILED;
        } else if (lower.contains("canceled") || lower.contains("cancelled")) {
            return PaymentStatus.CANCELLED;
        } else if (lower.contains("authorized") || lower.contains("amount_capturable_updated")) {
            return PaymentStatus.AUTHORIZED;
        } else if (lower.contains("created")) {
            return PaymentStatus.CREATED;
        }

        return PaymentStatus.CREATED;
    }
}
