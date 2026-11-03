package com.ledgersync.reconciliation.controller;

import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.reconciliation.dto.DiscrepancyResponse;
import com.ledgersync.reconciliation.dto.ReconciliationRunResponse;
import com.ledgersync.reconciliation.dto.ResolveDiscrepancyRequest;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.repository.ReconciliationRunRepository;
import com.ledgersync.reconciliation.service.DiscrepancyResolutionService;
import com.ledgersync.reconciliation.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Reconciliation", description = "Execute provider reconciliations, inspect discrepancies, and manage manual investigations")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final DiscrepancyResolutionService resolutionService;
    private final ReconciliationRunRepository runRepository;
    private final DiscrepancyRepository discrepancyRepository;

    public ReconciliationController(
            ReconciliationService reconciliationService,
            DiscrepancyResolutionService resolutionService,
            ReconciliationRunRepository runRepository,
            DiscrepancyRepository discrepancyRepository) {
        this.reconciliationService = reconciliationService;
        this.resolutionService = resolutionService;
        this.runRepository = runRepository;
        this.discrepancyRepository = discrepancyRepository;
    }

    @PostMapping("/run")
    @Operation(summary = "Trigger On-Demand Provider Reconciliation")
    public ResponseEntity<ReconciliationRunResponse> run(
            @Parameter(description = "Provider (STRIPE, RAZORPAY)")
            @RequestParam(name = "provider", defaultValue = "STRIPE") String providerStr) {
        PaymentProvider provider = PaymentProvider.valueOf(providerStr.toUpperCase());
        ReconciliationRunEntity run = reconciliationService.runReconciliation(provider);
        return ResponseEntity.ok(ReconciliationRunResponse.fromEntity(run));
    }

    @GetMapping("/runs")
    @Operation(summary = "List Past Reconciliation Runs with Pagination")
    public ResponseEntity<Page<ReconciliationRunResponse>> listRuns(
            @Parameter(description = "Filter by provider") @RequestParam(name = "provider", required = false) String providerStr,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ReconciliationRunEntity> page;
        if (providerStr != null && !providerStr.isBlank()) {
            page = runRepository.findByProvider(PaymentProvider.valueOf(providerStr.toUpperCase()), pageable);
        } else {
            page = runRepository.findAll(pageable);
        }
        return ResponseEntity.ok(page.map(ReconciliationRunResponse::fromEntity));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Get Reconciliation Run Details")
    public ResponseEntity<ReconciliationRunResponse> getRun(@PathVariable("id") String id) {
        ReconciliationRunEntity run = runRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found: " + id));
        return ResponseEntity.ok(ReconciliationRunResponse.fromEntity(run));
    }

    @GetMapping("/discrepancies")
    @Operation(summary = "List Discrepancies with Pagination and Status Filter")
    public ResponseEntity<Page<DiscrepancyResponse>> listDiscrepancies(
            @Parameter(description = "Filter by status (OPEN, INVESTIGATING, RESOLVED, IGNORED)")
            @RequestParam(name = "status", required = false) DiscrepancyStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<DiscrepancyEntity> page = status != null
                ? discrepancyRepository.findByStatus(status, pageable)
                : discrepancyRepository.findAll(pageable);
        return ResponseEntity.ok(page.map(DiscrepancyResponse::fromEntity));
    }

    @GetMapping("/discrepancies/{id}")
    @Operation(summary = "Get Discrepancy Details by ID")
    public ResponseEntity<DiscrepancyResponse> getDiscrepancy(@PathVariable("id") String id) {
        DiscrepancyEntity discrepancy = discrepancyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + id));
        return ResponseEntity.ok(DiscrepancyResponse.fromEntity(discrepancy));
    }

    @PostMapping("/discrepancies/{id}/resolve")
    @Operation(summary = "Resolve or Update Discrepancy Investigation Status (Admin only)")
    public ResponseEntity<DiscrepancyResponse> resolveDiscrepancy(
            @PathVariable("id") String id,
            @Valid @RequestBody ResolveDiscrepancyRequest request,
            Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "admin";
        DiscrepancyEntity resolved = resolutionService.resolve(id, request.status(), request.resolutionNotes(), operator);
        return ResponseEntity.ok(DiscrepancyResponse.fromEntity(resolved));
    }
}
