package com.ledgersync.queue.model;

public enum OutboxStatus {
    PENDING,
    DISPATCHED,
    FAILED
}
