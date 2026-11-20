package com.ledgersync.finance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "resolution_actions",
        indexes = {
                @Index(name = "idx_resolution_actions_discrepancy", columnList = "discrepancy_id")
        }
)
public class ResolutionActionEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discrepancy_id", nullable = false)
    private DiscrepancyEntity discrepancy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id")
    private AiInvestigationEntity investigation;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 64, nullable = false)
    private ResolutionActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ResolutionActionStatus status = ResolutionActionStatus.APPROVED;

    @Column(name = "actor", length = 128, nullable = false)
    private String actor;

    @Column(name = "decision_notes", columnDefinition = "TEXT")
    private String decisionNotes;

    @Column(name = "financial_adjustment_minor")
    private long financialAdjustmentMinor = 0;

    @Column(name = "applied_at")
    private Instant appliedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ResolutionActionEntity() {
    }

    public ResolutionActionEntity(String id, DiscrepancyEntity discrepancy,
                                  AiInvestigationEntity investigation, ResolutionActionType actionType,
                                  ResolutionActionStatus status, String actor, String decisionNotes,
                                  long financialAdjustmentMinor) {
        this.id = id;
        this.discrepancy = discrepancy;
        this.investigation = investigation;
        this.actionType = actionType;
        this.status = status;
        this.actor = actor;
        this.decisionNotes = decisionNotes;
        this.financialAdjustmentMinor = financialAdjustmentMinor;
        this.appliedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonIgnore
    public DiscrepancyEntity getDiscrepancy() {
        return discrepancy;
    }

    public void setDiscrepancy(DiscrepancyEntity discrepancy) {
        this.discrepancy = discrepancy;
    }

    @JsonIgnore
    public AiInvestigationEntity getInvestigation() {
        return investigation;
    }

    public void setInvestigation(AiInvestigationEntity investigation) {
        this.investigation = investigation;
    }

    public ResolutionActionType getActionType() {
        return actionType;
    }

    public void setActionType(ResolutionActionType actionType) {
        this.actionType = actionType;
    }

    public ResolutionActionStatus getStatus() {
        return status;
    }

    public void setStatus(ResolutionActionStatus status) {
        this.status = status;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }

    public long getFinancialAdjustmentMinor() {
        return financialAdjustmentMinor;
    }

    public void setFinancialAdjustmentMinor(long financialAdjustmentMinor) {
        this.financialAdjustmentMinor = financialAdjustmentMinor;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
