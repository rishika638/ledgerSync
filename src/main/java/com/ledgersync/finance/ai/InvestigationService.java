package com.ledgersync.finance.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ledgersync.audit.service.AuditService;
import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.common.id.IdGenerator;
import com.ledgersync.finance.model.AiInvestigationEntity;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.repository.AiInvestigationRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final DiscrepancyRepository discrepancyRepository;
    private final FinanceRecordRepository recordRepository;
    private final AiInvestigationRepository investigationRepository;
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final InvestigationOutputValidator outputValidator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public InvestigationService(DiscrepancyRepository discrepancyRepository,
                                FinanceRecordRepository recordRepository,
                                AiInvestigationRepository investigationRepository,
                                PromptBuilder promptBuilder,
                                GeminiClient geminiClient,
                                InvestigationOutputValidator outputValidator,
                                AuditService auditService,
                                ObjectMapper objectMapper) {
        this.discrepancyRepository = discrepancyRepository;
        this.recordRepository = recordRepository;
        this.investigationRepository = investigationRepository;
        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
        this.outputValidator = outputValidator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiInvestigationEntity investigateDiscrepancy(String discrepancyId, String actor) {
        DiscrepancyEntity discrepancy = discrepancyRepository.findById(discrepancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + discrepancyId));

        log.info("Initiating Gemini AI investigation for discrepancy {} by actor {}", discrepancyId, actor);

        // Fetch referenced records
        FinanceRecordEntity ledger = discrepancy.getLedgerRecordId() != null
                ? recordRepository.findById(discrepancy.getLedgerRecordId()).orElse(null)
                : null;
        FinanceRecordEntity gateway = discrepancy.getGatewayRecordId() != null
                ? recordRepository.findById(discrepancy.getGatewayRecordId()).orElse(null)
                : null;
        FinanceRecordEntity bank = discrepancy.getBankRecordId() != null
                ? recordRepository.findById(discrepancy.getBankRecordId()).orElse(null)
                : null;

        String prompt = promptBuilder.buildInvestigationPrompt(discrepancy, ledger, gateway, bank);
        GeminiInvestigationResponse rawResponse = geminiClient.investigate(prompt, discrepancy, ledger, gateway, bank);

        InvestigationOutputValidator.ValidationResult valResult = outputValidator.validateAndSanitize(
                rawResponse, discrepancy, ledger, gateway, bank
        );
        GeminiInvestigationResponse finalResponse = valResult.sanitizedResponse;

        String invId = IdGenerator.investigationId();
        String rawResponseJson;
        String causesJson;
        String evidenceJson;
        try {
            rawResponseJson = objectMapper.writeValueAsString(finalResponse);
            causesJson = objectMapper.writeValueAsString(finalResponse.getPossibleCauses());
            evidenceJson = objectMapper.writeValueAsString(finalResponse.getSupportingEvidence());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize validated investigation evidence.", e);
        }

        AiInvestigationEntity investigation = new AiInvestigationEntity(
                invId,
                discrepancy,
                "RULE_BASED_FALLBACK".equals(finalResponse.getAnalysisSource())
                        ? "RULE_BASED_FALLBACK" : geminiClient.getModelName(),
                PromptBuilder.PROMPT_VERSION,
                finalResponse.getClassification() != null ? finalResponse.getClassification() : discrepancy.getType().name(),
                BigDecimal.valueOf(finalResponse.getConfidenceScore() == null
                        ? 0.0 : finalResponse.getConfidenceScore()),
                finalResponse.getSummary(),
                causesJson,
                evidenceJson,
                finalResponse.getRecommendedAction(),
                finalResponse.getActionReasoning() != null ? finalResponse.getActionReasoning() : "AI Controller reasoning grounded in financial invariants.",
                finalResponse.getMissingEvidence(),
                finalResponse.isRequiresHumanApproval(),
                valResult.passed,
                rawResponseJson
        );

        AiInvestigationEntity savedInvestigation = investigationRepository.saveAndFlush(investigation);

        // Update discrepancy state
        String beforeState = "status=" + discrepancy.getStatus();
        discrepancy.setStatus(DiscrepancyStatus.AWAITING_APPROVAL);
        discrepancy.setUpdatedAt(Instant.now());
        discrepancyRepository.save(discrepancy);
        String afterState = "status=" + discrepancy.getStatus() + ",investigationId=" + invId;

        // Record immutable audit event
        auditService.record(
                "AI_INVESTIGATION_COMPLETED",
                actor != null ? actor : "gemini-controller",
                "DISCREPANCY",
                discrepancyId,
                beforeState,
                afterState
        );

        log.info("AI investigation {} completed for discrepancy {}: source={}, action={}",
                invId, discrepancyId, finalResponse.getAnalysisSource(), savedInvestigation.getRecommendedAction());

        return savedInvestigation;
    }

    public Optional<AiInvestigationEntity> getLatestInvestigation(String discrepancyId) {
        DiscrepancyEntity d = discrepancyRepository.findById(discrepancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + discrepancyId));
        return investigationRepository.findFirstByDiscrepancyOrderByCreatedAtDesc(d);
    }
}
