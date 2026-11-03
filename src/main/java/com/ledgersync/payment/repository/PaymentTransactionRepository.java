package com.ledgersync.payment.repository;

import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.payment.model.PaymentStatus;
import com.ledgersync.payment.model.PaymentTransactionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, String> {

    Optional<PaymentTransactionEntity> findByProviderAndProviderPaymentId(PaymentProvider provider, String providerPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransactionEntity p WHERE p.provider = :provider AND p.providerPaymentId = :providerPaymentId")
    Optional<PaymentTransactionEntity> findByProviderAndProviderPaymentIdForUpdate(
            @Param("provider") PaymentProvider provider,
            @Param("providerPaymentId") String providerPaymentId);

    Optional<PaymentTransactionEntity> findByIdempotencyKey(String idempotencyKey);

    Page<PaymentTransactionEntity> findByCustomerId(String customerId, Pageable pageable);

    Page<PaymentTransactionEntity> findByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentTransactionEntity> findByProvider(PaymentProvider provider, Pageable pageable);

    @Query("""
            SELECT p FROM PaymentTransactionEntity p
            WHERE p.provider = :provider
              AND p.status IN ('CREATED', 'AUTHORIZED')
              AND p.createdAt <= :cutoff
            """)
    java.util.List<PaymentTransactionEntity> findStaleUnresolved(
            @Param("provider") PaymentProvider provider,
            @Param("cutoff") java.time.Instant cutoff,
            Pageable pageable);
}
