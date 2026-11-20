package com.ledgersync.finance.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiClientFallbackTest {

    @Test
    void identifiesDeterministicFallbackWhenNoApiKeyIsConfigured() {
        GeminiClient client = new GeminiClient("", "gemini-1.5-flash", new ObjectMapper());
        DiscrepancyEntity discrepancy = mock(DiscrepancyEntity.class);
        when(discrepancy.getType()).thenReturn(DiscrepancyType.MISSING_GATEWAY);

        GeminiInvestigationResponse response = client.investigate(
                "ignored by local fallback", discrepancy, null, null, null);

        assertEquals("RULE_BASED_FALLBACK", response.getAnalysisSource());
        assertEquals("MISSING_GATEWAY", response.getClassification());
        assertNull(response.getConfidenceScore());
        assertEquals(true, response.isRequiresHumanApproval());
    }
}
