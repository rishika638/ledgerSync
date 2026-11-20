package com.ledgersync.finance.repository;

import com.ledgersync.finance.model.ResolutionActionEntity;
import com.ledgersync.finance.model.ResolutionActionStatus;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResolutionActionRepository extends JpaRepository<ResolutionActionEntity, String> {

    List<ResolutionActionEntity> findByDiscrepancyOrderByCreatedAtDesc(DiscrepancyEntity discrepancy);

    List<ResolutionActionEntity> findByStatusOrderByCreatedAtDesc(ResolutionActionStatus status);

    long countByStatus(ResolutionActionStatus status);
}
