package com.ledgersync.reconciliation.repository;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.reconciliation.model.ReconciliationRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRunEntity, String> {

    Page<ReconciliationRunEntity> findByProvider(PaymentProvider provider, Pageable pageable);

    List<ReconciliationRunEntity> findTop10ByOrderByStartedAtDesc();
}
