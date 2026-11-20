package com.ledgersync.finance.repository;

import com.ledgersync.finance.model.FinanceImportBatchEntity;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.model.FinanceRecordStatus;
import com.ledgersync.finance.model.FinanceSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinanceRecordRepository extends JpaRepository<FinanceRecordEntity, String> {

    List<FinanceRecordEntity> findByBatch(FinanceImportBatchEntity batch);

    Page<FinanceRecordEntity> findByBatch(FinanceImportBatchEntity batch, Pageable pageable);

    List<FinanceRecordEntity> findBySourceType(FinanceSourceType sourceType);

    List<FinanceRecordEntity> findBySourceTypeAndStatus(FinanceSourceType sourceType, FinanceRecordStatus status);

    Optional<FinanceRecordEntity> findBySourceTypeAndRecordReference(FinanceSourceType sourceType, String recordReference);

    List<FinanceRecordEntity> findByRecordReference(String recordReference);

    List<FinanceRecordEntity> findByOrderReference(String orderReference);

    List<FinanceRecordEntity> findBySecondaryReference(String secondaryReference);

    @Query("SELECT r FROM FinanceRecordEntity r WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(r.recordReference) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.orderReference) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.secondaryReference) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:sourceType IS NULL OR r.sourceType = :sourceType) AND " +
           "(:status IS NULL OR r.status = :status)")
    Page<FinanceRecordEntity> searchRecords(
            @Param("query") String query,
            @Param("sourceType") FinanceSourceType sourceType,
            @Param("status") FinanceRecordStatus status,
            Pageable pageable
    );

    long countBySourceType(FinanceSourceType sourceType);

    long countBySourceTypeAndStatus(FinanceSourceType sourceType, FinanceRecordStatus status);

    long countByBatchAndStatus(FinanceImportBatchEntity batch, FinanceRecordStatus status);

    long countByStatus(FinanceRecordStatus status);

    @Query("SELECT COALESCE(SUM(r.amountMinor), 0) FROM FinanceRecordEntity r WHERE r.status = 'UNRECONCILED' OR r.status = 'DISCREPANCY'")
    long sumUnreconciledExposure();

    @Query("SELECT COALESCE(SUM(r.amountMinor), 0) FROM FinanceRecordEntity r WHERE r.sourceType = :sourceType")
    long sumAmountBySourceType(@Param("sourceType") FinanceSourceType sourceType);
}
