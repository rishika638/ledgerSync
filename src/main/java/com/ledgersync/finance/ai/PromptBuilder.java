package com.ledgersync.finance.ai;

import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class PromptBuilder {

    public static final String PROMPT_VERSION = "v2.0-fintech-safety";

    public String buildInvestigationPrompt(DiscrepancyEntity discrepancy,
                                           FinanceRecordEntity ledger,
                                           FinanceRecordEntity gateway,
                                           FinanceRecordEntity bank) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the AI Finance Controller for LedgerSync 2.0, an enterprise financial reconciliation platform.\n");
        sb.append("Your task is to analyze the following financial discrepancy between the Merchant Payment Ledger, Razorpay Settlement Records, and Bank Statements.\n\n");

        sb.append("=== DISCREPANCY CONTEXT ===\n");
        sb.append("Discrepancy ID: ").append(discrepancy.getId()).append("\n");
        sb.append("Exception Type: ").append(discrepancy.getType().name()).append("\n");
        sb.append("Severity: ").append(discrepancy.getSeverity().name()).append("\n");
        sb.append("Discrepancy Exposure Amount: ₹").append(formatMinor(discrepancy.getAmountMinor())).append(" (").append(discrepancy.getAmountMinor()).append(" paise)\n");
        sb.append("Raw Evidence Log: ").append(discrepancy.getEvidence()).append("\n\n");

        sb.append("=== FINANCIAL SOURCE EVIDENCE ===\n");

        if (ledger != null) {
            sb.append("1. MERCHANT LEDGER RECORD:\n");
            sb.append("   - Record ID: ").append(ledger.getId()).append("\n");
            sb.append("   - Ledger Reference: ").append(ledger.getRecordReference()).append("\n");
            sb.append("   - Order ID: ").append(sanitize(ledger.getOrderReference())).append("\n");
            sb.append("   - Gross Amount: ₹").append(formatMinor(ledger.getAmountMinor())).append("\n");
            sb.append("   - Timestamp: ").append(ledger.getTransactionTimestamp()).append("\n");
            sb.append("   - Description: ").append(sanitize(ledger.getRawMetadata())).append("\n");
        } else {
            sb.append("1. MERCHANT LEDGER RECORD: [NOT PRESENT / MISSING IN LOCAL LEDGER]\n");
        }

        if (gateway != null) {
            sb.append("2. RAZORPAY SETTLEMENT RECORD:\n");
            sb.append("   - Record ID: ").append(gateway.getId()).append("\n");
            sb.append("   - Gateway Payment ID: ").append(gateway.getRecordReference()).append("\n");
            sb.append("   - Order ID: ").append(sanitize(gateway.getOrderReference())).append("\n");
            sb.append("   - Gross Amount: ₹").append(formatMinor(gateway.getAmountMinor())).append("\n");
            sb.append("   - Fee Deducted: ₹").append(formatMinor(gateway.getFeeMinor())).append("\n");
            sb.append("   - Tax Deducted: ₹").append(formatMinor(gateway.getTaxMinor())).append("\n");
            sb.append("   - Net Amount: ₹").append(formatMinor(gateway.getNetAmountMinor())).append("\n");
            sb.append("   - Settlement UTR/ARN: ").append(sanitize(gateway.getSecondaryReference())).append("\n");
            sb.append("   - Settled Timestamp: ").append(gateway.getSettlementTimestamp()).append("\n");

            if (ledger != null && gateway.getSettlementTimestamp() != null && ledger.getTransactionTimestamp() != null) {
                Duration d = Duration.between(ledger.getTransactionTimestamp(), gateway.getSettlementTimestamp());
                sb.append("   - Settlement Latency: ").append(d.toHours()).append(" hours (").append(d.toDays()).append(" days)\n");
            }
        } else {
            sb.append("2. RAZORPAY SETTLEMENT RECORD: [NOT PRESENT / MISSING IN GATEWAY]\n");
        }

        if (bank != null) {
            sb.append("3. BANK STATEMENT RECORD:\n");
            sb.append("   - Record ID: ").append(bank.getId()).append("\n");
            sb.append("   - Bank Transaction ID: ").append(bank.getRecordReference()).append("\n");
            sb.append("   - Credit Amount: ₹").append(formatMinor(bank.getAmountMinor())).append("\n");
            sb.append("   - Value Date: ").append(bank.getTransactionTimestamp()).append("\n");
            sb.append("   - Reference UTR: ").append(sanitize(bank.getSecondaryReference())).append("\n");
            sb.append("   - Statement Narration: ").append(sanitize(bank.getRawMetadata())).append("\n");
        } else {
            sb.append("3. BANK STATEMENT RECORD: [NOT PRESENT / NO CORRESPONDING BANK CREDIT]\n");
        }

        sb.append("\n=== BUSINESS RULES & INVARIANTS ===\n");
        sb.append("- Contracted Fee: 2.0% of Gross + 18% GST on Fee.\n");
        sb.append("- Settlement Invariant: Net Settlement = Gross Amount - Fee - Tax + Adjustments.\n");
        sb.append("- Settlement SLA: T+2 days (72 hours).\n");
        sb.append("- Never recommend FORCE_MATCH. Ambiguous or weakly referenced records must remain unresolved pending evidence.\n");
        sb.append("- Recommendations are advisory only; only a human may approve an action, and this system does not execute financial changes.\n\n");

        sb.append("=== OUTPUT SPECIFICATION ===\n");
        sb.append("Respond ONLY with a valid JSON object without markdown code blocks, conforming to:\n");
        sb.append("{\n");
        sb.append("  \"classification\": \"<string, e.g. MISSING_GATEWAY | MISSING_BANK | AMOUNT_MISMATCH | FEE_MISMATCH | PARTIAL_SETTLEMENT | DELAYED_SETTLEMENT | AMBIGUOUS_MATCH>\",\n");
        sb.append("  \"summary\": \"<concise 2-3 sentence financial explanation of the root cause>\",\n");
        sb.append("  \"possible_causes\": [\"<cause 1>\", \"<cause 2>\"],\n");
        sb.append("  \"supporting_evidence\": [\"<fact 1 with exact numbers>\", \"<fact 2>\"],\n");
        sb.append("  \"referenced_record_ids\": [\"<only actual record IDs mentioned above>\"],\n");
        sb.append("  \"recommended_action\": \"<one of APPROVE_ADJUSTMENT | REJECT_INVESTIGATION | REQUEST_EVIDENCE | WRITE_OFF | INITIATE_CHARGEBACK_DISPUTE | RETRY_SETTLEMENT>\",\n");
        sb.append("  \"action_reasoning\": \"<detailed justification for the controller recommendation>\",\n");
        sb.append("  \"missing_evidence\": \"<any additional documentation needed, or 'None'>\",\n");
        sb.append("  \"requires_human_approval\": true\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String sanitize(String input) {
        if (input == null) return "";
        // Mask account numbers and strip special delimiters
        String masked = input.replaceAll("\\b\\d{9,16}\\b", "XXXXXX9821");
        return masked.replace("\"", "'").replace("\n", " ").trim();
    }

    private String formatMinor(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, 2).toPlainString();
    }
}
