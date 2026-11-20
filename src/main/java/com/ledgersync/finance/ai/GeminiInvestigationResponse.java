package com.ledgersync.finance.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiInvestigationResponse {

    @JsonProperty("classification")
    private String classification;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("possible_causes")
    private List<String> possibleCauses = new ArrayList<>();

    @JsonProperty("supporting_evidence")
    private List<String> supportingEvidence = new ArrayList<>();

    @JsonProperty("referenced_record_ids")
    private List<String> referencedRecordIds = new ArrayList<>();

    @JsonProperty("recommended_action")
    private String recommendedAction = "REQUEST_EVIDENCE";

    @JsonProperty("action_reasoning")
    private String actionReasoning;

    @JsonProperty("missing_evidence")
    private String missingEvidence;

    @JsonProperty("requires_human_approval")
    private boolean requiresHumanApproval = true;

    @JsonProperty("analysis_source")
    private String analysisSource;

    public GeminiInvestigationResponse() {
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getPossibleCauses() {
        return possibleCauses;
    }

    public void setPossibleCauses(List<String> possibleCauses) {
        this.possibleCauses = possibleCauses;
    }

    public List<String> getSupportingEvidence() {
        return supportingEvidence;
    }

    public void setSupportingEvidence(List<String> supportingEvidence) {
        this.supportingEvidence = supportingEvidence;
    }

    public List<String> getReferencedRecordIds() {
        return referencedRecordIds;
    }

    public void setReferencedRecordIds(List<String> referencedRecordIds) {
        this.referencedRecordIds = referencedRecordIds;
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

    public String getAnalysisSource() {
        return analysisSource;
    }

    public void setAnalysisSource(String analysisSource) {
        this.analysisSource = analysisSource;
    }
}
