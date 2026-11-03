package com.ledgersync.common.money;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value object representing an exact monetary amount in minor units (cents).
 * Guarantees zero floating-point arithmetic errors.
 */
public record MonetaryAmount(long amountCents, Currency currency) implements Comparable<MonetaryAmount>, Serializable {

    public MonetaryAmount {
        Objects.requireNonNull(currency, "Currency must not be null");
        if (amountCents < 0) {
            throw new IllegalArgumentException("Monetary amount cannot be negative: " + amountCents);
        }
    }

    public static MonetaryAmount ofCents(long amountCents, String currencyCode) {
        return new MonetaryAmount(amountCents, Currency.getInstance(currencyCode.toUpperCase()));
    }

    public static MonetaryAmount ofCents(long amountCents, Currency currency) {
        return new MonetaryAmount(amountCents, currency);
    }

    public static MonetaryAmount zero(Currency currency) {
        return new MonetaryAmount(0, currency);
    }

    public static MonetaryAmount zero(String currencyCode) {
        return new MonetaryAmount(0, Currency.getInstance(currencyCode.toUpperCase()));
    }

    public MonetaryAmount add(MonetaryAmount other) {
        validateSameCurrency(other);
        return new MonetaryAmount(Math.addExact(this.amountCents, other.amountCents), this.currency);
    }

    public MonetaryAmount subtract(MonetaryAmount other) {
        validateSameCurrency(other);
        if (this.amountCents < other.amountCents) {
            throw new IllegalArgumentException(
                    "Cannot subtract %s from %s without producing negative balance"
                            .formatted(other, this));
        }
        return new MonetaryAmount(this.amountCents - other.amountCents, this.currency);
    }

    public boolean isGreaterThan(MonetaryAmount other) {
        validateSameCurrency(other);
        return this.amountCents > other.amountCents;
    }

    public boolean isLessThan(MonetaryAmount other) {
        validateSameCurrency(other);
        return this.amountCents < other.amountCents;
    }

    public boolean isZero() {
        return this.amountCents == 0;
    }

    public BigDecimal toMajorUnits() {
        int fractionDigits = currency.getDefaultFractionDigits();
        return BigDecimal.valueOf(amountCents).movePointLeft(fractionDigits).setScale(fractionDigits, RoundingMode.UNNECESSARY);
    }

    private void validateSameCurrency(MonetaryAmount other) {
        Objects.requireNonNull(other, "Other MonetaryAmount must not be null");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: %s vs %s".formatted(this.currency, other.currency));
        }
    }

    @Override
    public int compareTo(MonetaryAmount other) {
        validateSameCurrency(other);
        return Long.compare(this.amountCents, other.amountCents);
    }

    @Override
    public String toString() {
        return "%s %s".formatted(toMajorUnits(), currency.getCurrencyCode());
    }
}
