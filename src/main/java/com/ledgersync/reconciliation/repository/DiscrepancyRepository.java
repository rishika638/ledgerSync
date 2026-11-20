package com.ledgersync.reconciliation.repository;

import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import com.ledgersync.reconciliation.model.DiscrepancySeverity;
import com.ledgersync.reconciliation.model.DiscrepancyStatus;
import com.ledgersync.reconciliation.model.DiscrepancyType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscrepancyRepository extends JpaRepository<DiscrepancyEntity, String> {

    Page<DiscrepancyEntity> findByStatus(DiscrepancyStatus status, Pageable pageable);

    List<DiscrepancyEntity> findByRunId(String runId);

    Page<DiscrepancyEntity> findByRunId(String runId, Pageable pageable);

    List<DiscrepancyEntity> findByPaymentIdOrProviderPaymentId(String paymentId, String providerPaymentId);

    @Query("""
            SELECT d FROM DiscrepancyEntity d
            WHERE d.providerPaymentId = :providerPaymentId
              AND d.type = :type
              AND d.status IN ('OPEN', 'INVESTIGATING', 'INVESTIGATED', 'AWAITING_APPROVAL')
            """)
    Optional<DiscrepancyEntity> findActiveDiscrepancy(
            @Param("providerPaymentId") String providerPaymentId,
            @Param("type") DiscrepancyType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DiscrepancyEntity d WHERE d.id = :id")
    Optional<DiscrepancyEntity> findByIdForUpdate(@Param("id") String id);

    long countByStatus(DiscrepancyStatus status);

    long countBySeverity(DiscrepancySeverity severity);

    long countByType(DiscrepancyType type);

    @Query("SELECT COUNT(d) FROM DiscrepancyEntity d WHERE d.run.id = :runId AND d.status = :status")
    long countByRunIdAndStatus(@Param("runId") String runId, @Param("status") DiscrepancyStatus status);

    @Query("SELECT COUNT(d) FROM DiscrepancyEntity d WHERE d.run.id = :runId")
    long countByRunId(@Param("runId") String runId);

    @Query("SELECT d FROM DiscrepancyEntity d WHERE d.ledgerRecordId IN :recordIds " +
            "OR d.gatewayRecordId IN :recordIds OR d.bankRecordId IN :recordIds")
    List<DiscrepancyEntity> findByFinanceRecordIds(@Param("recordIds") List<String> recordIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM DiscrepancyEntity d WHERE d.ledgerRecordId IN :recordIds " +
            "OR d.gatewayRecordId IN :recordIds OR d.bankRecordId IN :recordIds")
    void deleteForFinanceRecords(@Param("recordIds") List<String> recordIds);

    @Query("SELECT d.type, COUNT(d) FROM DiscrepancyEntity d GROUP BY d.type")
    List<Object[]> countExceptionsByType();

    @Query("SELECT d.type, COUNT(d) FROM DiscrepancyEntity d WHERE d.run.id = :runId GROUP BY d.type")
    List<Object[]> countExceptionsByTypeForRun(@Param("runId") String runId);

    @Query("SELECT d.severity, COUNT(d) FROM DiscrepancyEntity d GROUP BY d.severity")
    List<Object[]> countExceptionsBySeverity();

    @Query("SELECT d.severity, COUNT(d) FROM DiscrepancyEntity d WHERE d.run.id = :runId GROUP BY d.severity")
    List<Object[]> countExceptionsBySeverityForRun(@Param("runId") String runId);

    @Query("SELECT COALESCE(SUM(d.amountMinor), 0) FROM DiscrepancyEntity d WHERE d.status IN ('OPEN', 'INVESTIGATING', 'INVESTIGATED', 'AWAITING_APPROVAL')")
    long sumUnresolvedDiscrepancyExposure();

    @Query("SELECT COALESCE(SUM(d.amountMinor), 0) FROM DiscrepancyEntity d " +
            "WHERE d.run.id = :runId AND d.status IN ('OPEN', 'INVESTIGATING', 'INVESTIGATED', 'AWAITING_APPROVAL')")
    long sumUnresolvedDiscrepancyExposureForRun(@Param("runId") String runId);

    @Query("SELECT d FROM DiscrepancyEntity d WHERE " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:severity IS NULL OR d.severity = :severity) AND " +
           "(:type IS NULL OR d.type = :type) AND " +
           "(:runId IS NULL OR d.run.id = :runId) AND " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(d.id) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.providerPaymentId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.evidence) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<DiscrepancyEntity> searchDiscrepancies(
            @Param("status") DiscrepancyStatus status,
            @Param("severity") DiscrepancySeverity severity,
            @Param("type") DiscrepancyType type,
            @Param("runId") String runId,
            @Param("query") String query,
            Pageable pageable
    );
}
