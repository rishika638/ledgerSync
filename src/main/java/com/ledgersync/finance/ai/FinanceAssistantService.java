package com.ledgersync.finance.ai;

import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class FinanceAssistantService {

    private static final String THREE_WAY_RUN_TYPE = "THREE_WAY_SETTLEMENT";

    private final DiscrepancyRepository discrepancyRepository;
    private final ReconciliationRunRepository runRepository;
    private final GeminiClient geminiClient;

    public FinanceAssistantService(DiscrepancyRepository discrepancyRepository,
                                  ReconciliationRunRepository runRepository,
                                  GeminiClient geminiClient) {
        this.discrepancyRepository = discrepancyRepository;
        this.runRepository = runRepository;
        this.geminiClient = geminiClient;
    }

    public static class AssistantReply {
        public String answer;
        public List<String> evidenceCitations = new ArrayList<>();
        public List<String> suggestedQuestions = new ArrayList<>();
        public Map<String, Object> groundTruthMetrics = new HashMap<>();
    }

    public AssistantReply ask(String userQuery) {
        AssistantReply reply = new AssistantReply();

        // 1. Gather factual grounded data via read-only repository queries
        Optional<ReconciliationRunEntity> latestRun =
                runRepository.findFirstByRunTypeOrderByStartedAtDesc(THREE_WAY_RUN_TYPE);
        long totalDiscrepancies = latestRun.map(run -> discrepancyRepository.countByRunId(run.getId())).orElse(0L);
        long activeDiscrepancies = latestRun.map(run ->
                discrepancyRepository.countByRunIdAndStatus(run.getId(), DiscrepancyStatus.OPEN) +
                discrepancyRepository.countByRunIdAndStatus(run.getId(), DiscrepancyStatus.INVESTIGATING) +
                discrepancyRepository.countByRunIdAndStatus(run.getId(), DiscrepancyStatus.INVESTIGATED) +
                discrepancyRepository.countByRunIdAndStatus(run.getId(), DiscrepancyStatus.AWAITING_APPROVAL)
        ).orElse(0L);
        long unresolvedExposureMinor = latestRun
                .map(run -> discrepancyRepository.sumUnresolvedDiscrepancyExposureForRun(run.getId()))
                .orElse(0L);
        List<Object[]> typeCounts = latestRun
                .map(run -> discrepancyRepository.countExceptionsByTypeForRun(run.getId()))
                .orElseGet(List::of);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_discrepancies", totalDiscrepancies);
        metrics.put("active_exceptions", activeDiscrepancies);
        metrics.put("unresolved_exposure_inr", formatMinor(unresolvedExposureMinor));
        metrics.put("unresolved_exposure_paise", unresolvedExposureMinor);

        Map<String, Long> exceptionBreakdown = new HashMap<>();
        for (Object[] row : typeCounts) {
            exceptionBreakdown.put(row[0].toString(), (Long) row[1]);
        }
        metrics.put("exception_types", exceptionBreakdown);

        if (latestRun.isPresent()) {
            ReconciliationRunEntity run = latestRun.get();
            metrics.put("latest_run_id", run.getId());
            metrics.put("match_rate_pct", run.getMatchRate());
            if (run.getPrecisionRate() != null) {
                metrics.put("precision_pct", run.getPrecisionRate());
            }
            if (run.getRecallRate() != null) {
                metrics.put("recall_pct", run.getRecallRate());
            }
            metrics.put("matched_count", run.getMatchedRecordsCount());
            metrics.put("duration_ms", run.getProcessingDurationMs());
        }

        reply.groundTruthMetrics = metrics;

        // Suggested follow-up questions
        reply.suggestedQuestions = List.of(
                "What is our total unmatched financial exposure right now?",
                "Which exception types represent the highest financial risk?",
                "How many Razorpay settlements are missing corresponding bank statement credits?",
                "What was the match rate and duration in the latest reconciliation run?",
                "Are there any delayed settlements violating our contractual T+2 SLA?"
        );

        // Grounded evidence citations
        reply.evidenceCitations.add(String.format("Latest three-way run: %d exceptions, %d still active.",
                totalDiscrepancies, activeDiscrepancies));
        reply.evidenceCitations.add("Unresolved Financial Exposure: ₹" + formatMinor(unresolvedExposureMinor) + ".");
        if (latestRun.isPresent()) {
            ReconciliationRunEntity run = latestRun.get();
            reply.evidenceCitations.add(String.format("Reconciliation Run %s: %s%% observed match coverage (%d matched records).",
                    run.getId(), run.getMatchRate(), run.getMatchedRecordsCount()));
        }

        // If Gemini is configured, generate natural language response grounded in this exact context
        String systemContext = "You are the AI Finance Controller Assistant for LedgerSync 2.0. " +
                "You answer questions ONLY using the following verified database facts:\n" +
                "- Unresolved Financial Exposure: ₹" + formatMinor(unresolvedExposureMinor) + "\n" +
                "- Latest Run Discrepancies: " + totalDiscrepancies + "\n" +
                "- Latest Run Active Exceptions: " + activeDiscrepancies + "\n" +
                "- Exception Breakdown: " + exceptionBreakdown + "\n" +
                (latestRun.isPresent() ? "- Latest Run: " + latestRun.get().getId() + ", Match Rate: " + latestRun.get().getMatchRate() + "%, Duration: " + latestRun.get().getProcessingDurationMs() + "ms\n" : "") +
                "Ground all answers strictly on these facts. State exact rupee amounts. Do not hallucinate external figures.";

        String aiAnswer = geminiClient.generateAssistantResponse(systemContext, userQuery);
        if (aiAnswer != null && !aiAnswer.isBlank()) {
            reply.answer = aiAnswer;
        } else {
            // High-quality deterministic response generator based on query intent
            reply.answer = buildDeterministicAnswer(userQuery.toLowerCase(), metrics, exceptionBreakdown, unresolvedExposureMinor);
        }

        return reply;
    }

    private String buildDeterministicAnswer(String query, Map<String, Object> metrics,
                                            Map<String, Long> exceptionBreakdown, long exposureMinor) {
        String exposureInr = formatMinor(exposureMinor);

        if (query.contains("exposure") || query.contains("unmatched amount") || query.contains("total amount")) {
            return String.format("Our current total unmatched financial exposure is **₹%s** across %s active exceptions. " +
                    "All exposure is strictly tracked in integer minor currency units to prevent rounding leakage.",
                    exposureInr, metrics.get("active_exceptions"));
        }

        if (query.contains("missing") || query.contains("bank") || query.contains("settlement")) {
            long missingBank = exceptionBreakdown.getOrDefault("MISSING_BANK", 0L);
            long missingGateway = exceptionBreakdown.getOrDefault("MISSING_GATEWAY", 0L);
            return String.format("Based on our latest reconciliation, we identified **%d missing bank statement credits** (where Razorpay confirmed payout but funds have not credited the account) " +
                    "and **%d missing gateway records** (where merchant ledger orders were never received by Razorpay). " +
                    "You can inspect each exception in the Investigation Workspace to review bank UTR and gateway traces.",
                    missingBank, missingGateway);
        }

        if (query.contains("performance") || query.contains("rate") || query.contains("match") || query.contains("precision")) {
            Object matchRate = metrics.getOrDefault("match_rate_pct", "0.00");
            Object duration = metrics.getOrDefault("duration_ms", "0");
            Object precision = metrics.get("precision_pct");
            String accuracyText = precision == null
                    ? "Precision and recall have not been evaluated because this run has no attached ground-truth labels."
                    : String.format("Measured precision is %s%%.", precision);
            return String.format("The latest three-way reconciliation recorded **%s%% observed match coverage**. %s " +
                    "The deterministic matching engine processed all available financial sources in **%s ms**.",
                    matchRate, accuracyText, duration);
        }

        if (query.contains("cause") || query.contains("exception") || query.contains("risk")) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("The latest reconciliation contains **%s exceptions**, of which **%s are active**:\n\n",
                    metrics.get("total_discrepancies"), metrics.get("active_exceptions")));
            for (Map.Entry<String, Long> entry : exceptionBreakdown.entrySet()) {
                sb.append(String.format("- **%s**: %d instances\n", entry.getKey(), entry.getValue()));
            }
            sb.append("\nReview the exception queue and its amount/severity fields to prioritize controller work.");
            return sb.toString();
        }

        return String.format("LedgerSync Controller Summary: Currently monitoring **₹%s** in unresolved financial exposure across **%s discrepancies**. " +
                "Latest reconciliation run achieved a **%s%% match rate**. Select any exception to review AI investigations and execute human-approved resolutions.",
                exposureInr, metrics.get("active_exceptions"), metrics.getOrDefault("match_rate_pct", "0.00"));
    }

    private String formatMinor(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, 2).toPlainString();
    }
}
