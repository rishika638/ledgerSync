package com.ledgersync.payment.model;

import com.ledgersync.ingestion.model.PaymentProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "payment_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_provider_payment_id", columnNames = {"provider", "provider_payment_id"}),
                @UniqueConstraint(name = "uk_payment_idempotency_key", columnNames = {"idempotency_key"})
        }
)
public class PaymentTransactionEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32, nullable = false)
    private PaymentProvider provider;

    @Column(name = "provider_payment_id", length = 128, nullable = false)
    private String providerPaymentId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "captured_amount_cents", nullable = false)
    private long capturedAmountCents;

    @Column(name = "refunded_amount_cents", nullable = false)
    private long refundedAmountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private PaymentStatus status;

    @Column(name = "provider_created_at")
    private Instant providerCreatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PaymentTransactionEntity() {
    }

    public PaymentTransactionEntity(String id, PaymentProvider provider, String providerPaymentId,
                                    String idempotencyKey, String customerId, String currency,
                                    long amountCents, long capturedAmountCents, long refundedAmountCents,
                                    PaymentStatus status, Instant providerCreatedAt,
                                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.currency = currency;
        this.amountCents = amountCents;
        this.capturedAmountCents = capturedAmountCents;
        this.refundedAmountCents = refundedAmountCents;
        this.status = status;
        this.providerCreatedAt = providerCreatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public long getCapturedAmountCents() {
        return capturedAmountCents;
    }

    public void setCapturedAmountCents(long capturedAmountCents) {
        this.capturedAmountCents = capturedAmountCents;
    }

    public long getRefundedAmountCents() {
        return refundedAmountCents;
    }

    public void setRefundedAmountCents(long refundedAmountCents) {
        this.refundedAmountCents = refundedAmountCents;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Instant getProviderCreatedAt() {
        return providerCreatedAt;
    }

    public void setProviderCreatedAt(Instant providerCreatedAt) {
        this.providerCreatedAt = providerCreatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
