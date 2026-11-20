package com.ledgersync.finance.repository;

import com.ledgersync.finance.model.FinanceImportBatchEntity;
import com.ledgersync.finance.model.FinanceSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinanceImportBatchRepository extends JpaRepository<FinanceImportBatchEntity, String> {

    List<FinanceImportBatchEntity> findBySourceType(FinanceSourceType sourceType);

    Page<FinanceImportBatchEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<FinanceImportBatchEntity> findFirstByBatchName(String batchName);

    Optional<FinanceImportBatchEntity> findFirstBySourceTypeAndContentSha256(
            FinanceSourceType sourceType, String contentSha256);

    Optional<FinanceImportBatchEntity> findFirstBySourceTypeAndStatusOrderByCreatedAtDesc(
            FinanceSourceType sourceType, String status);
}
