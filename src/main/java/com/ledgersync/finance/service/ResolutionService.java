package com.ledgersync.finance.service;

import com.ledgersync.audit.service.AuditService;
import com.ledgersync.common.error.ConflictOperationException;
import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.common.id.IdGenerator;
import com.ledgersync.finance.model.AiInvestigationEntity;
import com.ledgersync.finance.model.ResolutionActionEntity;
import com.ledgersync.finance.model.ResolutionActionStatus;
import com.ledgersync.finance.model.ResolutionActionType;
import com.ledgersync.finance.repository.AiInvestigationRepository;
import com.ledgersync.finance.repository.ResolutionActionRepository;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ResolutionService.class);

    private final DiscrepancyRepository discrepancyRepository;
    private final AiInvestigationRepository investigationRepository;
    private final ResolutionActionRepository actionRepository;
    private final AuditService auditService;

    public ResolutionService(DiscrepancyRepository discrepancyRepository,
                             AiInvestigationRepository investigationRepository,
                             ResolutionActionRepository actionRepository,
                             AuditService auditService) {
        this.discrepancyRepository = discrepancyRepository;
        this.investigationRepository = investigationRepository;
        this.actionRepository = actionRepository;
        this.auditService = auditService;
    }

    public static class ActionRequest {
        public ResolutionActionType actionType;
        public String notes;
        public Long financialAdjustmentMinor;
    }

    @Transactional
    public ResolutionActionEntity executeAction(String discrepancyId, ActionRequest request, String actor) {
        if (request == null) {
            throw new IllegalArgumentException("Action request is required.");
        }
        DiscrepancyEntity discrepancy = discrepancyRepository.findByIdForUpdate(discrepancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + discrepancyId));

        if (actor == null || actor.isBlank()) {
            actor = "finance-operator";
        }

        AiInvestigationEntity latestInvestigation = investigationRepository
                .findFirstByDiscrepancyOrderByCreatedAtDesc(discrepancy)
                .orElse(null);

        String beforeState = "status=" + discrepancy.getStatus() + ",resolvedBy=" + discrepancy.getResolvedBy();

        if (request.actionType == null) {
            throw new IllegalArgumentException("Action type is required.");
        }
        ResolutionActionType actionType = request.actionType;
        if (actionType == ResolutionActionType.FORCE_MATCH) {
            throw new ConflictOperationException("Force matching is disabled. Ambiguous records must remain unmatched until evidence is resolved.");
        }
        if (discrepancy.getStatus() == DiscrepancyStatus.RESOLVED ||
                discrepancy.getStatus() == DiscrepancyStatus.IGNORED) {
            throw new ConflictOperationException("This exception is already in a terminal state; no additional action was recorded.");
        }
        long adjustment = request.financialAdjustmentMinor != null ? request.financialAdjustmentMinor : discrepancy.getAmountMinor();

        ResolutionActionEntity action = new ResolutionActionEntity(
                IdGenerator.resolutionActionId(),
                discrepancy,
                latestInvestigation,
                actionType,
                ResolutionActionStatus.APPROVED,
                actor,
                request.notes != null ? request.notes : "Authorized by finance controller",
                adjustment
        );

        // Update discrepancy status based on action
        switch (actionType) {
            case APPROVE_ADJUSTMENT:
            case WRITE_OFF:
                discrepancy.setStatus(DiscrepancyStatus.RESOLVED);
                discrepancy.setResolvedBy(actor);
                discrepancy.setResolvedAt(Instant.now());
                discrepancy.setResolutionNotes("Resolved via " + actionType.name() + ": " + action.getDecisionNotes());
                break;

            case REJECT_INVESTIGATION:
                discrepancy.setStatus(DiscrepancyStatus.OPEN);
                discrepancy.setResolutionNotes("AI investigation rejected: " + action.getDecisionNotes());
                action.setStatus(ResolutionActionStatus.REJECTED);
                break;

            case REQUEST_EVIDENCE:
                discrepancy.setStatus(DiscrepancyStatus.INVESTIGATING);
                discrepancy.setResolutionNotes("Additional evidence requested: " + action.getDecisionNotes());
                action.setStatus(ResolutionActionStatus.PENDING);
                break;

            case INITIATE_CHARGEBACK_DISPUTE:
            case RETRY_SETTLEMENT:
                discrepancy.setStatus(DiscrepancyStatus.INVESTIGATING);
                discrepancy.setResolutionNotes("Workflow triggered: " + actionType.name() + " - " + action.getDecisionNotes());
                action.setStatus(ResolutionActionStatus.EXECUTED);
                break;
        }

        discrepancy.setUpdatedAt(Instant.now());
        discrepancyRepository.save(discrepancy);
        ResolutionActionEntity savedAction = actionRepository.save(action);

        String afterState = "status=" + discrepancy.getStatus() + ",action=" + actionType.name() + ",actor=" + actor;

        // Tamper-evident audit event record
        auditService.record(
                "RESOLUTION_ACTION_" + actionType.name(),
                actor,
                "DISCREPANCY",
                discrepancyId,
                beforeState,
                afterState
        );

        log.info("Action {} executed for discrepancy {} by actor {}: new status={}",
                actionType, discrepancyId, actor, discrepancy.getStatus());

        return savedAction;
    }

    public List<ResolutionActionEntity> getActionsForDiscrepancy(String discrepancyId) {
        DiscrepancyEntity d = discrepancyRepository.findById(discrepancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + discrepancyId));
        return actionRepository.findByDiscrepancyOrderByCreatedAtDesc(d);
    }
}
