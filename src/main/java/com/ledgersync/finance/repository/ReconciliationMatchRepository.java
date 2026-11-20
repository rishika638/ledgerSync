package com.ledgersync.finance.repository;

import com.ledgersync.finance.model.MatchType;
import com.ledgersync.finance.model.ReconciliationMatchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ReconciliationMatchRepository extends JpaRepository<ReconciliationMatchEntity, String> {

    @EntityGraph(attributePaths = {"ledgerRecord", "gatewayRecord", "bankRecord"})
    List<ReconciliationMatchEntity> findByRunId(String runId);

    @EntityGraph(attributePaths = {"ledgerRecord", "gatewayRecord", "bankRecord"})
    Page<ReconciliationMatchEntity> findByRunId(String runId, Pageable pageable);

    long countByRunId(String runId);

    long countByRunIdAndMatchType(String runId, MatchType matchType);

    @Modifying
    @Transactional
    @Query("DELETE FROM ReconciliationMatchEntity m WHERE m.ledgerRecord.id IN :recordIds " +
            "OR m.gatewayRecord.id IN :recordIds OR m.bankRecord.id IN :recordIds")
    void deleteForFinanceRecords(@Param("recordIds") List<String> recordIds);

    @EntityGraph(attributePaths = {"ledgerRecord", "gatewayRecord", "bankRecord"})
    @Query("SELECT m FROM ReconciliationMatchEntity m WHERE m.runId = :runId AND " +
           "(:matchType IS NULL OR m.matchType = :matchType) AND " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(m.ledgerRecord.recordReference) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(m.gatewayRecord.recordReference) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(m.bankRecord.recordReference) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<ReconciliationMatchEntity> searchMatches(
            @Param("runId") String runId,
            @Param("matchType") MatchType matchType,
            @Param("query") String query,
            Pageable pageable
    );
}
