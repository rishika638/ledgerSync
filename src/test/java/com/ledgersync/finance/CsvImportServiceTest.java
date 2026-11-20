package com.ledgersync.finance;

import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.model.FinanceSourceType;
import com.ledgersync.finance.repository.FinanceImportBatchRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import com.ledgersync.finance.service.CsvImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CsvImportServiceTest {

    private FinanceImportBatchRepository batchRepository;
    private FinanceRecordRepository recordRepository;
    private CsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        batchRepository = mock(FinanceImportBatchRepository.class);
        recordRepository = mock(FinanceRecordRepository.class);
        when(batchRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        csvImportService = new CsvImportService(batchRepository, recordRepository, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void importsAmountsAsExactMinorUnits() {
        String csv = "ledger_id,order_id,customer_id,currency,amount,fee,tax,created_at,description,status\n" +
                "PAY-1,ORD-0001,CUST-1,INR,1234.56,24.69,4.44,2026-11-15T10:00:00Z,Sample,CAPTURED\n";

        CsvImportService.ImportResult result = csvImportService.importCsv(
                stream(csv), "ledger.csv", com.ledgersync.finance.model.FinanceSourceType.LEDGER, null);

        assertEquals("COMPLETED", result.status);
        assertEquals(1, result.validRows);
        assertEquals(0, result.invalidRows);
        var records = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(recordRepository).saveAll(records.capture());
        FinanceRecordEntity record = (FinanceRecordEntity) records.getValue().iterator().next();
        assertEquals(123456L, record.getAmountMinor());
        assertEquals(2469L, record.getFeeMinor());
        assertEquals(444L, record.getTaxMinor());
    }

    @Test
    void rejectsFractionsSmallerThanOneMinorUnitInsteadOfRounding() {
        String csv = "ledger_id,order_id,customer_id,currency,amount,fee,tax,created_at,description,status\n" +
                "PAY-2,ORD-0002,CUST-2,INR,12.345,0.00,0.00,2026-11-15T10:00:00Z,Sample,CAPTURED\n";

        CsvImportService.ImportResult result = csvImportService.importCsv(
                stream(csv), "ledger.csv", com.ledgersync.finance.model.FinanceSourceType.LEDGER, null);

        assertEquals(0, result.validRows);
        assertEquals(1, result.invalidRows);
        assertTrue(result.invalidRowErrors.get(0).contains("Rounding necessary"));
        verify(recordRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void acceptsUtf8BomAndValidHeadersForAllThreeSources() {
        String ledger = "\uFEFFledger_id,order_id,amount,created_at\nPAY-1,ORD-0001,12.34,2026-11-15T10:00:00Z\n";
        String razorpay = "payment_id,order_id,gross_amount,settled_at\nrzp-1,ORD-0001,12.34,2026-11-15T10:00:00Z\n";
        String bank = "bank_txn_id,value_date,credit_amount,currency\nbank-1,2026-11-15T10:00:00Z,12.34,USD\n";

        assertEquals("COMPLETED", importCsv(ledger, FinanceSourceType.LEDGER).status);
        assertEquals("COMPLETED", importCsv(razorpay, FinanceSourceType.RAZORPAY).status);
        CsvImportService.ImportResult bankResult = importCsv(bank, FinanceSourceType.BANK);

        assertEquals("COMPLETED", bankResult.status);
        assertEquals(1, bankResult.validRows);
        var records = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(recordRepository, org.mockito.Mockito.times(3)).saveAll(records.capture());
        List<?> savedBanks = (List<?>) records.getAllValues().get(2);
        FinanceRecordEntity bankRecord = (FinanceRecordEntity) savedBanks.get(0);
        assertEquals("USD", bankRecord.getCurrency());
        assertEquals(1234L, bankRecord.getAmountMinor());
    }

    @Test
    void rejectsEmptyHeaderOnlyAndWrongSourceFilesWithoutSavingRecords() {
        CsvImportService.ImportResult empty = importCsv("", FinanceSourceType.LEDGER);
        CsvImportService.ImportResult headerOnly = importCsv(
                "ledger_id,order_id,amount,created_at\n", FinanceSourceType.LEDGER);
        CsvImportService.ImportResult wrongSource = importCsv(
                "ledger_id,order_id,amount,created_at\n", FinanceSourceType.BANK);

        assertEquals("FAILED", empty.status);
        assertEquals("FAILED", headerOnly.status);
        assertEquals("FAILED", wrongSource.status);
        assertTrue(wrongSource.invalidRowErrors.get(0).contains("missing required columns"));
        verify(recordRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void keepsValidRowsAndReportsMalformedAmountsAndDatesByLine() {
        String csv = "ledger_id,order_id,amount,created_at\n" +
                "PAY-1,ORD-0001,12.34,2026-11-15T10:00:00Z\n" +
                "PAY-2,ORD-0002,12.345,2026-11-15T10:00:00Z\n" +
                "PAY-3,ORD-0003,15.00,not-a-date\n";

        CsvImportService.ImportResult result = importCsv(csv, FinanceSourceType.LEDGER);

        assertEquals("COMPLETED", result.status);
        assertEquals(3, result.totalRows);
        assertEquals(1, result.validRows);
        assertEquals(2, result.invalidRows);
        assertTrue(result.invalidRowErrors.get(0).contains("Line 3"));
        assertTrue(result.invalidRowErrors.get(1).contains("Line 4"));
        verify(recordRepository).saveAll(any());
    }

    @Test
    void rejectsMalformedUtf8WithoutPersistingPartialRows() {
        byte[] prefix = ("ledger_id,order_id,amount,created_at\n" +
                "PAY-1,ORD-0001,12.34,2026-11-15T10:00:00Z\n").getBytes(StandardCharsets.UTF_8);
        byte[] malformed = java.util.Arrays.copyOf(prefix, prefix.length + 1);
        malformed[prefix.length] = (byte) 0xC3;

        CsvImportService.ImportResult result = csvImportService.importCsv(
                new ByteArrayInputStream(malformed), "ledger.csv", FinanceSourceType.LEDGER, null);

        assertEquals("FAILED", result.status);
        assertTrue(result.invalidRowErrors.get(0).contains("UTF-8"));
        verify(recordRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void preservesDuplicateRowsWithinAFileForReconciliationDetection() {
        String csv = "ledger_id,order_id,amount,created_at\n" +
                "PAY-DUP,ORD-0001,12.34,2026-11-15T10:00:00Z\n" +
                "PAY-DUP,ORD-0001,12.34,2026-11-15T10:00:00Z\n";

        CsvImportService.ImportResult result = importCsv(csv, FinanceSourceType.LEDGER);

        assertEquals(2, result.validRows);
        var records = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(recordRepository).saveAll(records.capture());
        assertEquals(2, ((List<?>) records.getValue()).size());
    }

    @Test
    void parsesQuotedMultilineFieldsAndEscapedQuotes() throws Exception {
        String csv = "ledger_id,order_id,amount,created_at,description\n" +
                "PAY-1,ORD-0001,12.34,2026-11-15T10:00:00Z,\"first line, with comma\nsecond \"\"quoted\"\" line\"\n";

        CsvImportService.ImportResult result = importCsv(csv, FinanceSourceType.LEDGER);

        assertEquals("COMPLETED", result.status);
        assertEquals(1, result.totalRows);
        assertEquals(1, result.validRows);
        var records = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(recordRepository).saveAll(records.capture());
        FinanceRecordEntity record = (FinanceRecordEntity) records.getValue().iterator().next();
        var metadata = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(record.getRawMetadata(), java.util.Map.class);
        assertEquals("first line, with comma\nsecond \"quoted\" line", metadata.get("description"));
    }

    @Test
    void repeatedIdenticalUploadReturnsOriginalBatchWithoutDuplicatingRecords() {
        String csv = "ledger_id,order_id,amount,created_at\n" +
                "PAY-1,ORD-0001,12.34,2026-11-15T10:00:00Z\n";
        var originalBatch = new com.ledgersync.finance.model.FinanceImportBatchEntity(
                "original-batch", "Ledger Import", FinanceSourceType.LEDGER,
                "ledger.csv", 1, 1, 0, "COMPLETED", null);
        when(batchRepository.findFirstBySourceTypeAndContentSha256(
                org.mockito.ArgumentMatchers.eq(FinanceSourceType.LEDGER),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty(), Optional.of(originalBatch));

        CsvImportService.ImportResult first = importCsv(csv, FinanceSourceType.LEDGER);
        CsvImportService.ImportResult duplicate = importCsv(csv, FinanceSourceType.LEDGER);

        assertEquals("COMPLETED", first.status);
        assertEquals("original-batch", duplicate.batchId);
        assertTrue(duplicate.duplicateUpload);
        assertEquals(1, duplicate.validRows);
        assertTrue(duplicate.message.contains("no records were added"));
        verify(recordRepository, org.mockito.Mockito.times(1)).saveAll(any());
        verify(batchRepository).delete(any(com.ledgersync.finance.model.FinanceImportBatchEntity.class));
    }

    @Test
    void reportsColumnCountAndInvalidCurrencyPerPhysicalDataRow() {
        String csv = "ledger_id,order_id,currency,amount,created_at\n" +
                "PAY-1,ORD-0001,INR,12.34,2026-11-15T10:00:00Z\n" +
                "PAY-2,ORD-0002,INR,12.34\n" +
                "PAY-3,ORD-0003,TOOLONG,12.34,2026-11-15T10:00:00Z\n";

        CsvImportService.ImportResult result = importCsv(csv, FinanceSourceType.LEDGER);

        assertEquals(3, result.totalRows);
        assertEquals(1, result.validRows);
        assertEquals(2, result.invalidRows);
        assertTrue(result.invalidRowErrors.get(0).contains("Line 3: Expected 5 columns but found 4"));
        assertTrue(result.invalidRowErrors.get(1).contains("Currency must be a three-letter ISO code"));
    }

    private CsvImportService.ImportResult importCsv(String csv, FinanceSourceType sourceType) {
        return csvImportService.importCsv(stream(csv), sourceType.name().toLowerCase() + ".csv", sourceType, null);
    }

    private ByteArrayInputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
