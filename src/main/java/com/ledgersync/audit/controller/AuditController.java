package com.ledgersync.audit.controller;

import com.ledgersync.audit.model.AuditEventEntity;
import com.ledgersync.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit Log", description = "Query append-only application audit history and correlation traces")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/resources/{type}/{id}")
    @Operation(summary = "Get Audit Trail for a Given Resource")
    public ResponseEntity<Page<AuditEventEntity>> getResourceAuditHistory(
            @Parameter(description = "Resource Type (e.g. PAYMENT, DISCREPANCY, INBOX_MESSAGE)")
            @PathVariable("type") String type,
            @Parameter(description = "Resource ID")
            @PathVariable("id") String id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditEventEntity> history = auditService.getHistory(type.toUpperCase(), id, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/correlation/{correlationId}")
    @Operation(summary = "Get All Audit Events for a Correlation ID")
    public ResponseEntity<List<AuditEventEntity>> getByCorrelationId(
            @PathVariable("correlationId") String correlationId) {
        List<AuditEventEntity> events = auditService.getByCorrelationId(correlationId);
        return ResponseEntity.ok(events);
    }
}
