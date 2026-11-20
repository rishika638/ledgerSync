package com.ledgersync.finance.controller;

import com.ledgersync.finance.ai.FinanceAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/assistant")
@Tag(name = "Finance AI Assistant", description = "Conversational financial queries grounded in actual database metrics")
public class FinanceAssistantController {

    private final FinanceAssistantService assistantService;

    public FinanceAssistantController(FinanceAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/query")
    @Operation(summary = "Ask Financial Question to AI Assistant")
    public ResponseEntity<FinanceAssistantService.AssistantReply> queryAssistant(
            @RequestBody Map<String, String> request) {
        String query = request.get("query");
        if (query == null || query.isBlank()) {
            query = "What is our current financial reconciliation status and unmatched exposure?";
        }
        return ResponseEntity.ok(assistantService.ask(query));
    }
}
