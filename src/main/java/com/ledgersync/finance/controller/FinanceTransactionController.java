package com.ledgersync.finance.controller;

import com.ledgersync.audit.repository.AuditEventRepository;
import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.model.FinanceRecordStatus;
import com.ledgersync.finance.model.FinanceSourceType;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.audit.model.AuditEventEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/finance/transactions")
@Tag(name = "Finance Transactions", description = "Explore multi-source financial transactions and detailed audit timelines")
public class FinanceTransactionController {

    private final FinanceRecordRepository recordRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final AuditEventRepository auditRepository;

    public FinanceTransactionController(FinanceRecordRepository recordRepository,
                                        DiscrepancyRepository discrepancyRepository,
                                        AuditEventRepository auditRepository) {
        this.recordRepository = recordRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.auditRepository = auditRepository;
    }

    @GetMapping
    @Operation(summary = "Search Financial Transactions with Filters")
    public ResponseEntity<Page<FinanceRecordEntity>> listTransactions(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "sourceType", required = false) FinanceSourceType sourceType,
            @RequestParam(value = "status", required = false) FinanceRecordStatus status,
            @PageableDefault(size = 25, sort = "transactionTimestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(recordRepository.searchRecords(query, sourceType, status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Transaction Details by ID")
    public ResponseEntity<FinanceRecordEntity> getTransaction(@PathVariable("id") String id) {
        FinanceRecordEntity rec = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction record not found: " + id));
        return ResponseEntity.ok(rec);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get Comprehensive Financial Transaction Timeline")
    public ResponseEntity<Map<String, Object>> getTransactionTimeline(@PathVariable("id") String id) {
        FinanceRecordEntity baseRec = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction record not found: " + id));

        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("primaryRecord", baseRec);

        // Find linked records sharing the same order reference or secondary reference
        String orderRef = baseRec.getOrderReference();
        List<FinanceRecordEntity> relatedRecords = new ArrayList<>();
        if (orderRef != null && !orderRef.isBlank()) {
            relatedRecords = recordRepository.findByOrderReference(orderRef);
        }

        timeline.put("relatedRecords", relatedRecords);

        List<String> relatedRecordIds = new ArrayList<>();
        relatedRecordIds.add(baseRec.getId());
        relatedRecordIds.addAll(relatedRecords.stream().map(FinanceRecordEntity::getId).toList());
        List<DiscrepancyEntity> discList = discrepancyRepository.findByFinanceRecordIds(
                relatedRecordIds.stream().distinct().toList());
        timeline.put("discrepancies", discList);

        List<AuditEventEntity> auditLogs = new ArrayList<>();
        for (DiscrepancyEntity discrepancy : discList) {
            auditLogs.addAll(auditRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
                    "DISCREPANCY", discrepancy.getId()));
        }
        auditLogs.sort(Comparator.comparing(AuditEventEntity::getCreatedAt).reversed());
        timeline.put("auditLogs", auditLogs);

        return ResponseEntity.ok(timeline);
    }
}
