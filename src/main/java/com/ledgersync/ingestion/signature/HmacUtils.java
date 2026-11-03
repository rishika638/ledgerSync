package com.ledgersync.ingestion.signature;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HmacUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacUtils() {
    }

    public static byte[] computeHmacSha256(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC SHA256", e);
        }
    }

    public static String computeHmacSha256Hex(byte[] data, String secret) {
        byte[] rawMac = computeHmacSha256(data, secret.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(rawMac);
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }

    /**
     * Constant-time hex string comparison.
     */
    public static boolean constantTimeHexEquals(String hexA, String hexB) {
        if (hexA == null || hexB == null) {
            return false;
        }
        try {
            byte[] a = HexFormat.of().parseHex(hexA.trim().toLowerCase());
            byte[] b = HexFormat.of().parseHex(hexB.trim().toLowerCase());
            return constantTimeEquals(a, b);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
