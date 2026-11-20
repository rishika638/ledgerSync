package com.ledgersync.ingestion.signature;

import com.ledgersync.ingestion.model.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class StripeSignatureVerifier implements ProviderSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(StripeSignatureVerifier.class);

    private final String secret;
    private final long toleranceSeconds;

    public StripeSignatureVerifier(
            @Value("${ledgersync.webhook.stripe.secret:}") String secret,
            @Value("${ledgersync.webhook.stripe.tolerance-seconds:300}") long toleranceSeconds) {
        this.secret = secret;
        this.toleranceSeconds = toleranceSeconds;
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public boolean verify(byte[] rawBody, String signatureHeader) {
        if (secret == null || secret.isBlank() || rawBody == null || signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Stripe signature verification failed: missing secret, payload, or signature header");
            return false;
        }

        Long timestamp = null;
        List<String> signatures = new ArrayList<>();

        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length == 2) {
                if ("t".equalsIgnoreCase(keyValue[0])) {
                    try {
                        timestamp = Long.parseLong(keyValue[1]);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid timestamp format in Stripe signature header: {}", keyValue[1]);
                        return false;
                    }
                } else if ("v1".equalsIgnoreCase(keyValue[0])) {
                    signatures.add(keyValue[1]);
                }
            }
        }

        if (timestamp == null || signatures.isEmpty()) {
            log.warn("Stripe signature header missing timestamp or v1 signature");
            return false;
        }

        // Validate timestamp replay tolerance window
        long currentEpoch = Instant.now().getEpochSecond();
        if (Math.abs(currentEpoch - timestamp) > toleranceSeconds) {
            log.warn("Stripe webhook timestamp {} outside tolerance window (current: {}, tolerance: {}s)",
                    timestamp, currentEpoch, toleranceSeconds);
            return false;
        }

        // Expected signed payload: "{timestamp}.{rawBody}"
        byte[] timestampBytes = String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8);
        byte[] dot = new byte[]{'.'};
        byte[] signedPayload = new byte[timestampBytes.length + 1 + rawBody.length];
        System.arraycopy(timestampBytes, 0, signedPayload, 0, timestampBytes.length);
        signedPayload[timestampBytes.length] = '.';
        System.arraycopy(rawBody, 0, signedPayload, timestampBytes.length + 1, rawBody.length);

        String expectedSignatureHex = HmacUtils.computeHmacSha256Hex(signedPayload, secret);

        for (String sig : signatures) {
            if (HmacUtils.constantTimeHexEquals(sig, expectedSignatureHex)) {
                return true;
            }
        }

        log.warn("Stripe webhook signature mismatch");
        return false;
    }
}
