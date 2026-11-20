package com.ledgersync.finance.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.audit.repository.AuditEventRepository;
import com.ledgersync.finance.ai.GeminiClient;
import com.ledgersync.finance.model.FinanceImportBatchEntity;
import com.ledgersync.finance.model.FinanceRecordStatus;
import com.ledgersync.finance.model.FinanceSourceType;
import com.ledgersync.finance.repository.FinanceImportBatchRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.finance.repository.ReconciliationMatchRepository;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/finance/dashboard")
@Tag(name = "Finance Dashboard", description = "Dashboard overview and high-level reconciliation metrics")
public class FinanceDashboardController {

    private static final String THREE_WAY_RUN_TYPE = "THREE_WAY_SETTLEMENT";

    private final FinanceRecordRepository recordRepository;
    private final FinanceImportBatchRepository batchRepository;
    private final ReconciliationMatchRepository matchRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final ReconciliationRunRepository runRepository;
    private final AuditEventRepository auditRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public FinanceDashboardController(FinanceRecordRepository recordRepository,
                                      FinanceImportBatchRepository batchRepository,
                                      ReconciliationMatchRepository matchRepository,
                                      DiscrepancyRepository discrepancyRepository,
                                      ReconciliationRunRepository runRepository,
                                      AuditEventRepository auditRepository,
                                      GeminiClient geminiClient,
                                      ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.batchRepository = batchRepository;
        this.matchRepository = matchRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.runRepository = runRepository;
        this.auditRepository = auditRepository;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get Consolidated Dashboard Metrics")
    public ResponseEntity<Map<String, Object>> getDashboardMetrics() {
        Map<String, Object> resp = new LinkedHashMap<>();

        Optional<ReconciliationRunEntity> latestRun =
                runRepository.findFirstByRunTypeOrderByStartedAtDesc(THREE_WAY_RUN_TYPE);
        long totalTransactions = latestRun.map(run -> (long) run.getTotalLedgerRecords())
                .orElseGet(() -> recordRepository.countBySourceType(FinanceSourceType.LEDGER));
        long matchedRecords = latestRun.map(ReconciliationRunEntity::getMatchedRecordsCount)
                .orElse(0);
        long unmatchedDiscrepancies = latestRun.map(run ->
                discrepancyRepository.countByRunIdAndStatus(run.getId(), DiscrepancyStatus.OPEN) +
                discrepancyRepository.countByRunIdAndStatus(run.getId(), DiscrepancyStatus.INVESTIGATING) +
                discrepancyRepository.countByRunIdAndStatus(run.getId(), DiscrepancyStatus.INVESTIGATED) +
                discrepancyRepository.countByRunIdAndStatus(run.getId(), DiscrepancyStatus.AWAITING_APPROVAL)
        ).orElse(0L);

        long pendingSettlements = latestRun.map(run -> getGatewayRecordsForRun(run).stream()
                .mapToLong(batch -> recordRepository.countByBatchAndStatus(batch, FinanceRecordStatus.UNRECONCILED) +
                        recordRepository.countByBatchAndStatus(batch, FinanceRecordStatus.PARTIALLY_MATCHED))
                .sum()).orElse(0L);

        long unmatchedExposureMinor = latestRun
                .map(run -> discrepancyRepository.sumUnresolvedDiscrepancyExposureForRun(run.getId()))
                .orElse(0L);

        resp.put("totalTransactions", totalTransactions);
        resp.put("matchedRecords", matchedRecords);
        resp.put("unmatchedRecords", unmatchedDiscrepancies);
        resp.put("totalExceptions", latestRun.map(run -> (long) run.getDiscrepanciesCount()).orElse(0L));
        resp.put("pendingSettlements", pendingSettlements);
        resp.put("unmatchedExposureMinor", unmatchedExposureMinor);
        resp.put("unmatchedExposureInr", BigDecimal.valueOf(unmatchedExposureMinor, 2).toPlainString());

        if (latestRun.isPresent()) {
            ReconciliationRunEntity run = latestRun.get();
            resp.put("matchRate", run.getMatchRate());
            resp.put("precisionRate", run.getPrecisionRate());
            resp.put("recallRate", run.getRecallRate());
            resp.put("latestRunId", run.getId());
            resp.put("lastRunTimestamp", run.getCompletedAt() != null ? run.getCompletedAt() : run.getStartedAt());
            resp.put("durationMs", run.getProcessingDurationMs());
        } else {
            resp.put("matchRate", 0.00);
            resp.put("precisionRate", null);
            resp.put("recallRate", null);
            resp.put("latestRunId", null);
            resp.put("lastRunTimestamp", null);
            resp.put("durationMs", 0);
        }

        // Exception distribution by type
        List<Object[]> typeCounts = latestRun
                .map(run -> discrepancyRepository.countExceptionsByTypeForRun(run.getId()))
                .orElseGet(List::of);
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] r : typeCounts) {
            byType.put(r[0].toString(), (Long) r[1]);
        }
        resp.put("exceptionDistributionByType", byType);

        // Exception distribution by severity
        List<Object[]> severityCounts = latestRun
                .map(run -> discrepancyRepository.countExceptionsBySeverityForRun(run.getId()))
                .orElseGet(List::of);
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (Object[] r : severityCounts) {
            bySeverity.put(r[0].toString(), (Long) r[1]);
        }
        resp.put("exceptionDistributionBySeverity", bySeverity);

        // Recent reconciliation runs for trend chart
        List<ReconciliationRunEntity> runs = runRepository.findTop10ByRunTypeOrderByStartedAtDesc(THREE_WAY_RUN_TYPE);
        List<Map<String, Object>> runTrends = new ArrayList<>();
        for (ReconciliationRunEntity r : runs) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("date", r.getStartedAt().toString());
            m.put("matchRate", r.getMatchRate());
            m.put("matchedCount", r.getMatchedRecordsCount());
            m.put("discrepanciesCount", r.getDiscrepanciesCount());
            m.put("durationMs", r.getProcessingDurationMs());
            runTrends.add(m);
        }
        resp.put("reconciliationTrends", runTrends);

        // Recent Audit Events
        var recentAudit = auditRepository.findAll(PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        resp.put("recentActivity", recentAudit);

        // Gemini AI Integration Status
        Map<String, Object> aiStatus = new HashMap<>();
        aiStatus.put("configured", geminiClient.isConfigured());
        aiStatus.put("model", geminiClient.getModelName());
        resp.put("geminiStatus", aiStatus);

        return ResponseEntity.ok(resp);
    }

    private List<FinanceImportBatchEntity> getGatewayRecordsForRun(ReconciliationRunEntity run) {
        try {
            List<String> batchIds = objectMapper.readValue(
                    run.getInputBatchIds(), new TypeReference<>() {});
            return batchIds.stream()
                    .map(batchRepository::findById)
                    .flatMap(Optional::stream)
                    .filter(batch -> batch.getSourceType() == FinanceSourceType.RAZORPAY)
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the reconciliation run's source batch snapshot", e);
        }
    }
}
