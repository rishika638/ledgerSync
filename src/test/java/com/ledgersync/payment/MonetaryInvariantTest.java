package com.ledgersync.payment;

import com.ledgersync.common.money.MonetaryAmount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class MonetaryInvariantTest {

    @Test
    @DisplayName("MonetaryAmount: exact minor unit math avoids floating-point precision loss")
    void testPrecisionLossAvoidance() {
        // Standard IEEE 754 float: 0.1 + 0.2 = 0.30000000000000004
        MonetaryAmount a = MonetaryAmount.ofCents(10, "USD");
        MonetaryAmount b = MonetaryAmount.ofCents(20, "USD");
        MonetaryAmount sum = a.add(b);

        assertEquals(30, sum.amountCents());
        assertEquals(new BigDecimal("0.30"), sum.toMajorUnits());
    }

    @Test
    @DisplayName("MonetaryAmount: subtracting larger amount throws IllegalArgumentException")
    void testNegativeBalancePrevention() {
        MonetaryAmount balance = MonetaryAmount.ofCents(500, "USD");
        MonetaryAmount debit = MonetaryAmount.ofCents(600, "USD");

        assertThrows(IllegalArgumentException.class, () -> balance.subtract(debit));
    }

    @Test
    @DisplayName("MonetaryAmount: operations across different currencies fail immediately")
    void testCurrencyMismatchRejected() {
        MonetaryAmount usd = MonetaryAmount.ofCents(1000, "USD");
        MonetaryAmount eur = MonetaryAmount.ofCents(1000, "EUR");

        assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
    }
}
