package com.ledgersync.ingestion.dto;

import java.time.Instant;

public record WebhookIngestionResponse(
        String internalEventId,
        String providerEventId,
        String status,
        boolean duplicate,
        String message,
        Instant ingestedAt
) {
    public static WebhookIngestionResponse accepted(String internalEventId, String providerEventId) {
        return new WebhookIngestionResponse(internalEventId, providerEventId, "ACCEPTED", false, "Event ingested successfully", Instant.now());
    }

    public static WebhookIngestionResponse duplicate(String internalEventId, String providerEventId) {
        return new WebhookIngestionResponse(internalEventId, providerEventId, "DUPLICATE", true, "Duplicate event ignored", Instant.now());
    }
}
