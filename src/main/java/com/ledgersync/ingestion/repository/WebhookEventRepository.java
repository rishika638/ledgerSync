package com.ledgersync.ingestion.repository;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.ingestion.model.WebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, String> {

    Optional<WebhookEventEntity> findByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);

    boolean existsByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);
}
