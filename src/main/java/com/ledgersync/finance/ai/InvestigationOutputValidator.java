package com.ledgersync.finance.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.model.ResolutionActionType;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class InvestigationOutputValidator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOutputValidator.class);
    private final ObjectMapper objectMapper;
    private static final Set<String> ALLOWED_ACTIONS = EnumSet.of(
            ResolutionActionType.APPROVE_ADJUSTMENT,
            ResolutionActionType.REJECT_INVESTIGATION,
            ResolutionActionType.REQUEST_EVIDENCE,
            ResolutionActionType.WRITE_OFF,
            ResolutionActionType.INITIATE_CHARGEBACK_DISPUTE,
            ResolutionActionType.RETRY_SETTLEMENT
    ).stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());

    public InvestigationOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static class ValidationResult {
        public boolean passed = true;
        public List<String> warnings = new ArrayList<>();
        public GeminiInvestigationResponse sanitizedResponse;
    }

    public ValidationResult validateAndSanitize(GeminiInvestigationResponse response,
                                                DiscrepancyEntity discrepancy,
                                                FinanceRecordEntity ledger,
                                                FinanceRecordEntity gateway,
                                                FinanceRecordEntity bank) {
        ValidationResult result = new ValidationResult();
        result.sanitizedResponse = response;

        // 1. Validate recommended action
        if (response.getRecommendedAction() == null || !ALLOWED_ACTIONS.contains(response.getRecommendedAction())) {
            result.passed = false;
            result.warnings.add("Invalid or unsafe recommended action: " + response.getRecommendedAction() +
                    ". Defaulting to REQUEST_EVIDENCE");
            response.setRecommendedAction(ResolutionActionType.REQUEST_EVIDENCE.name());
        }

        // 2. Only records linked to this persisted discrepancy may be cited.
        Set<String> validIds = new java.util.LinkedHashSet<>();
        if (ledger != null) validIds.add(ledger.getId());
        if (gateway != null) validIds.add(gateway.getId());
        if (bank != null) validIds.add(bank.getId());
        if (response.getReferencedRecordIds() != null) {
            for (String recId : response.getReferencedRecordIds()) {
                if (recId != null && !validIds.contains(recId)) {
                    result.warnings.add("Pruned hallucinated record ID: " + recId);
                }
            }
        }
        List<String> linkedRecordIds = new ArrayList<>(validIds);
        if (!Objects.equals(response.getReferencedRecordIds(), linkedRecordIds)) {
            result.passed = false;
            result.warnings.add("Restricted cited records to the persisted discrepancy links.");
        }
        response.setReferencedRecordIds(linkedRecordIds);

        // Classification and financial claims are derived from persisted reconciliation evidence.
        if (!discrepancy.getType().name().equals(response.getClassification())) {
            result.passed = false;
            result.warnings.add("Replaced unsupported AI classification with persisted discrepancy type.");
        }
        response.setClassification(discrepancy.getType().name());
        String persistedRootCause = readPersistedRootCause(discrepancy, result);
        if (!Objects.equals(response.getSummary(), persistedRootCause)) {
            result.passed = false;
            result.warnings.add("Replaced unsupported AI summary with the persisted root-cause explanation.");
        }
        response.setSummary(persistedRootCause);
        List<String> persistedEvidence = buildPersistedEvidence(discrepancy, ledger, gateway, bank);
        if (!Objects.equals(response.getSupportingEvidence(), persistedEvidence)) {
            result.passed = false;
            result.warnings.add("Replaced unsupported citations with persisted source-record evidence.");
        }
        response.setSupportingEvidence(persistedEvidence);
        List<String> persistedCauses = List.of(persistedRootCause);
        if (!Objects.equals(response.getPossibleCauses(), persistedCauses)) {
            result.passed = false;
            result.warnings.add("Replaced unverified possible causes with the persisted root-cause explanation.");
        }
        response.setPossibleCauses(persistedCauses);
        String persistedMissingEvidence = buildMissingSourceEvidence(ledger, gateway, bank);
        if (!Objects.equals(response.getMissingEvidence(), persistedMissingEvidence)) {
            result.passed = false;
            result.warnings.add("Replaced unsupported missing-evidence claims with linked-record availability.");
        }
        response.setMissingEvidence(persistedMissingEvidence);
        String advisoryReasoning = "This is an advisory recommendation only. No financial record or settlement was changed. " +
                "A human reviewer must verify the persisted source records before taking action.";
        if (!Objects.equals(response.getActionReasoning(), advisoryReasoning)) {
            result.passed = false;
            result.warnings.add("Replaced action reasoning with a human-review-only advisory.");
        }
        response.setActionReasoning(advisoryReasoning);

        // Confidence has no calibrated ground-truth meaning in this project.
        if (response.getConfidenceScore() != null) {
            result.passed = false;
            result.warnings.add("Ignored uncalibrated AI confidence score.");
        }
        response.setConfidenceScore(null);

        // 3. Force human approval requirement
        if (!response.isRequiresHumanApproval()) {
            result.passed = false;
            result.warnings.add("Forced human approval for all recommendations.");
        }
        response.setRequiresHumanApproval(true);

        if (!result.warnings.isEmpty()) {
            log.warn("Validation completed with warnings for discrepancy {}: {}",
                    discrepancy.getId(), String.join("; ", result.warnings));
        }

        return result;
    }

    private String readPersistedRootCause(DiscrepancyEntity discrepancy, ValidationResult result) {
        try {
            JsonNode root = objectMapper.readTree(discrepancy.getEvidence());
            String rootCause = root.path("root_cause_explanation").asText();
            if (!rootCause.isBlank()) {
                return rootCause;
            }
        } catch (JsonProcessingException e) {
            result.warnings.add("Persisted evidence is not structured JSON; no root cause can be asserted.");
        }
        result.warnings.add("No structured persisted root-cause explanation is available.");
        return "Root cause is not established by the persisted evidence; human review is required.";
    }

    private String buildMissingSourceEvidence(FinanceRecordEntity ledger,
                                             FinanceRecordEntity gateway,
                                             FinanceRecordEntity bank) {
        List<String> missingSources = new ArrayList<>();
        if (ledger == null) missingSources.add("LEDGER");
        if (gateway == null) missingSources.add("RAZORPAY");
        if (bank == null) missingSources.add("BANK");
        if (!missingSources.isEmpty()) {
            return "Missing linked source record(s): " + String.join(", ", missingSources) + ".";
        }
        return "All linked source records are present; additional external evidence is not available in this workspace.";
    }

    private List<String> buildPersistedEvidence(DiscrepancyEntity discrepancy,
                                                FinanceRecordEntity ledger,
                                                FinanceRecordEntity gateway,
                                                FinanceRecordEntity bank) {
        List<String> evidence = new ArrayList<>();
        evidence.add("Persisted discrepancy " + discrepancy.getId() + ": type=" + discrepancy.getType() +
                ", amount_minor=" + discrepancy.getAmountMinor() +
                ", expected_minor=" + discrepancy.getExpectedAmountMinor() +
                ", actual_minor=" + discrepancy.getActualAmountMinor());
        addRecordEvidence(evidence, "LEDGER", ledger);
        addRecordEvidence(evidence, "RAZORPAY", gateway);
        addRecordEvidence(evidence, "BANK", bank);
        return evidence;
    }

    private void addRecordEvidence(List<String> evidence, String source, FinanceRecordEntity record) {
        if (record != null) {
            evidence.add(source + " record " + record.getId() + ": reference=" + record.getRecordReference() +
                    ", currency=" + record.getCurrency() +
                    ", amount_minor=" + record.getAmountMinor() +
                    ", fee_minor=" + record.getFeeMinor() +
                    ", tax_minor=" + record.getTaxMinor() +
                    ", net_minor=" + record.getNetAmountMinor());
        }
    }
}
