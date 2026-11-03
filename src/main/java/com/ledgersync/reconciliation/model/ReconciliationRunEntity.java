package com.ledgersync.reconciliation.model;

import com.ledgersync.ingestion.model.PaymentProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRunEntity {

    public enum RunStatus {
        STARTED,
        COMPLETED,
        FAILED
    }

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32, nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private RunStatus status;

    @Column(name = "total_remote_checked", nullable = false)
    private int totalRemoteChecked;

    @Column(name = "total_local_checked", nullable = false)
    private int totalLocalChecked;

    @Column(name = "discrepancies_count", nullable = false)
    private int discrepanciesCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public ReconciliationRunEntity() {
    }

    public ReconciliationRunEntity(String id, PaymentProvider provider, RunStatus status, Instant startedAt) {
        this.id = id;
        this.provider = provider;
        this.status = status;
        this.startedAt = startedAt;
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

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public int getTotalRemoteChecked() {
        return totalRemoteChecked;
    }

    public void setTotalRemoteChecked(int totalRemoteChecked) {
        this.totalRemoteChecked = totalRemoteChecked;
    }

    public int getTotalLocalChecked() {
        return totalLocalChecked;
    }

    public void setTotalLocalChecked(int totalLocalChecked) {
        this.totalLocalChecked = totalLocalChecked;
    }

    public int getDiscrepanciesCount() {
        return discrepanciesCount;
    }

    public void setDiscrepanciesCount(int discrepanciesCount) {
        this.discrepanciesCount = discrepanciesCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
