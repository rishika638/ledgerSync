package com.ledgersync.reconciliation.provider;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.payment.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockStripeClient implements PaymentProviderClient {

    private final Map<String, RemotePaymentRecord> remoteStore = new ConcurrentHashMap<>();

    public MockStripeClient() {
        // Default deterministic simulator fixtures
        registerRemoteRecord(new RemotePaymentRecord(
                "pi_stripe_demo_001",
                PaymentProvider.STRIPE,
                10000,
                10000,
                0,
                "USD",
                PaymentStatus.CAPTURED,
                Instant.now().minusSeconds(3600)
        ));
        registerRemoteRecord(new RemotePaymentRecord(
                "pi_stripe_demo_missing_webhook",
                PaymentProvider.STRIPE,
                7500,
                7500,
                0,
                "USD",
                PaymentStatus.CAPTURED,
                Instant.now().minusSeconds(1800)
        ));
    }

    public void registerRemoteRecord(RemotePaymentRecord record) {
        remoteStore.put(record.providerPaymentId(), record);
    }

    public void clear() {
        remoteStore.clear();
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public List<RemotePaymentRecord> fetchSettledTransactions(Instant from, Instant to, int limit) {
        return remoteStore.values().stream()
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<RemotePaymentRecord> fetchTransaction(String providerPaymentId) {
        return Optional.ofNullable(remoteStore.get(providerPaymentId));
    }
}
