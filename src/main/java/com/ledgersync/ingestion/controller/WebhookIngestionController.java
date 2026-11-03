package com.ledgersync.ingestion.controller;

import com.ledgersync.ingestion.dto.WebhookIngestionResponse;
import com.ledgersync.ingestion.model.PaymentProvider;
import com.ledgersync.ingestion.service.WebhookIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhook Ingestion", description = "Endpoints for ingesting signed provider payment webhooks")
public class WebhookIngestionController {

    private final WebhookIngestionService ingestionService;

    public WebhookIngestionController(WebhookIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/stripe")
    @Operation(
            summary = "Ingest Stripe Webhook",
            description = "Validates Stripe HMAC-SHA256 signature against raw bytes and persists event idempotently to PostgreSQL inbox",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Webhook ingested or duplicate acknowledged",
                            content = @Content(schema = @Schema(implementation = WebhookIngestionResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid webhook signature or replay expired"),
                    @ApiResponse(responseCode = "400", description = "Malformed webhook payload")
            }
    )
    public ResponseEntity<WebhookIngestionResponse> ingestStripe(
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader,
            @RequestBody byte[] rawPayload
    ) {
        WebhookIngestionResponse response = ingestionService.ingest(PaymentProvider.STRIPE, rawPayload, signatureHeader);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/razorpay")
    @Operation(
            summary = "Ingest Razorpay Webhook",
            description = "Validates Razorpay HMAC-SHA256 signature against raw bytes and persists event idempotently to PostgreSQL inbox",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Webhook ingested or duplicate acknowledged",
                            content = @Content(schema = @Schema(implementation = WebhookIngestionResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid webhook signature"),
                    @ApiResponse(responseCode = "400", description = "Malformed webhook payload")
            }
    )
    public ResponseEntity<WebhookIngestionResponse> ingestRazorpay(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader,
            @RequestBody byte[] rawPayload
    ) {
        WebhookIngestionResponse response = ingestionService.ingest(PaymentProvider.RAZORPAY, rawPayload, signatureHeader);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{provider}")
    @Operation(
            summary = "Generic Webhook Ingestion Endpoint",
            description = "Ingests webhook for given provider using provider-specific signature verification"
    )
    public ResponseEntity<WebhookIngestionResponse> ingestGeneric(
            @Parameter(description = "Provider name (STRIPE, RAZORPAY)") @PathVariable("provider") String providerStr,
            @RequestHeader(value = "X-Signature", required = false) String signatureHeader,
            @RequestBody byte[] rawPayload
    ) {
        PaymentProvider provider = PaymentProvider.valueOf(providerStr.toUpperCase());
        WebhookIngestionResponse response = ingestionService.ingest(provider, rawPayload, signatureHeader);
        return ResponseEntity.ok(response);
    }
}
