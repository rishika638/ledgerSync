package com.ledgersync.audit.service;

import com.ledgersync.audit.model.AuditEventEntity;
import com.ledgersync.audit.repository.AuditEventRepository;
import com.ledgersync.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository auditRepository;

    public AuditService(AuditEventRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Records an audit record. Participates in caller's transaction so financial change
     * and audit trail are atomically committed or rolled back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEventEntity record(String action, String actor, String resourceType,
                                   String resourceId, String beforeState, String afterState) {
        String correlationId = MDC.get("correlationId");

        AuditEventEntity event = new AuditEventEntity(
                IdGenerator.auditId(),
                action,
                actor,
                resourceType,
                resourceId,
                correlationId,
                beforeState,
                afterState,
                null,
                Instant.now()
        );

        AuditEventEntity saved = auditRepository.save(event);
        log.info("Audit recorded: action={}, resourceType={}, resourceId={}, correlationId={}",
                action, resourceType, resourceId, correlationId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> getHistory(String resourceType, String resourceId, Pageable pageable) {
        return auditRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditEventEntity> getByCorrelationId(String correlationId) {
        return auditRepository.findByCorrelationId(correlationId);
    }
}
