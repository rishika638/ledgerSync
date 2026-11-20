package com.ledgersync.finance.controller;

import com.ledgersync.finance.ai.GeminiClient;
import com.ledgersync.finance.repository.FinanceImportBatchRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.finance.repository.ReconciliationMatchRepository;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/settings")
@Tag(name = "Finance Settings", description = "Workspace configuration, security profile, and system status")
public class FinanceSettingsController {

    private final GeminiClient geminiClient;
    private final FinanceRecordRepository recordRepository;
    private final FinanceImportBatchRepository batchRepository;
    private final ReconciliationMatchRepository matchRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final ReconciliationRunRepository runRepository;

    @Value("${spring.application.name:ledgersync}")
    private String appName;

    public FinanceSettingsController(GeminiClient geminiClient,
                                     FinanceRecordRepository recordRepository,
                                     FinanceImportBatchRepository batchRepository,
                                     ReconciliationMatchRepository matchRepository,
                                     DiscrepancyRepository discrepancyRepository,
                                     ReconciliationRunRepository runRepository) {
        this.geminiClient = geminiClient;
        this.recordRepository = recordRepository;
        this.batchRepository = batchRepository;
        this.matchRepository = matchRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.runRepository = runRepository;
    }

    @GetMapping
    @Operation(summary = "Get System Settings and Health Status")
    public ResponseEntity<Map<String, Object>> getSettings(Authentication authentication) {
        Map<String, Object> resp = new LinkedHashMap<>();

        // Workspace & Identity
        Map<String, Object> workspace = new HashMap<>();
        workspace.put("id", "ws_default_fintech");
        workspace.put("name", "LedgerSync Finance Workspace");
        workspace.put("activeRole", authentication != null ? authentication.getAuthorities().toString() : "ROLE_OPERATOR");
        workspace.put("actor", authentication != null ? authentication.getName() : "finance-operator");
        resp.put("workspace", workspace);

        // Integrations (secrets NEVER exposed)
        Map<String, Object> integrations = new HashMap<>();
        integrations.put("geminiAi", Map.of(
                "status", geminiClient.isConfigured() ? "CONFIGURED_UNVERIFIED" : "FALLBACK_ENABLED",
                "configured", geminiClient.isConfigured(),
                "model", geminiClient.getModelName(),
                "safetyValidation", "ACTIVE"
        ));
        integrations.put("razorpay", Map.of(
                "status", "CSV_IMPORT_ONLY",
                "webhookEndpoint", "AVAILABLE"
        ));
        integrations.put("bankIntegration", Map.of(
                "status", "CSV_IMPORT_ONLY",
                "currency", "INR"
        ));
        resp.put("integrations", integrations);

        // Database statistics
        Map<String, Object> dbStats = new HashMap<>();
        dbStats.put("totalBatches", batchRepository.count());
        dbStats.put("totalRecords", recordRepository.count());
        dbStats.put("totalMatches", matchRepository.count());
        dbStats.put("totalDiscrepancies", discrepancyRepository.count());
        dbStats.put("totalRuns", runRepository.count());
        resp.put("databaseStats", dbStats);

        return ResponseEntity.ok(resp);
    }
}
