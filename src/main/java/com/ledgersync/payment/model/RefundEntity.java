package com.ledgersync.payment.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "refunds",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refund_provider_refund_id", columnNames = {"payment_id", "provider_refund_id"})
        }
)
public class RefundEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentTransactionEntity payment;

    @Column(name = "provider_refund_id", length = 128, nullable = false)
    private String providerRefundId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private RefundStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RefundEntity() {
    }

    public RefundEntity(String id, PaymentTransactionEntity payment, String providerRefundId,
                        long amountCents, String currency, String reason,
                        RefundStatus status, Instant createdAt) {
        this.id = id;
        this.payment = payment;
        this.providerRefundId = providerRefundId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PaymentTransactionEntity getPayment() {
        return payment;
    }

    public void setPayment(PaymentTransactionEntity payment) {
        this.payment = payment;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public void setProviderRefundId(String providerRefundId) {
        this.providerRefundId = providerRefundId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
