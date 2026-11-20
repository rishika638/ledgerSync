package com.ledgersync.finance.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "reconciliation_matches",
        indexes = {
                @Index(name = "idx_match_run_id", columnList = "run_id"),
                @Index(name = "idx_match_records", columnList = "ledger_record_id, gateway_record_id, bank_record_id")
        }
)
public class ReconciliationMatchEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", length = 32, nullable = false)
    private MatchType matchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", length = 32, nullable = false)
    private MatchStatus matchStatus = MatchStatus.AUTOMATIC_CONFIRMED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_record_id")
    private FinanceRecordEntity ledgerRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gateway_record_id")
    private FinanceRecordEntity gatewayRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_record_id")
    private FinanceRecordEntity bankRecord;

    @Column(name = "ledger_amount_minor")
    private Long ledgerAmountMinor;

    @Column(name = "gateway_amount_minor")
    private Long gatewayAmountMinor;

    @Column(name = "bank_amount_minor")
    private Long bankAmountMinor;

    @Column(name = "fee_minor")
    private Long feeMinor = 0L;

    @Column(name = "tax_minor")
    private Long taxMinor = 0L;

    @Column(name = "confidence_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal confidenceScore = new BigDecimal("100.00");

    @Column(name = "match_notes", columnDefinition = "TEXT")
    private String matchNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ReconciliationMatchEntity() {
    }

    public ReconciliationMatchEntity(String id, String runId, MatchType matchType,
                                     MatchStatus matchStatus, FinanceRecordEntity ledgerRecord,
                                     FinanceRecordEntity gatewayRecord, FinanceRecordEntity bankRecord,
                                     Long ledgerAmountMinor, Long gatewayAmountMinor, Long bankAmountMinor,
                                     Long feeMinor, Long taxMinor, BigDecimal confidenceScore,
                                     String matchNotes) {
        this.id = id;
        this.runId = runId;
        this.matchType = matchType;
        this.matchStatus = matchStatus;
        this.ledgerRecord = ledgerRecord;
        this.gatewayRecord = gatewayRecord;
        this.bankRecord = bankRecord;
        this.ledgerAmountMinor = ledgerAmountMinor;
        this.gatewayAmountMinor = gatewayAmountMinor;
        this.bankAmountMinor = bankAmountMinor;
        this.feeMinor = feeMinor;
        this.taxMinor = taxMinor;
        this.confidenceScore = confidenceScore;
        this.matchNotes = matchNotes;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(MatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    public FinanceRecordEntity getLedgerRecord() {
        return ledgerRecord;
    }

    public void setLedgerRecord(FinanceRecordEntity ledgerRecord) {
        this.ledgerRecord = ledgerRecord;
    }

    public FinanceRecordEntity getGatewayRecord() {
        return gatewayRecord;
    }

    public void setGatewayRecord(FinanceRecordEntity gatewayRecord) {
        this.gatewayRecord = gatewayRecord;
    }

    public FinanceRecordEntity getBankRecord() {
        return bankRecord;
    }

    public void setBankRecord(FinanceRecordEntity bankRecord) {
        this.bankRecord = bankRecord;
    }

    public Long getLedgerAmountMinor() {
        return ledgerAmountMinor;
    }

    public void setLedgerAmountMinor(Long ledgerAmountMinor) {
        this.ledgerAmountMinor = ledgerAmountMinor;
    }

    public Long getGatewayAmountMinor() {
        return gatewayAmountMinor;
    }

    public void setGatewayAmountMinor(Long gatewayAmountMinor) {
        this.gatewayAmountMinor = gatewayAmountMinor;
    }

    public Long getBankAmountMinor() {
        return bankAmountMinor;
    }

    public void setBankAmountMinor(Long bankAmountMinor) {
        this.bankAmountMinor = bankAmountMinor;
    }

    public Long getFeeMinor() {
        return feeMinor;
    }

    public void setFeeMinor(Long feeMinor) {
        this.feeMinor = feeMinor;
    }

    public Long getTaxMinor() {
        return taxMinor;
    }

    public void setTaxMinor(Long taxMinor) {
        this.taxMinor = taxMinor;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getMatchNotes() {
        return matchNotes;
    }

    public void setMatchNotes(String matchNotes) {
        this.matchNotes = matchNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
