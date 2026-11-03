package com.ledgersync.reconciliation.repository;

import com.ledgersync.payment.model.PaymentTransactionEntity;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.model.DiscrepancyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscrepancyRepository extends JpaRepository<DiscrepancyEntity, String> {

    Page<DiscrepancyEntity> findByStatus(DiscrepancyStatus status, Pageable pageable);

    List<DiscrepancyEntity> findByRunId(String runId);

    @Query("""
            SELECT d FROM DiscrepancyEntity d
            WHERE d.providerPaymentId = :providerPaymentId
              AND d.type = :type
              AND d.status IN ('OPEN', 'INVESTIGATING')
            """)
    Optional<DiscrepancyEntity> findActiveDiscrepancy(
            @Param("providerPaymentId") String providerPaymentId,
            @Param("type") DiscrepancyType type);

    long countByStatus(DiscrepancyStatus status);
}
