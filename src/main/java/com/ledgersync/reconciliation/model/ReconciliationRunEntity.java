package com.ledgersync.reconciliation.model;

import com.ledgersync.ingestion.model.PaymentProvider;
import jakarta.persistence.*;
import java.math.BigDecimal;
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

    // 3-Way Reconciliation Extensions
    @Column(name = "run_type", length = 32)
    private String runType = "THREE_WAY_SETTLEMENT";

    @Column(name = "total_ledger_records")
    private int totalLedgerRecords;

    @Column(name = "total_gateway_records")
    private int totalGatewayRecords;

    @Column(name = "total_bank_records")
    private int totalBankRecords;

    @Column(name = "matched_records_count")
    private int matchedRecordsCount;

    @Column(name = "unmatched_records_count")
    private int unmatchedRecordsCount;

    @Column(name = "precision_rate", precision = 5, scale = 2)
    private BigDecimal precisionRate;

    @Column(name = "recall_rate", precision = 5, scale = 2)
    private BigDecimal recallRate;

    @Column(name = "match_rate", precision = 5, scale = 2)
    private BigDecimal matchRate = BigDecimal.ZERO;

    @Column(name = "unmatched_amount_minor")
    private long unmatchedAmountMinor;

    @Column(name = "processing_duration_ms")
    private long processingDurationMs;

    @Column(name = "input_batch_ids", columnDefinition = "TEXT")
    private String inputBatchIds = "[]";

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

    public String getRunType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType;
    }

    public int getTotalLedgerRecords() {
        return totalLedgerRecords;
    }

    public void setTotalLedgerRecords(int totalLedgerRecords) {
        this.totalLedgerRecords = totalLedgerRecords;
    }

    public int getTotalGatewayRecords() {
        return totalGatewayRecords;
    }

    public void setTotalGatewayRecords(int totalGatewayRecords) {
        this.totalGatewayRecords = totalGatewayRecords;
    }

    public int getTotalBankRecords() {
        return totalBankRecords;
    }

    public void setTotalBankRecords(int totalBankRecords) {
        this.totalBankRecords = totalBankRecords;
    }

    public int getMatchedRecordsCount() {
        return matchedRecordsCount;
    }

    public void setMatchedRecordsCount(int matchedRecordsCount) {
        this.matchedRecordsCount = matchedRecordsCount;
    }

    public int getUnmatchedRecordsCount() {
        return unmatchedRecordsCount;
    }

    public void setUnmatchedRecordsCount(int unmatchedRecordsCount) {
        this.unmatchedRecordsCount = unmatchedRecordsCount;
    }

    public BigDecimal getPrecisionRate() {
        return precisionRate;
    }

    public void setPrecisionRate(BigDecimal precisionRate) {
        this.precisionRate = precisionRate;
    }

    public BigDecimal getRecallRate() {
        return recallRate;
    }

    public void setRecallRate(BigDecimal recallRate) {
        this.recallRate = recallRate;
    }

    public BigDecimal getMatchRate() {
        return matchRate;
    }

    public void setMatchRate(BigDecimal matchRate) {
        this.matchRate = matchRate;
    }

    public long getUnmatchedAmountMinor() {
        return unmatchedAmountMinor;
    }

    public void setUnmatchedAmountMinor(long unmatchedAmountMinor) {
        this.unmatchedAmountMinor = unmatchedAmountMinor;
    }

    public long getProcessingDurationMs() {
        return processingDurationMs;
    }

    public String getInputBatchIds() {
        return inputBatchIds;
    }

    public void setInputBatchIds(String inputBatchIds) {
        this.inputBatchIds = inputBatchIds;
    }

    public void setProcessingDurationMs(long processingDurationMs) {
        this.processingDurationMs = processingDurationMs;
    }
}
