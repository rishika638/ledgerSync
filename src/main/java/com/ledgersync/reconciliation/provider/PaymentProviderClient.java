package com.ledgersync.reconciliation.provider;

import com.ledgersync.ingestion.model.PaymentProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentProviderClient {

    PaymentProvider getProvider();

    List<RemotePaymentRecord> fetchSettledTransactions(Instant from, Instant to, int limit);

    Optional<RemotePaymentRecord> fetchTransaction(String providerPaymentId);
}
