package com.ledgersync.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    private final Counter webhooksReceived;
    private final Counter webhooksDuplicates;
    private final Counter webhooksInvalidSignature;
    private final Counter inboxProcessed;
    private final Counter inboxDeadLetters;
    private final Counter reconciliationDiscrepancies;

    public MetricsConfig(MeterRegistry registry) {
        this.webhooksReceived = Counter.builder("ledgersync.webhooks.received")
                .description("Total number of received webhooks")
                .register(registry);

        this.webhooksDuplicates = Counter.builder("ledgersync.webhooks.duplicates")
                .description("Total number of duplicate webhooks detected and ignored")
                .register(registry);

        this.webhooksInvalidSignature = Counter.builder("ledgersync.webhooks.invalid_signature")
                .description("Total number of webhooks rejected due to invalid signature")
                .register(registry);

        this.inboxProcessed = Counter.builder("ledgersync.inbox.processed")
                .description("Total number of inbox events successfully processed")
                .register(registry);

        this.inboxDeadLetters = Counter.builder("ledgersync.inbox.dead_letters")
                .description("Total number of events moved to dead-letter queue")
                .register(registry);

        this.reconciliationDiscrepancies = Counter.builder("ledgersync.reconciliation.discrepancies")
                .description("Total number of reconciliation discrepancies identified")
                .register(registry);
    }

    public void incrementWebhooksReceived() {
        webhooksReceived.increment();
    }

    public void incrementWebhooksDuplicates() {
        webhooksDuplicates.increment();
    }

    public void incrementWebhooksInvalidSignature() {
        webhooksInvalidSignature.increment();
    }

    public void incrementInboxProcessed() {
        inboxProcessed.increment();
    }

    public void incrementInboxDeadLetters() {
        inboxDeadLetters.increment();
    }

    public void incrementReconciliationDiscrepancies() {
        reconciliationDiscrepancies.increment();
    }
}
