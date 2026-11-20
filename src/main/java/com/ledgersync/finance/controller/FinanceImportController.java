package com.ledgersync.finance.controller;

import com.ledgersync.common.error.ResourceNotFoundException;
import com.ledgersync.finance.model.FinanceImportBatchEntity;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.model.FinanceSourceType;
import com.ledgersync.finance.repository.FinanceImportBatchRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.finance.service.CsvImportService;
import com.ledgersync.finance.service.DemoDataLoaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/imports")
@Tag(name = "Finance Data Imports", description = "Import financial CSV files for Ledger, Razorpay, and Bank statements")
public class FinanceImportController {

    private final CsvImportService csvImportService;
    private final DemoDataLoaderService demoDataLoaderService;
    private final FinanceImportBatchRepository batchRepository;
    private final FinanceRecordRepository recordRepository;

    public FinanceImportController(CsvImportService csvImportService,
                                   DemoDataLoaderService demoDataLoaderService,
                                   FinanceImportBatchRepository batchRepository,
                                   FinanceRecordRepository recordRepository) {
        this.csvImportService = csvImportService;
        this.demoDataLoaderService = demoDataLoaderService;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Financial CSV File")
    public ResponseEntity<CsvImportService.ImportResult> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") FinanceSourceType sourceType,
            @RequestParam(value = "batchName", required = false) String batchName) {
        try {
            InputStream in = file.getInputStream();
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "imported.csv";
            CsvImportService.ImportResult result = csvImportService.importCsv(in, filename, sourceType, batchName);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            CsvImportService.ImportResult failed = new CsvImportService.ImportResult();
            failed.sourceType = sourceType;
            failed.invalidRowErrors.add("File read error: " + e.getMessage());
            return ResponseEntity.badRequest().body(failed);
        }
    }

    @GetMapping("/batches")
    @Operation(summary = "List Import Batches with Pagination")
    public ResponseEntity<Page<FinanceImportBatchEntity>> listBatches(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(batchRepository.findAllByOrderByCreatedAtDesc(pageable));
    }

    @GetMapping("/batches/{id}/records")
    @Operation(summary = "Get Records in Batch")
    public ResponseEntity<Page<FinanceRecordEntity>> getBatchRecords(
            @PathVariable("id") String id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        FinanceImportBatchEntity batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + id));
        return ResponseEntity.ok(recordRepository.findByBatch(batch, pageable));
    }

    @PostMapping("/demo/load")
    @Operation(summary = "One-Click Load Synthetic Demo Dataset (>=150 records)")
    public ResponseEntity<DemoDataLoaderService.DemoLoadResult> loadDemoDataset(
            @RequestParam(value = "forceReload", defaultValue = "false") boolean forceReload,
            Authentication authentication) {
        if (forceReload && (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())))) {
            throw new AccessDeniedException("forceReload requires administrator role");
        }
        DemoDataLoaderService.DemoLoadResult result = demoDataLoaderService.loadDemoDataset(forceReload);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/demo/reset")
    @Operation(summary = "Reset Demo Dataset while preserving non-demo data and run history")
    public ResponseEntity<Map<String, String>> resetDemo() {
        demoDataLoaderService.resetDemoData();
        return ResponseEntity.ok(Map.of("message", "Demo imports and linked matches/exceptions have been removed; non-demo data and run history were preserved."));
    }

    @GetMapping("/templates/{source}")
    @Operation(summary = "Download CSV Sample Template")
    public ResponseEntity<String> getTemplate(@PathVariable("source") String source) {
        String template;
        String filename;
        switch (source.toLowerCase()) {
            case "ledger":
                template = "ledger_id,order_id,customer_id,currency,amount,fee,tax,created_at,description,status\n" +
                           "PAY-0001,ORD-0001,CUST-001,INR,1500.00,30.00,5.40,2026-11-15T10:00:00Z,Sample Order ORD-0001,CAPTURED\n";
                filename = "ledger_template.csv";
                break;
            case "razorpay":
                template = "payment_id,settlement_id,order_id,arn_utr,currency,gross_amount,fee,tax,net_amount,settled_at,payment_method,status\n" +
                           "pay_Rzp0001,setl_0001,ORD-0001,UTR98760001,INR,1500.00,30.00,5.40,1464.60,2026-11-16T12:00:00Z,upi,settled\n";
                filename = "razorpay_settlements_template.csv";
                break;
            case "bank":
                template = "bank_txn_id,account_number,value_date,credit_amount,debit_amount,currency,reference_utr,description,balance\n" +
                           "BNK-TXN-0001,XXXXXX9821,2026-11-16T15:00:00Z,1464.60,0.00,INR,UTR98760001,CMS/RAZORPAY/ORD-0001/UTR98760001,1001464.60\n";
                filename = "bank_statement_template.csv";
                break;
            default:
                return ResponseEntity.badRequest().body("Invalid template source: " + source);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(template);
    }
}
