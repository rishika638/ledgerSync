package com.ledgersync.finance.controller;

import com.ledgersync.audit.model.AuditEventEntity;
import com.ledgersync.audit.repository.AuditEventRepository;
import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.finance.ai.InvestigationService;
import com.ledgersync.finance.model.AiInvestigationEntity;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.model.ResolutionActionType;
import com.ledgersync.finance.model.ResolutionActionEntity;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.finance.service.ResolutionService;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancySeverity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.model.DiscrepancyType;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/finance/exceptions")
@Tag(name = "Finance Exceptions & AI Workspace", description = "Exception queue, Gemini AI investigations, and human-in-the-loop resolutions")
public class FinanceExceptionController {

    private static final String THREE_WAY_RUN_TYPE = "THREE_WAY_SETTLEMENT";

    private final DiscrepancyRepository discrepancyRepository;
    private final ReconciliationRunRepository runRepository;
    private final FinanceRecordRepository recordRepository;
    private final InvestigationService investigationService;
    private final ResolutionService resolutionService;
    private final AuditEventRepository auditRepository;

    public FinanceExceptionController(DiscrepancyRepository discrepancyRepository,
                                      ReconciliationRunRepository runRepository,
                                      FinanceRecordRepository recordRepository,
                                      InvestigationService investigationService,
                                      ResolutionService resolutionService,
                                      AuditEventRepository auditRepository) {
        this.discrepancyRepository = discrepancyRepository;
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
        this.investigationService = investigationService;
        this.resolutionService = resolutionService;
        this.auditRepository = auditRepository;
    }

    @GetMapping
    @Operation(summary = "List Exceptions with Multi-Criteria Filtering")
    public ResponseEntity<Page<DiscrepancyEntity>> listExceptions(
            @RequestParam(value = "status", required = false) DiscrepancyStatus status,
            @RequestParam(value = "severity", required = false) DiscrepancySeverity severity,
            @RequestParam(value = "type", required = false) DiscrepancyType type,
            @RequestParam(value = "runId", required = false) String runId,
            @RequestParam(value = "query", required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (runId == null) {
            runId = runRepository.findFirstByRunTypeOrderByStartedAtDesc(THREE_WAY_RUN_TYPE)
                    .map(run -> run.getId())
                    .orElse(null);
            if (runId == null) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
        } else if (runRepository.findByIdAndRunType(runId, THREE_WAY_RUN_TYPE).isEmpty()) {
            throw new ResourceNotFoundException("Three-way run not found: " + runId);
        }
        return ResponseEntity.ok(discrepancyRepository.searchDiscrepancies(status, severity, type, runId, query, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Investigation Workspace Data for Exception")
    public ResponseEntity<Map<String, Object>> getWorkspaceData(@PathVariable("id") String id) {
        DiscrepancyEntity discrepancy = discrepancyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + id));

        FinanceRecordEntity ledger = discrepancy.getLedgerRecordId() != null
                ? recordRepository.findById(discrepancy.getLedgerRecordId()).orElse(null)
                : null;
        FinanceRecordEntity gateway = discrepancy.getGatewayRecordId() != null
                ? recordRepository.findById(discrepancy.getGatewayRecordId()).orElse(null)
                : null;
        FinanceRecordEntity bank = discrepancy.getBankRecordId() != null
                ? recordRepository.findById(discrepancy.getBankRecordId()).orElse(null)
                : null;

        Optional<AiInvestigationEntity> latestInvestigation = investigationService.getLatestInvestigation(id);
        List<ResolutionActionEntity> actions = resolutionService.getActionsForDiscrepancy(id);
        List<AuditEventEntity> auditTrail = auditRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("DISCREPANCY", id);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("discrepancy", discrepancy);
        resp.put("ledgerRecord", ledger);
        resp.put("gatewayRecord", gateway);
        resp.put("bankRecord", bank);
        resp.put("investigation", latestInvestigation.orElse(null));
        resp.put("actions", actions);
        resp.put("auditTrail", auditTrail);

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/investigate")
    @Operation(summary = "Trigger Gemini AI Investigation on Discrepancy")
    public ResponseEntity<AiInvestigationEntity> investigate(
            @PathVariable("id") String id,
            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "finance-operator";
        AiInvestigationEntity investigation = investigationService.investigateDiscrepancy(id, actor);
        return ResponseEntity.ok(investigation);
    }

    @PostMapping("/{id}/actions")
    @Operation(summary = "Execute Human Resolution Action (Approve, Reject, Request Evidence)")
    public ResponseEntity<ResolutionActionEntity> executeAction(
            @PathVariable("id") String id,
            @RequestBody ResolutionService.ActionRequest request,
            Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Resolution action is required");
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if ((request.actionType == ResolutionActionType.FORCE_MATCH ||
             request.actionType == ResolutionActionType.WRITE_OFF) &&
                !isAdmin) {
            throw new AccessDeniedException("Force match and write-off actions require administrator role");
        }
        String actor = authentication != null ? authentication.getName() : "finance-operator";
        ResolutionActionEntity action = resolutionService.executeAction(id, request, actor);
        return ResponseEntity.ok(action);
    }
}
