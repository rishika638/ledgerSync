package com.ledgersync.finance.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "finance_import_batches")
public class FinanceImportBatchEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "batch_name", length = 128, nullable = false)
    private String batchName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 32, nullable = false)
    private FinanceSourceType sourceType;

    @Column(name = "filename", length = 255, nullable = false)
    private String filename;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "COMPLETED";

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public FinanceImportBatchEntity() {
    }

    public FinanceImportBatchEntity(String id, String batchName, FinanceSourceType sourceType,
                                    String filename, int totalRows, int validRows, int invalidRows,
                                    String status, String errorSummary) {
        this.id = id;
        this.batchName = batchName;
        this.sourceType = sourceType;
        this.filename = filename;
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.invalidRows = invalidRows;
        this.status = status;
        this.errorSummary = errorSummary;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBatchName() {
        return batchName;
    }

    public void setBatchName(String batchName) {
        this.batchName = batchName;
    }

    public FinanceSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(FinanceSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = contentSha256;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public void setValidRows(int validRows) {
        this.validRows = validRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(int invalidRows) {
        this.invalidRows = invalidRows;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
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
