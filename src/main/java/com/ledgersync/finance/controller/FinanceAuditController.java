package com.ledgersync.finance.controller;

import com.ledgersync.audit.model.AuditEventEntity;
import com.ledgersync.audit.repository.AuditEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/audit")
@Tag(name = "Finance Audit Trail", description = "Query application-recorded financial audit events")
public class FinanceAuditController {

    private final AuditEventRepository auditRepository;

    public FinanceAuditController(AuditEventRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping
    @Operation(summary = "List Audit Trail Events with Pagination")
    public ResponseEntity<Page<AuditEventEntity>> getAuditTrail(
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditEventEntity> page = (resourceType != null && !resourceType.isBlank())
                ? auditRepository.findByResourceTypeOrderByCreatedAtDesc(resourceType, pageable)
                : auditRepository.findAll(pageable);
        return ResponseEntity.ok(page);
    }
}
