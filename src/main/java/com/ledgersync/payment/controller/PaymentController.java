package com.ledgersync.payment.controller;

import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.payment.dto.PaymentResponse;
import com.ledgersync.payment.dto.RefundResponse;
import com.ledgersync.payment.model.PaymentStatus;
import com.ledgersync.payment.model.PaymentTransactionEntity;
import com.ledgersync.payment.repository.PaymentTransactionRepository;
import com.ledgersync.payment.repository.RefundRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment Transactions", description = "Query financial transactions, history, and associated refunds")
public class PaymentController {

    private final PaymentTransactionRepository paymentRepository;
    private final RefundRepository refundRepository;

    public PaymentController(
            PaymentTransactionRepository paymentRepository,
            RefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Payment by Internal ID")
    public ResponseEntity<PaymentResponse> getById(@PathVariable("id") String id) {
        PaymentTransactionEntity payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));

        List<RefundResponse> refunds = refundRepository.findByPaymentOrderByCreatedAtDesc(payment)
                .stream().map(RefundResponse::fromEntity).toList();

        return ResponseEntity.ok(PaymentResponse.fromEntity(payment, refunds));
    }

    @GetMapping("/provider/{provider}/{providerPaymentId}")
    @Operation(summary = "Get Payment by Provider & Provider Payment ID")
    public ResponseEntity<PaymentResponse> getByProviderPaymentId(
            @PathVariable("provider") String providerStr,
            @PathVariable("providerPaymentId") String providerPaymentId) {
        PaymentProvider provider = PaymentProvider.valueOf(providerStr.toUpperCase());
        PaymentTransactionEntity payment = paymentRepository.findByProviderAndProviderPaymentId(provider, providerPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for provider %s and id %s".formatted(provider, providerPaymentId)));

        List<RefundResponse> refunds = refundRepository.findByPaymentOrderByCreatedAtDesc(payment)
                .stream().map(RefundResponse::fromEntity).toList();

        return ResponseEntity.ok(PaymentResponse.fromEntity(payment, refunds));
    }

    @GetMapping
    @Operation(summary = "List Payments with Pagination and Optional Filters")
    public ResponseEntity<Page<PaymentResponse>> list(
            @Parameter(description = "Filter by status") @RequestParam(name = "status", required = false) PaymentStatus status,
            @Parameter(description = "Filter by customer ID") @RequestParam(name = "customerId", required = false) String customerId,
            @Parameter(description = "Filter by provider") @RequestParam(name = "provider", required = false) String providerStr,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<PaymentTransactionEntity> page;
        if (status != null) {
            page = paymentRepository.findByStatus(status, pageable);
        } else if (customerId != null && !customerId.isBlank()) {
            page = paymentRepository.findByCustomerId(customerId, pageable);
        } else if (providerStr != null && !providerStr.isBlank()) {
            PaymentProvider provider = PaymentProvider.valueOf(providerStr.toUpperCase());
            page = paymentRepository.findByProvider(provider, pageable);
        } else {
            page = paymentRepository.findAll(pageable);
        }

        return ResponseEntity.ok(page.map(p -> {
            List<RefundResponse> refunds = refundRepository.findByPaymentOrderByCreatedAtDesc(p)
                    .stream().map(RefundResponse::fromEntity).toList();
            return PaymentResponse.fromEntity(p, refunds);
        }));
    }

    @GetMapping("/{id}/refunds")
    @Operation(summary = "Get all refunds for a specific payment")
    public ResponseEntity<List<RefundResponse>> getRefunds(@PathVariable("id") String paymentId) {
        PaymentTransactionEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        List<RefundResponse> refunds = refundRepository.findByPaymentOrderByCreatedAtDesc(payment)
                .stream().map(RefundResponse::fromEntity).toList();

        return ResponseEntity.ok(refunds);
    }
}
