package com.ledgersync.finance.service;

import com.ledgersync.finance.model.FinanceImportBatchEntity;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.model.FinanceSourceType;
import com.ledgersync.finance.repository.FinanceImportBatchRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.finance.repository.ReconciliationMatchRepository;
import com.ledgersync.reconciliation.repository.DiscrepancyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DemoDataLoaderService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoaderService.class);
    private static final List<String> DEMO_BATCH_NAMES = List.of(
            "Demo Merchant Payment Ledger",
            "Demo Razorpay Settlements",
            "Demo Bank Statement (HDFC)"
    );

    private final CsvImportService csvImportService;
    private final FinanceImportBatchRepository batchRepository;
    private final FinanceRecordRepository recordRepository;
    private final ReconciliationMatchRepository matchRepository;
    private final DiscrepancyRepository discrepancyRepository;

    public DemoDataLoaderService(CsvImportService csvImportService,
                                 FinanceImportBatchRepository batchRepository,
                                 FinanceRecordRepository recordRepository,
                                 ReconciliationMatchRepository matchRepository,
                                 DiscrepancyRepository discrepancyRepository) {
        this.csvImportService = csvImportService;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.matchRepository = matchRepository;
        this.discrepancyRepository = discrepancyRepository;
    }

    public static class DemoLoadResult {
        public boolean alreadyLoaded;
        public int ledgerRecordsLoaded;
        public int razorpayRecordsLoaded;
        public int bankRecordsLoaded;
        public String message;
        public Map<String, Object> details = new HashMap<>();
    }

    @Transactional
    public DemoLoadResult loadDemoDataset(boolean forceReload) {
        DemoLoadResult result = new DemoLoadResult();

        List<FinanceImportBatchEntity> existingBatches = findDemoBatches();
        if (!forceReload && isCompleteDemoDataset(existingBatches)) {
            long totalRecords = recordRepository.count();
            result.alreadyLoaded = true;
            result.message = "Demo dataset already loaded (" + totalRecords + " total records present).";
            result.details.put("totalRecords", totalRecords);
            return result;
        }

        if (forceReload || !existingBatches.isEmpty()) {
            resetDemoData();
        }

        result.ledgerRecordsLoaded = importDemoCsv(
                "demo-data/ledger.csv", "ledger.csv", FinanceSourceType.LEDGER, "Demo Merchant Payment Ledger");
        result.razorpayRecordsLoaded = importDemoCsv(
                "demo-data/razorpay_settlements.csv", "razorpay_settlements.csv",
                FinanceSourceType.RAZORPAY, "Demo Razorpay Settlements");
        result.bankRecordsLoaded = importDemoCsv(
                "demo-data/bank_statement.csv", "bank_statement.csv",
                FinanceSourceType.BANK, "Demo Bank Statement (HDFC)");

        result.alreadyLoaded = false;
        result.message = String.format("Successfully loaded demo dataset: %d ledger rows, %d gateway settlements, %d bank transactions.",
                result.ledgerRecordsLoaded, result.razorpayRecordsLoaded, result.bankRecordsLoaded);
        log.info("Demo dataset loaded successfully: {}", result.message);

        return result;
    }

    private int importDemoCsv(String resourceName, String filename, FinanceSourceType sourceType, String batchName) {
        Resource resource = new ClassPathResource(resourceName);
        try (InputStream in = resource.getInputStream()) {
            CsvImportService.ImportResult result = csvImportService.importCsv(in, filename, sourceType, batchName);
            if (!"COMPLETED".equals(result.status) || result.validRows == 0 || result.invalidRows > 0) {
                throw new IllegalStateException("Demo CSV import did not complete cleanly for " + filename +
                        ": status=" + result.status + ", validRows=" + result.validRows +
                        ", invalidRows=" + result.invalidRows + ", errors=" + result.invalidRowErrors);
            }
            return result.validRows;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read required demo CSV " + filename, e);
        }
    }

    @Transactional
    public void resetDemoData() {
        List<FinanceImportBatchEntity> demoBatches = findDemoBatches();
        if (demoBatches.isEmpty()) {
            return;
        }

        List<FinanceRecordEntity> demoRecords = new ArrayList<>();
        for (FinanceImportBatchEntity batch : demoBatches) {
            demoRecords.addAll(recordRepository.findByBatch(batch));
        }
        List<String> recordIds = demoRecords.stream().map(FinanceRecordEntity::getId).toList();
        if (!recordIds.isEmpty()) {
            matchRepository.deleteForFinanceRecords(recordIds);
            discrepancyRepository.deleteForFinanceRecords(recordIds);
            recordRepository.deleteAll(demoRecords);
        }
        batchRepository.deleteAll(demoBatches);
        log.info("Reset {} demo batches and {} demo records; non-demo data and run history were preserved.",
                demoBatches.size(), demoRecords.size());
    }

    private List<FinanceImportBatchEntity> findDemoBatches() {
        return DEMO_BATCH_NAMES.stream()
                .map(batchRepository::findFirstByBatchName)
                .flatMap(Optional::stream)
                .toList();
    }

    private boolean isCompleteDemoDataset(List<FinanceImportBatchEntity> batches) {
        if (batches.size() != DEMO_BATCH_NAMES.size()) {
            return false;
        }
        return batches.stream().allMatch(batch -> !recordRepository.findByBatch(batch).isEmpty());
    }
}
