package com.ledgersync.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.common.error.InvalidSignatureException;
import com.ledgersync.common.id.IdGenerator;
import com.ledgersync.ingestion.dto.StandardWebhookEvent;
import com.ledgersync.ingestion.dto.WebhookIngestionResponse;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.ingestion.model.WebhookEventEntity;
import com.ledgersync.ingestion.model.WebhookEventStatus;
import com.ledgersync.ingestion.repository.WebhookEventRepository;
import com.ledgersync.ingestion.signature.ProviderSignatureVerifier;
import com.ledgersync.observability.MetricsConfig;
import com.ledgersync.queue.model.InboxMessageEntity;
import com.ledgersync.queue.model.InboxStatus;
import com.ledgersync.queue.repository.InboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WebhookIngestionService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestionService.class);

    private final Map<PaymentProvider, ProviderSignatureVerifier> verifiers;
    private final WebhookParser parser;
    private final WebhookEventRepository eventRepository;
    private final InboxMessageRepository inboxRepository;
    private final MetricsConfig metrics;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public WebhookIngestionService(
            List<ProviderSignatureVerifier> verifierList,
            WebhookParser parser,
            WebhookEventRepository eventRepository,
            InboxMessageRepository inboxRepository,
            MetricsConfig metrics,
            ObjectMapper objectMapper,
            @Value("${ledgersync.inbox.max-retries:5}") int maxAttempts) {
        this.verifiers = verifierList.stream()
                .collect(Collectors.toMap(ProviderSignatureVerifier::getProvider, Function.identity()));
        this.parser = parser;
        this.eventRepository = eventRepository;
        this.inboxRepository = inboxRepository;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public WebhookIngestionResponse ingest(PaymentProvider provider, byte[] rawBytes, String signatureHeader) {
        metrics.incrementWebhooksReceived();

        // 1. Verify Cryptographic Signature
        ProviderSignatureVerifier verifier = verifiers.get(provider);
        if (verifier == null) {
            throw new IllegalArgumentException("Unsupported payment provider: " + provider);
        }

        boolean valid = verifier.verify(rawBytes, signatureHeader);
        if (!valid) {
            metrics.incrementWebhooksInvalidSignature();
            log.warn("Invalid signature rejected for provider: {}", provider);
            throw new InvalidSignatureException("Webhook signature verification failed for " + provider);
        }

        // 2. Parse payload into normalized representation
        StandardWebhookEvent event = parser.parse(provider, rawBytes);
        String providerEventId = event.eventId();

        if (providerEventId == null || providerEventId.isBlank()) {
            throw new IllegalArgumentException("Provider event id missing from payload");
        }

        // 3. Check for idempotency/duplicate
        Optional<WebhookEventEntity> existing = eventRepository.findByProviderAndProviderEventId(provider, providerEventId);
        if (existing.isPresent()) {
            metrics.incrementWebhooksDuplicates();
            log.info("Duplicate webhook received: provider={}, eventId={}. Skipping duplicate financial processing.",
                    provider, providerEventId);
            return WebhookIngestionResponse.duplicate(existing.get().getId(), providerEventId);
        }

        // 4. Persist Webhook Event and Enqueue into Inbox atomically
        String internalEventId = IdGenerator.webhookEventId();
        String rawJsonString = new String(rawBytes, StandardCharsets.UTF_8);

        WebhookEventEntity eventEntity = new WebhookEventEntity(
                internalEventId,
                provider,
                providerEventId,
                event.eventType(),
                rawJsonString,
                signatureHeader,
                Instant.now(),
                WebhookEventStatus.INGESTED
        );

        try {
            eventRepository.saveAndFlush(eventEntity);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition caught by DB unique constraint
            metrics.incrementWebhooksDuplicates();
            log.warn("Concurrent duplicate insertion prevented by database constraint: {}", ex.getMessage());
            WebhookEventEntity duplicate = eventRepository.findByProviderAndProviderEventId(provider, providerEventId)
                    .orElseThrow(() -> ex);
            return WebhookIngestionResponse.duplicate(duplicate.getId(), providerEventId);
        }

        String serializedEvent;
        try {
            serializedEvent = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            serializedEvent = rawJsonString;
        }

        InboxMessageEntity inboxMessage = new InboxMessageEntity(
                IdGenerator.inboxMessageId(),
                internalEventId,
                provider,
                event.eventType(),
                serializedEvent,
                InboxStatus.PENDING,
                0,
                maxAttempts,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        inboxRepository.save(inboxMessage);

        log.info("Webhook successfully ingested and enqueued in inbox: id={}, provider={}, eventId={}, eventType={}",
                internalEventId, provider, providerEventId, event.eventType());

        return WebhookIngestionResponse.accepted(internalEventId, providerEventId);
    }
}
