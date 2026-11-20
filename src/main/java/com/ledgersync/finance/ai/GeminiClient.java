package com.ledgersync.finance.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiClient(
            @Value("${ledgersync.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
            @Value("${ledgersync.gemini.model:gemini-1.5-flash}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("dummy-key");
    }

    public String getModelName() {
        return model;
    }

    public GeminiInvestigationResponse investigate(String promptText, DiscrepancyEntity discrepancy,
                                                   FinanceRecordEntity ledger,
                                                   FinanceRecordEntity gateway,
                                                   FinanceRecordEntity bank) {
        if (!isConfigured()) {
            log.info("Gemini API key not configured or empty. Using deterministic rule-based controller fallback.");
            return buildDeterministicFallback(discrepancy, ledger, gateway, bank);
        }

        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent", model);

        Map<String, Object> requestPayload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", promptText)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );

        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String jsonBody = objectMapper.writeValueAsString(requestPayload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey)
                        .timeout(Duration.ofSeconds(12))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        String rawContent = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                        // Strip any potential codeblock formatting
                        String cleanJson = rawContent.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "").trim();
                        GeminiInvestigationResponse investigationResponse =
                                objectMapper.readValue(cleanJson, GeminiInvestigationResponse.class);
                        investigationResponse.setAnalysisSource("GEMINI");
                        return investigationResponse;
                    }
                } else {
                    log.warn("Gemini API call failed with HTTP status {} on attempt {}",
                            response.statusCode(), attempt);
                }
            } catch (Exception e) {
                log.warn("Gemini API invocation attempt {} failed: {}", attempt, e.getMessage());
            }

            try {
                Thread.sleep(1000L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.warn("Gemini API retries exhausted. Activating deterministic controller fallback.");
        return buildDeterministicFallback(discrepancy, ledger, gateway, bank);
    }

    public String generateAssistantResponse(String systemPrompt, String userQuery) {
        if (!isConfigured()) {
            return null; // Signals controller to use grounded deterministic summary
        }

        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent", model);

        Map<String, Object> requestPayload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", systemPrompt + "\n\nUser Question: " + userQuery)))
                ),
                "generationConfig", Map.of("temperature", 0.2)
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(requestPayload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Assistant query Gemini invocation failed: {}", e.getMessage());
        }

        return null;
    }

    private GeminiInvestigationResponse buildDeterministicFallback(DiscrepancyEntity discrepancy,
                                                                   FinanceRecordEntity ledger,
                                                                   FinanceRecordEntity gateway,
                                                                   FinanceRecordEntity bank) {
        GeminiInvestigationResponse r = new GeminiInvestigationResponse();
        r.setAnalysisSource("RULE_BASED_FALLBACK");
        r.setClassification(discrepancy.getType().name());
        r.setRequiresHumanApproval(true);

        List<String> evidence = new ArrayList<>();
        List<String> recIds = new ArrayList<>();
        List<String> causes = new ArrayList<>();

        if (ledger != null) {
            recIds.add(ledger.getId());
            evidence.add("Merchant Ledger captured Order " + ledger.getOrderReference() + " for ₹" + formatAmount(ledger.getAmountMinor()));
        }
        if (gateway != null) {
            recIds.add(gateway.getId());
            evidence.add("Razorpay processed Payment " + gateway.getRecordReference() + " (Gross ₹" +
                    formatAmount(gateway.getAmountMinor()) + ", Net ₹" + formatAmount(gateway.getNetAmountMinor()) +
                    ", UTR: " + gateway.getSecondaryReference() + ")");
        }
        if (bank != null) {
            recIds.add(bank.getId());
            evidence.add("Bank statement recorded credit ₹" + formatAmount(bank.getAmountMinor()) + " (Txn: " + bank.getRecordReference() + ")");
        }

        r.setReferencedRecordIds(recIds);

        switch (discrepancy.getType()) {
            case MISSING_GATEWAY:
                r.setSummary("Payment record exists in Merchant Ledger but was never registered or settled by Razorpay gateway.");
                causes.add("Customer initiated checkout but abandoned before payment gateway authorization.");
                causes.add("Webhook delivery dropped or authorization timed out on payment aggregator.");
                r.setRecommendedAction("WRITE_OFF");
                r.setActionReasoning("Because no funds were settled by Razorpay or credited to the bank, the order should be cancelled or written off to prevent unbacked fulfillment.");
                r.setMissingEvidence("Gateway server logs or customer checkout session trace.");
                break;

            case MISSING_BANK:
                r.setSummary("Razorpay settlement confirmed net payout, but corresponding credit is completely missing from the bank statement.");
                causes.add("Bank transfer failed or was held in nodal clearing account due to NEFT/RTGS batch cutoff.");
                causes.add("Beneficiary account mismatch or bank reconciliation mismatch on statement generation date.");
                r.setRecommendedAction("INITIATE_CHARGEBACK_DISPUTE");
                r.setActionReasoning("Settlement funds of ₹" + formatAmount(discrepancy.getAmountMinor()) + " are overdue. Demand payment confirmation and UTR trace from Razorpay settlement operations.");
                r.setMissingEvidence("Bank clearing advice / UTR trace report from Razorpay nodal desk.");
                break;

            case AMOUNT_MISMATCH:
                r.setSummary("The gross amount recorded in the merchant ledger differs from the amount charged and settled by Razorpay.");
                causes.add("Merchant cart item was updated or discounted after initial payment authorization.");
                causes.add("Post-checkout coupon or surcharge applied directly at gateway checkout.");
                r.setRecommendedAction("APPROVE_ADJUSTMENT");
                r.setActionReasoning("Post an adjusting journal entry of ₹" + formatAmount(discrepancy.getAmountMinor()) + " to align merchant gross receipts with gateway captured settlement.");
                r.setMissingEvidence("Merchant shopping cart audit log.");
                break;

            case DUPLICATE_RECORD:
                r.setSummary("Duplicate settlement capture records detected for the same order reference in Razorpay settlement report.");
                causes.add("Retry mechanism on gateway webhook or double capture trigger on customer double-click.");
                causes.add("Settlement batch reprocessing on payment processor side.");
                r.setRecommendedAction("RETRY_SETTLEMENT");
                r.setActionReasoning("Flag duplicate settlement instance and reverse duplicate ledger credit to avoid overstating merchant revenue.");
                r.setMissingEvidence("Payment gateway idempotent request log.");
                break;

            case FEE_MISMATCH:
            case TAX_MISMATCH:
                r.setSummary("Deducted MDR fee and GST exceed contractual schedule of 2.0% gross + 18% GST.");
                causes.add("Gateway applied standard international card rate or premium payment instrument pricing tier.");
                causes.add("Incorrect pricing slab mapped on payment aggregator merchant profile.");
                r.setRecommendedAction("APPROVE_ADJUSTMENT");
                r.setActionReasoning("Raise fee discrepancy ticket with Razorpay account manager to claim MDR refund for ₹" + formatAmount(discrepancy.getAmountMinor()) + ".");
                r.setMissingEvidence("Merchant fee schedule annexure.");
                break;

            case PARTIAL_SETTLEMENT:
                r.setSummary("Razorpay settled only a partial tranche of the order gross amount.");
                causes.add("Rolling reserve hold applied by gateway risk management.");
                causes.add("Multi-item order settled across separate delivery tranches.");
                r.setRecommendedAction("APPROVE_ADJUSTMENT");
                r.setActionReasoning("Record partial settlement in ledger and create pending settlement receivable for remaining ₹" + formatAmount(discrepancy.getAmountMinor()) + ".");
                r.setMissingEvidence("Razorpay rolling reserve report.");
                break;

            case DELAYED_SETTLEMENT:
                r.setSummary("Funds arrived successfully, but settlement duration exceeded contractual T+2 SLA window.");
                causes.add("Settlement delay caused by banking holidays or gateway compliance review.");
                r.setRecommendedAction("REQUEST_EVIDENCE");
                r.setActionReasoning("Confirm settlement timing against the applicable SLA and retain the bank credit evidence.");
                r.setMissingEvidence("None - funds verified.");
                break;

            case REFERENCE_MISMATCH:
                r.setSummary("Bank credit amount exactly matches net settlement, but bank narration omits order reference.");
                causes.add("Bank truncated narration string or bulk lumped transaction reference.");
                r.setRecommendedAction("REQUEST_EVIDENCE");
                r.setActionReasoning("Request the detailed bank statement reference before linking this credit to an order.");
                r.setMissingEvidence("Detailed bank CMS transaction export.");
                break;

            case AMBIGUOUS_MATCH:
                r.setSummary("Multiple transactions share identical amount and timestamp with generic narration. Engine refused to force-match.");
                causes.add("High-frequency micro-transactions with non-unique customer UPI narration.");
                r.setRecommendedAction("REQUEST_EVIDENCE");
                r.setActionReasoning("Require customer UPI transaction ID or merchant order invoice before confirming match.");
                r.setMissingEvidence("Customer UPI payment reference screenshot or merchant invoice copy.");
                break;

            default:
                r.setSummary("Discrepancy detected during financial reconciliation.");
                causes.add("Asynchronous processing delay or reference mismatch.");
                r.setRecommendedAction("APPROVE_ADJUSTMENT");
                r.setActionReasoning("Review transaction details and apply recommended adjustment.");
                r.setMissingEvidence("None");
                break;
        }

        r.setPossibleCauses(causes);
        r.setSupportingEvidence(evidence);
        return r;
    }

    private String formatAmount(long minorUnits) {
        return java.math.BigDecimal.valueOf(minorUnits, 2).toPlainString();
    }
}
