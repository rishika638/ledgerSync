package com.ledgersync.finance.repository;

import com.ledgersync.finance.model.AiInvestigationEntity;
import com.ledgersync.reconciliation.model.DiscrepancyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiInvestigationRepository extends JpaRepository<AiInvestigationEntity, String> {

    List<AiInvestigationEntity> findByDiscrepancyOrderByCreatedAtDesc(DiscrepancyEntity discrepancy);

    Optional<AiInvestigationEntity> findFirstByDiscrepancyOrderByCreatedAtDesc(DiscrepancyEntity discrepancy);

    long countByValidationPassed(boolean validationPassed);
}
