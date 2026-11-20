package com.ledgersync.finance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "ai_investigations",
        indexes = {
                @Index(name = "idx_ai_investigation_discrepancy", columnList = "discrepancy_id")
        }
)
public class AiInvestigationEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discrepancy_id", nullable = false)
    private DiscrepancyEntity discrepancy;

    @Column(name = "model_version", length = 64, nullable = false)
    private String modelVersion;

    @Column(name = "prompt_version", length = 32, nullable = false)
    private String promptVersion;

    @Column(name = "classification", length = 64, nullable = false)
    private String classification;

    @Column(name = "confidence_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal confidenceScore;

    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "possible_causes", columnDefinition = "TEXT", nullable = false)
    private String possibleCauses; // JSON array string

    @Column(name = "supporting_evidence", columnDefinition = "TEXT", nullable = false)
    private String supportingEvidence; // JSON object or array string

    @Column(name = "recommended_action", length = 64, nullable = false)
    private String recommendedAction;

    @Column(name = "action_reasoning", columnDefinition = "TEXT", nullable = false)
    private String actionReasoning;

    @Column(name = "missing_evidence", columnDefinition = "TEXT")
    private String missingEvidence;

    @Column(name = "requires_human_approval", nullable = false)
    private boolean requiresHumanApproval = true;

    @Column(name = "validation_passed", nullable = false)
    private boolean validationPassed = true;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AiInvestigationEntity() {
    }

    public AiInvestigationEntity(String id, DiscrepancyEntity discrepancy, String modelVersion,
                                 String promptVersion, String classification, BigDecimal confidenceScore,
                                 String summary, String possibleCauses, String supportingEvidence,
                                 String recommendedAction, String actionReasoning, String missingEvidence,
                                 boolean requiresHumanApproval, boolean validationPassed,
                                 String rawResponse) {
        this.id = id;
        this.discrepancy = discrepancy;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.classification = classification;
        this.confidenceScore = confidenceScore;
        this.summary = summary;
        this.possibleCauses = possibleCauses;
        this.supportingEvidence = supportingEvidence;
        this.recommendedAction = recommendedAction;
        this.actionReasoning = actionReasoning;
        this.missingEvidence = missingEvidence;
        this.requiresHumanApproval = requiresHumanApproval;
        this.validationPassed = validationPassed;
        this.rawResponse = rawResponse;
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

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPossibleCauses() {
        return possibleCauses;
    }

    public void setPossibleCauses(String possibleCauses) {
        this.possibleCauses = possibleCauses;
    }

    public String getSupportingEvidence() {
        return supportingEvidence;
    }

    public void setSupportingEvidence(String supportingEvidence) {
        this.supportingEvidence = supportingEvidence;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getActionReasoning() {
        return actionReasoning;
    }

    public void setActionReasoning(String actionReasoning) {
        this.actionReasoning = actionReasoning;
    }

    public String getMissingEvidence() {
        return missingEvidence;
    }

    public void setMissingEvidence(String missingEvidence) {
        this.missingEvidence = missingEvidence;
    }

    public boolean isRequiresHumanApproval() {
        return requiresHumanApproval;
    }

    public void setRequiresHumanApproval(boolean requiresHumanApproval) {
        this.requiresHumanApproval = requiresHumanApproval;
    }

    public boolean isValidationPassed() {
        return validationPassed;
    }

    public void setValidationPassed(boolean validationPassed) {
        this.validationPassed = validationPassed;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
