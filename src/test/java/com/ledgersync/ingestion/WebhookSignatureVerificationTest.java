package com.ledgersync.ingestion;

import com.ledgersync.ingestion.signature.HmacUtils;
import com.ledgersync.ingestion.signature.RazorpaySignatureVerifier;
import com.ledgersync.ingestion.signature.StripeSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerificationTest {

    private static final String STRIPE_SECRET = "whsec_test_stripe_secret_key_abc123";
    private static final String RAZORPAY_SECRET = "whsec_test_razorpay_secret_key_xyz789";

    private StripeSignatureVerifier stripeVerifier;
    private RazorpaySignatureVerifier razorpayVerifier;

    @BeforeEach
    void setUp() {
        stripeVerifier = new StripeSignatureVerifier(STRIPE_SECRET, 300);
        razorpayVerifier = new RazorpaySignatureVerifier(RAZORPAY_SECRET);
    }

    @Test
    @DisplayName("Stripe: valid signature within tolerance window returns true")
    void testStripeValidSignature() {
        byte[] payload = "{\"id\":\"evt_test_123\",\"type\":\"payment_intent.succeeded\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = Instant.now().getEpochSecond();

        byte[] timestampBytes = String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8);
        byte[] signedPayload = new byte[timestampBytes.length + 1 + payload.length];
        System.arraycopy(timestampBytes, 0, signedPayload, 0, timestampBytes.length);
        signedPayload[timestampBytes.length] = '.';
        System.arraycopy(payload, 0, signedPayload, timestampBytes.length + 1, payload.length);

        String signatureHex = HmacUtils.computeHmacSha256Hex(signedPayload, STRIPE_SECRET);
        String header = "t=" + timestamp + ",v1=" + signatureHex;

        assertTrue(stripeVerifier.verify(payload, header));
    }

    @Test
    @DisplayName("Stripe: tampered payload fails verification")
    void testStripeTamperedPayload() {
        byte[] original = "{\"amount\":5000}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"amount\":9999}".getBytes(StandardCharsets.UTF_8);
        long timestamp = Instant.now().getEpochSecond();

        String header = "t=" + timestamp + ",v1=" + HmacUtils.computeHmacSha256Hex(
                (timestamp + ".{\"amount\":5000}").getBytes(StandardCharsets.UTF_8), STRIPE_SECRET);

        assertFalse(stripeVerifier.verify(tampered, header));
    }

    @Test
    @DisplayName("Stripe: expired timestamp beyond tolerance window rejected (replay attack prevention)")
    void testStripeExpiredTimestamp() {
        byte[] payload = "{\"id\":\"evt_123\"}".getBytes(StandardCharsets.UTF_8);
        long oldTimestamp = Instant.now().getEpochSecond() - 600; // 10 minutes ago, tolerance is 5 min

        String header = "t=" + oldTimestamp + ",v1=" + HmacUtils.computeHmacSha256Hex(
                (oldTimestamp + ".{\"id\":\"evt_123\"}").getBytes(StandardCharsets.UTF_8), STRIPE_SECRET);

        assertFalse(stripeVerifier.verify(payload, header));
    }

    @Test
    @DisplayName("Razorpay: valid signature returns true")
    void testRazorpayValidSignature() {
        byte[] payload = "{\"event\":\"payment.captured\",\"payload\":{}}".getBytes(StandardCharsets.UTF_8);
        String signatureHex = HmacUtils.computeHmacSha256Hex(payload, RAZORPAY_SECRET);

        assertTrue(razorpayVerifier.verify(payload, signatureHex));
    }

    @Test
    @DisplayName("Razorpay: invalid signature returns false")
    void testRazorpayInvalidSignature() {
        byte[] payload = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(razorpayVerifier.verify(payload, "invalid_signature_hex_0000000000000000"));
    }
}
