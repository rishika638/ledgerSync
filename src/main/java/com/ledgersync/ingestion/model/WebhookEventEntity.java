package com.ledgersync.ingestion.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_provider_event", columnNames = {"provider", "provider_event_id"})
        }
)
public class WebhookEventEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32, nullable = false)
    private PaymentProvider provider;

    @Column(name = "provider_event_id", length = 128, nullable = false)
    private String providerEventId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "raw_payload", columnDefinition = "TEXT", nullable = false)
    private String rawPayload;

    @Column(name = "signature_header", length = 512)
    private String signatureHeader;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private WebhookEventStatus status;

    public WebhookEventEntity() {
    }

    public WebhookEventEntity(String id, PaymentProvider provider, String providerEventId,
                              String eventType, String rawPayload, String signatureHeader,
                              Instant ingestedAt, WebhookEventStatus status) {
        this.id = id;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.rawPayload = rawPayload;
        this.signatureHeader = signatureHeader;
        this.ingestedAt = ingestedAt;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public void setProvider(PaymentProvider provider) {
        this.provider = provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(Instant ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public WebhookEventStatus getStatus() {
        return status;
    }

    public void setStatus(WebhookEventStatus status) {
        this.status = status;
    }
}
