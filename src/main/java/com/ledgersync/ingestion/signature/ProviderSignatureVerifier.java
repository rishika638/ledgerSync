package com.ledgersync.ingestion.signature;

import com.ledgersync.ingestion.model.PaymentProvider;

public interface ProviderSignatureVerifier {

    PaymentProvider getProvider();

    /**
     * Verifies cryptographic signature against raw request bytes.
     *
     * @param rawBody         exact raw HTTP payload bytes
     * @param signatureHeader signature header received from the payment provider
     * @return true if signature is valid and within replay tolerance window
     */
    boolean verify(byte[] rawBody, String signatureHeader);
}
