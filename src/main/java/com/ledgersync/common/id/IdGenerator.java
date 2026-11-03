package com.ledgersync.common.id;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Generates human-readable, time-ordered, collision-resistant identifiers.
 * Format: {prefix}_{timestamp_millis_hex}{random_hex}
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static String generate(String prefix) {
        long now = Instant.now().toEpochMilli();
        byte[] randomBytes = new byte[8];
        RANDOM.nextBytes(randomBytes);

        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            sb.append(prefix).append("_");
        }
        sb.append(Long.toHexString(now));
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String webhookEventId() {
        return generate("evt");
    }

    public static String inboxMessageId() {
        return generate("inb");
    }

    public static String outboxMessageId() {
        return generate("out");
    }

    public static String paymentId() {
        return generate("pay");
    }

    public static String refundId() {
        return generate("ref");
    }

    public static String auditId() {
        return generate("aud");
    }

    public static String reconciliationRunId() {
        return generate("rec");
    }

    public static String discrepancyId() {
        return generate("dsc");
    }
}
