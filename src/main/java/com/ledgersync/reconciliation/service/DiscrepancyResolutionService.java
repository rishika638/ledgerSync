package com.ledgersync.reconciliation.service;

import com.ledgersync.audit.service.AuditService;
import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DiscrepancyResolutionService {

    private static final Logger log = LoggerFactory.getLogger(DiscrepancyResolutionService.class);

    private final DiscrepancyRepository discrepancyRepository;
    private final AuditService auditService;

    public DiscrepancyResolutionService(
            DiscrepancyRepository discrepancyRepository,
            AuditService auditService) {
        this.discrepancyRepository = discrepancyRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DiscrepancyEntity resolve(String discrepancyId, DiscrepancyStatus targetStatus, String resolutionNotes, String operator) {
        if (targetStatus != DiscrepancyStatus.RESOLVED && targetStatus != DiscrepancyStatus.IGNORED && targetStatus != DiscrepancyStatus.INVESTIGATING) {
            throw new IllegalArgumentException("Invalid target status for discrepancy resolution: " + targetStatus);
        }

        DiscrepancyEntity discrepancy = discrepancyRepository.findById(discrepancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + discrepancyId));

        String beforeState = "status=" + discrepancy.getStatus() + ",resolvedBy=" + discrepancy.getResolvedBy();

        discrepancy.setStatus(targetStatus);
        discrepancy.setResolutionNotes(resolutionNotes);
        discrepancy.setResolvedBy(operator);
        discrepancy.setResolvedAt(Instant.now());
        discrepancy.setUpdatedAt(Instant.now());

        DiscrepancyEntity saved = discrepancyRepository.saveAndFlush(discrepancy);

        String afterState = "status=" + saved.getStatus() + ",resolvedBy=" + saved.getResolvedBy() + ",notes=" + resolutionNotes;

        // Record resolution in tamper-evident append-only audit trail
        auditService.record(
                "RESOLVE_DISCREPANCY",
                operator,
                "DISCREPANCY",
                discrepancy.getId(),
                beforeState,
                afterState
        );

        log.info("Discrepancy {} resolved to {} by {} with note: {}",
                discrepancyId, targetStatus, operator, resolutionNotes);

        return saved;
    }
}
