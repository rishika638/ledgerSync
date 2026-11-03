package com.ledgersync.ingestion.signature;

import com.ledgersync.ingestion.model.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RazorpaySignatureVerifier implements ProviderSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(RazorpaySignatureVerifier.class);

    private final String secret;

    public RazorpaySignatureVerifier(
            @Value("${ledgersync.webhook.razorpay.secret:whsec_test_razorpay_secret_key_xyz789}") String secret) {
        this.secret = secret;
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.RAZORPAY;
    }

    @Override
    public boolean verify(byte[] rawBody, String signatureHeader) {
        if (rawBody == null || signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Razorpay signature verification failed: empty payload or signature header");
            return false;
        }

        String expectedSignatureHex = HmacUtils.computeHmacSha256Hex(rawBody, secret);
        boolean matched = HmacUtils.constantTimeHexEquals(signatureHeader, expectedSignatureHex);
        if (!matched) {
            log.warn("Razorpay webhook signature mismatch");
        }
        return matched;
    }
}
