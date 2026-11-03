package com.ledgersync.reconciliation.provider;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.payment.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockRazorpayClient implements PaymentProviderClient {

    private final Map<String, RemotePaymentRecord> remoteStore = new ConcurrentHashMap<>();

    public MockRazorpayClient() {
        registerRemoteRecord(new RemotePaymentRecord(
                "pay_rzp_demo_001",
                PaymentProvider.RAZORPAY,
                250000,
                250000,
                0,
                "INR",
                PaymentStatus.CAPTURED,
                Instant.now().minusSeconds(7200)
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
        return PaymentProvider.RAZORPAY;
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
