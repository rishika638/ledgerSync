package com.ledgersync.payment.repository;

import com.ledgersync.payment.model.PaymentTransactionEntity;
import com.ledgersync.payment.model.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, String> {

    List<RefundEntity> findByPaymentOrderByCreatedAtDesc(PaymentTransactionEntity payment);

    Optional<RefundEntity> findByPaymentAndProviderRefundId(PaymentTransactionEntity payment, String providerRefundId);

    boolean existsByPaymentAndProviderRefundId(PaymentTransactionEntity payment, String providerRefundId);
}
