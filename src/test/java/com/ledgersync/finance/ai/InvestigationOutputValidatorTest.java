package com.ledgersync.finance.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvestigationOutputValidatorTest {

    @Test
    void replacesUnsupportedClaimsAndRejectsForceMatchRecommendations() {
        DiscrepancyEntity discrepancy = mock(DiscrepancyEntity.class);
        FinanceRecordEntity ledger = mock(FinanceRecordEntity.class);
        when(discrepancy.getId()).thenReturn("disc-1");
        when(discrepancy.getType()).thenReturn(DiscrepancyType.MISSING_BANK);
        when(discrepancy.getEvidence()).thenReturn("Persisted bank credit is missing.");
        when(discrepancy.getAmountMinor()).thenReturn(12345L);
        when(discrepancy.getExpectedAmountMinor()).thenReturn(12345L);
        when(discrepancy.getActualAmountMinor()).thenReturn(0L);
        when(ledger.getId()).thenReturn("ledger-1");
        when(ledger.getRecordReference()).thenReturn("PAY-1");
        when(ledger.getCurrency()).thenReturn("INR");
        when(ledger.getAmountMinor()).thenReturn(12345L);
        when(ledger.getFeeMinor()).thenReturn(0L);
        when(ledger.getTaxMinor()).thenReturn(0L);
        when(ledger.getNetAmountMinor()).thenReturn(12345L);

        GeminiInvestigationResponse response = new GeminiInvestigationResponse();
        response.setClassification("CLEAN");
        response.setConfidenceScore(99.0);
        response.setRecommendedAction("FORCE_MATCH");
        response.setSummary("Unsupported summary");
        response.setPossibleCauses(List.of("Invented accusation"));
        response.setActionReasoning("Force the match.");
        response.setMissingEvidence("Fabricated missing report");
        response.setReferencedRecordIds(List.of("ledger-1", "unrelated-record"));
        response.setSupportingEvidence(List.of("Unsupported amount is INR 9999"));
        response.setRequiresHumanApproval(false);

        InvestigationOutputValidator.ValidationResult result =
                new InvestigationOutputValidator(new ObjectMapper())
                        .validateAndSanitize(response, discrepancy, ledger, null, null);

        assertFalse(result.passed);
        assertEquals("MISSING_BANK", response.getClassification());
        assertEquals("REQUEST_EVIDENCE", response.getRecommendedAction());
        assertNull(response.getConfidenceScore());
        assertEquals("Root cause is not established by the persisted evidence; human review is required.",
                response.getSummary());
        assertEquals(List.of("ledger-1"), response.getReferencedRecordIds());
        assertTrue(response.getSupportingEvidence().get(0).contains("amount_minor=12345"));
        assertFalse(response.getSupportingEvidence().toString().contains("9999"));
        assertTrue(response.isRequiresHumanApproval());
        assertTrue(response.getActionReasoning().contains("No financial record"));
        assertEquals(List.of("Root cause is not established by the persisted evidence; human review is required."),
                response.getPossibleCauses());
        assertEquals("Missing linked source record(s): RAZORPAY, BANK.", response.getMissingEvidence());
    }

    @Test
    void derivesRootCauseOnlyFromStructuredPersistedEvidence() {
        DiscrepancyEntity discrepancy = mock(DiscrepancyEntity.class);
        when(discrepancy.getId()).thenReturn("disc-2");
        when(discrepancy.getType()).thenReturn(DiscrepancyType.MISSING_BANK);
        when(discrepancy.getEvidence()).thenReturn("{\"root_cause_explanation\":\"persisted explanation\"}");
        when(discrepancy.getAmountMinor()).thenReturn(100L);
        when(discrepancy.getExpectedAmountMinor()).thenReturn(100L);
        when(discrepancy.getActualAmountMinor()).thenReturn(0L);

        GeminiInvestigationResponse response = new GeminiInvestigationResponse();
        response.setPossibleCauses(List.of("Unverified model-generated claim"));

        new InvestigationOutputValidator(new ObjectMapper())
                .validateAndSanitize(response, discrepancy, null, null, null);

        assertEquals(List.of("persisted explanation"), response.getPossibleCauses());
        assertEquals("persisted explanation", response.getSummary());
        assertEquals("Missing linked source record(s): LEDGER, RAZORPAY, BANK.", response.getMissingEvidence());
    }
}
