package com.ledgersync.finance.controller;

import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.finance.model.MatchType;
import com.ledgersync.finance.model.ReconciliationMatchEntity;
import com.ledgersync.finance.repository.ReconciliationMatchRepository;
import com.ledgersync.finance.service.ThreeWayReconciliationService;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/finance/reconciliation")
@Tag(name = "Finance Reconciliation", description = "Execute deterministic three-way reconciliation runs and inspect matches")
public class FinanceReconciliationController {

    private static final String THREE_WAY_RUN_TYPE = "THREE_WAY_SETTLEMENT";

    private final ThreeWayReconciliationService reconciliationService;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationMatchRepository matchRepository;

    public FinanceReconciliationController(ThreeWayReconciliationService reconciliationService,
                                           ReconciliationRunRepository runRepository,
                                           ReconciliationMatchRepository matchRepository) {
        this.reconciliationService = reconciliationService;
        this.runRepository = runRepository;
        this.matchRepository = matchRepository;
    }

    @PostMapping("/run")
    @Operation(summary = "Start Deterministic 3-Way Reconciliation Run")
    public ResponseEntity<ReconciliationRunEntity> startReconciliation() {
        ReconciliationRunEntity run = reconciliationService.executeThreeWayReconciliation();
        return ResponseEntity.ok(run);
    }

    @GetMapping("/runs")
    @Operation(summary = "List Past Reconciliation Runs with Pagination")
    public ResponseEntity<Page<ReconciliationRunEntity>> listRuns(
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(runRepository.findByRunTypeOrderByStartedAtDesc(THREE_WAY_RUN_TYPE, pageable));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Get Reconciliation Run Details")
    public ResponseEntity<ReconciliationRunEntity> getRun(@PathVariable("id") String id) {
        ReconciliationRunEntity run = runRepository.findByIdAndRunType(id, THREE_WAY_RUN_TYPE)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + id));
        return ResponseEntity.ok(run);
    }

    @GetMapping("/runs/{id}/matches")
    @Operation(summary = "Get Matched Records for Run with Search & Filter")
    public ResponseEntity<Page<ReconciliationMatchEntity>> getRunMatches(
            @PathVariable("id") String id,
            @RequestParam(value = "matchType", required = false) MatchType matchType,
            @RequestParam(value = "query", required = false) String query,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        requireThreeWayRun(id);
        return ResponseEntity.ok(matchRepository.searchMatches(id, matchType, query, pageable));
    }

    @GetMapping("/runs/{id}/export")
    @Operation(summary = "Export Run Matches as CSV")
    public ResponseEntity<String> exportRunMatches(@PathVariable("id") String id) {
        ReconciliationRunEntity run = requireThreeWayRun(id);

        List<ReconciliationMatchEntity> matches = matchRepository.findByRunId(id);
        StringBuilder sb = new StringBuilder();
        sb.append("match_id,run_id,match_type,match_status,ledger_record,gateway_record,bank_record,ledger_amount,gateway_amount,bank_amount,fee,tax,confidence_score,notes\n");

        for (ReconciliationMatchEntity m : matches) {
            String ledgerRef = m.getLedgerRecord() != null ? m.getLedgerRecord().getRecordReference() : "";
            String gatewayRef = m.getGatewayRecord() != null ? m.getGatewayRecord().getRecordReference() : "";
            String bankRef = m.getBankRecord() != null ? m.getBankRecord().getRecordReference() : "";
            String ledgerAmount = formatMinor(m.getLedgerAmountMinor());
            String gatewayAmount = formatMinor(m.getGatewayAmountMinor());
            String bankAmount = formatMinor(m.getBankAmountMinor());
            String fee = formatMinor(m.getFeeMinor());
            String tax = formatMinor(m.getTaxMinor());

            sb.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,\"%s\"\n",
                    m.getId(), m.getRunId(), m.getMatchType(), m.getMatchStatus(),
                    ledgerRef, gatewayRef, bankRef,
                    ledgerAmount, gatewayAmount, bankAmount, fee, tax,
                    m.getConfidenceScore(),
                    m.getMatchNotes() != null ? m.getMatchNotes().replace("\"", "'") : ""));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reconciliation_run_" + id + "_matches.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString());
    }

    private String formatMinor(Long minorUnits) {
        return BigDecimal.valueOf(minorUnits == null ? 0 : minorUnits, 2).toPlainString();
    }

    private ReconciliationRunEntity requireThreeWayRun(String id) {
        return runRepository.findByIdAndRunType(id, THREE_WAY_RUN_TYPE)
                .orElseThrow(() -> new ResourceNotFoundException("Three-way run not found: " + id));
    }
}
