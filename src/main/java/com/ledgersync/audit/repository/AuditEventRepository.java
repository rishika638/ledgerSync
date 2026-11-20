package com.ledgersync.audit.repository;

import com.ledgersync.audit.model.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {

    Page<AuditEventEntity> findByResourceTypeAndResourceId(String resourceType, String resourceId, Pageable pageable);

    List<AuditEventEntity> findByCorrelationId(String correlationId);

    Page<AuditEventEntity> findByResourceTypeOrderByCreatedAtDesc(String resourceType, Pageable pageable);

    List<AuditEventEntity> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, String resourceId);

    Page<AuditEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
