package com.ledgersync.reconciliation.model;

import com.ledgersync.ingestion.model.PaymentProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_discrepancies")
public class DiscrepancyEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRunEntity run;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32, nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 64, nullable = false)
    private DiscrepancyType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 32, nullable = false)
    private DiscrepancySeverity severity;

    @Column(name = "evidence", columnDefinition = "TEXT", nullable = false)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private DiscrepancyStatus status;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DiscrepancyEntity() {
    }

    public DiscrepancyEntity(String id, ReconciliationRunEntity run, String paymentId,
                             String providerPaymentId, PaymentProvider provider,
                             DiscrepancyType type, DiscrepancySeverity severity,
                             String evidence, DiscrepancyStatus status,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.run = run;
        this.paymentId = paymentId;
        this.providerPaymentId = providerPaymentId;
        this.provider = provider;
        this.type = type;
        this.severity = severity;
        this.evidence = evidence;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ReconciliationRunEntity getRun() {
        return run;
    }

    public void setRun(ReconciliationRunEntity run) {
        this.run = run;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public void setProvider(PaymentProvider provider) {
        this.provider = provider;
    }

    public DiscrepancyType getType() {
        return type;
    }

    public void setType(DiscrepancyType type) {
        this.type = type;
    }

    public DiscrepancySeverity getSeverity() {
        return severity;
    }

    public void setSeverity(DiscrepancySeverity severity) {
        this.severity = severity;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public DiscrepancyStatus getStatus() {
        return status;
    }

    public void setStatus(DiscrepancyStatus status) {
        this.status = status;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
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
