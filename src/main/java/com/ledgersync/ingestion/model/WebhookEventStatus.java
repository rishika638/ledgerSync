package com.ledgersync.ingestion.model;

public enum WebhookEventStatus {
    INGESTED,
    PROCESSED,
    DUPLICATE,
    FAILED
}
