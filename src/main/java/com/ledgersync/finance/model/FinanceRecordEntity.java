package com.ledgersync.finance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "finance_records",
        indexes = {
                @Index(name = "idx_finance_record_lookup", columnList = "source_type, record_reference"),
                @Index(name = "idx_finance_record_order", columnList = "order_reference"),
                @Index(name = "idx_finance_record_secondary", columnList = "secondary_reference"),
                @Index(name = "idx_finance_record_batch", columnList = "batch_id"),
                @Index(name = "idx_finance_record_status", columnList = "status")
        }
)
public class FinanceRecordEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private FinanceImportBatchEntity batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 32, nullable = false)
    private FinanceSourceType sourceType;

    @Column(name = "record_reference", length = 128, nullable = false)
    private String recordReference;

    @Column(name = "order_reference", length = 128)
    private String orderReference;

    @Column(name = "secondary_reference", length = 128)
    private String secondaryReference;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "INR";

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor = 0;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor = 0;

    @Column(name = "net_amount_minor", nullable = false)
    private long netAmountMinor = 0;

    @Column(name = "transaction_timestamp", nullable = false)
    private Instant transactionTimestamp;

    @Column(name = "settlement_timestamp")
    private Instant settlementTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private FinanceRecordStatus status = FinanceRecordStatus.UNRECONCILED;

    @Column(name = "raw_metadata", columnDefinition = "TEXT")
    private String rawMetadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public FinanceRecordEntity() {
    }

    public FinanceRecordEntity(String id, FinanceImportBatchEntity batch, FinanceSourceType sourceType,
                               String recordReference, String orderReference, String secondaryReference,
                               String currency, long amountMinor, long feeMinor, long taxMinor,
                               long netAmountMinor, Instant transactionTimestamp, Instant settlementTimestamp,
                               FinanceRecordStatus status, String rawMetadata) {
        this.id = id;
        this.batch = batch;
        this.sourceType = sourceType;
        this.recordReference = recordReference;
        this.orderReference = orderReference;
        this.secondaryReference = secondaryReference;
        this.currency = currency;
        this.amountMinor = amountMinor;
        this.feeMinor = feeMinor;
        this.taxMinor = taxMinor;
        this.netAmountMinor = netAmountMinor;
        this.transactionTimestamp = transactionTimestamp;
        this.settlementTimestamp = settlementTimestamp;
        this.status = status;
        this.rawMetadata = rawMetadata;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonIgnore
    public FinanceImportBatchEntity getBatch() {
        return batch;
    }

    public String getBatchId() {
        return batch != null ? batch.getId() : null;
    }

    public void setBatch(FinanceImportBatchEntity batch) {
        this.batch = batch;
    }

    public FinanceSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(FinanceSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getRecordReference() {
        return recordReference;
    }

    public void setRecordReference(String recordReference) {
        this.recordReference = recordReference;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public void setOrderReference(String orderReference) {
        this.orderReference = orderReference;
    }

    public String getSecondaryReference() {
        return secondaryReference;
    }

    public void setSecondaryReference(String secondaryReference) {
        this.secondaryReference = secondaryReference;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public long getFeeMinor() {
        return feeMinor;
    }

    public void setFeeMinor(long feeMinor) {
        this.feeMinor = feeMinor;
    }

    public long getTaxMinor() {
        return taxMinor;
    }

    public void setTaxMinor(long taxMinor) {
        this.taxMinor = taxMinor;
    }

    public long getNetAmountMinor() {
        return netAmountMinor;
    }

    public void setNetAmountMinor(long netAmountMinor) {
        this.netAmountMinor = netAmountMinor;
    }

    public Instant getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public void setTransactionTimestamp(Instant transactionTimestamp) {
        this.transactionTimestamp = transactionTimestamp;
    }

    public Instant getSettlementTimestamp() {
        return settlementTimestamp;
    }

    public void setSettlementTimestamp(Instant settlementTimestamp) {
        this.settlementTimestamp = settlementTimestamp;
    }

    public FinanceRecordStatus getStatus() {
        return status;
    }

    public void setStatus(FinanceRecordStatus status) {
        this.status = status;
    }

    public String getRawMetadata() {
        return rawMetadata;
    }

    public void setRawMetadata(String rawMetadata) {
        this.rawMetadata = rawMetadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
