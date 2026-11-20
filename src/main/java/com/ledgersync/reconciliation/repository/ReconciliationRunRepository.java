package com.ledgersync.reconciliation.repository;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRunEntity, String> {

    Page<ReconciliationRunEntity> findByProvider(PaymentProvider provider, Pageable pageable);

    Page<ReconciliationRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<ReconciliationRunEntity> findByRunTypeOrderByStartedAtDesc(String runType, Pageable pageable);

    Optional<ReconciliationRunEntity> findByIdAndRunType(String id, String runType);

    Optional<ReconciliationRunEntity> findFirstByRunTypeOrderByStartedAtDesc(String runType);

    List<ReconciliationRunEntity> findTop10ByOrderByStartedAtDesc();

    List<ReconciliationRunEntity> findTop10ByRunTypeOrderByStartedAtDesc(String runType);

    Optional<ReconciliationRunEntity> findFirstByOrderByStartedAtDesc();
}
