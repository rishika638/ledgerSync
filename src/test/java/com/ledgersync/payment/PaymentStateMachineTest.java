package com.ledgersync.payment;

import com.ledgersync.common.error.InvalidStateTransitionException;
import com.ledgersync.payment.model.PaymentStatus;
import com.ledgersync.payment.service.PaymentStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStateMachineTest {

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine();
    }

    @Test
    @DisplayName("Valid normal progression: CREATED -> AUTHORIZED -> CAPTURED -> REFUNDED")
    void testNormalProgression() {
        var r1 = stateMachine.evaluateTransition(PaymentStatus.CREATED, PaymentStatus.AUTHORIZED);
        assertEquals(PaymentStatus.AUTHORIZED, r1.targetStatus());
        assertFalse(r1.isNoop());

        var r2 = stateMachine.evaluateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED);
        assertEquals(PaymentStatus.CAPTURED, r2.targetStatus());
        assertFalse(r2.isNoop());

        var r3 = stateMachine.evaluateTransition(PaymentStatus.CAPTURED, PaymentStatus.REFUNDED);
        assertEquals(PaymentStatus.REFUNDED, r3.targetStatus());
        assertFalse(r3.isNoop());
    }

    @Test
    @DisplayName("Direct capture without separate authorize: CREATED -> CAPTURED")
    void testDirectCapture() {
        var r = stateMachine.evaluateTransition(PaymentStatus.CREATED, PaymentStatus.CAPTURED);
        assertEquals(PaymentStatus.CAPTURED, r.targetStatus());
        assertFalse(r.isNoop());
    }

    @Test
    @DisplayName("Out-of-order event: Late AUTHORIZE arriving after CAPTURED is safely treated as NO-OP")
    void testLateAuthorizeIgnored() {
        var r = stateMachine.evaluateTransition(PaymentStatus.CAPTURED, PaymentStatus.AUTHORIZED);
        assertTrue(r.isNoop());
        assertEquals(PaymentStatus.CAPTURED, r.targetStatus());
    }

    @Test
    @DisplayName("Out-of-order event: Late FAILED arriving after CAPTURED is safely treated as NO-OP")
    void testLateFailedIgnored() {
        var r = stateMachine.evaluateTransition(PaymentStatus.CAPTURED, PaymentStatus.FAILED);
        assertTrue(r.isNoop());
        assertEquals(PaymentStatus.CAPTURED, r.targetStatus());
    }

    @Test
    @DisplayName("Terminal state: Events arriving after REFUNDED are ignored safely")
    void testTerminalStateIgnored() {
        var r = stateMachine.evaluateTransition(PaymentStatus.REFUNDED, PaymentStatus.AUTHORIZED);
        assertTrue(r.isNoop());
        assertEquals(PaymentStatus.REFUNDED, r.targetStatus());
    }

    @Test
    @DisplayName("Illegal transition throws InvalidStateTransitionException")
    void testIllegalTransition() {
        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.evaluateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.REFUNDED));
    }
}
