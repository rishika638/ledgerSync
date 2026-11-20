package com.ledgersync.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgersync.common.id.IdGenerator;
import com.ledgersync.finance.model.FinanceImportBatchEntity;
import com.ledgersync.finance.model.FinanceRecordEntity;
import com.ledgersync.finance.model.FinanceRecordStatus;
import com.ledgersync.finance.model.FinanceSourceType;
import com.ledgersync.finance.repository.FinanceImportBatchRepository;
import com.ledgersync.finance.repository.FinanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private static final Pattern ORDER_REGEX = Pattern.compile("(ORD-\\d{4})");
    private static final Map<FinanceSourceType, Set<String>> REQUIRED_HEADERS = Map.of(
            FinanceSourceType.LEDGER, Set.of("ledger_id", "order_id", "amount", "created_at"),
            FinanceSourceType.RAZORPAY, Set.of("payment_id", "order_id", "gross_amount", "settled_at"),
            FinanceSourceType.BANK, Set.of("bank_txn_id", "value_date", "credit_amount")
    );

    private final FinanceImportBatchRepository batchRepository;
    private final FinanceRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    public CsvImportService(FinanceImportBatchRepository batchRepository,
                            FinanceRecordRepository recordRepository,
                            ObjectMapper objectMapper) {
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
    }

    public static class ImportResult {
        public String batchId;
        public String batchName;
        public FinanceSourceType sourceType;
        public String status;
        public int totalRows;
        public int validRows;
        public int invalidRows;
        public boolean duplicateUpload;
        public String message;
        public List<String> invalidRowErrors = new ArrayList<>();
    }

    @Transactional
    public ImportResult importCsv(InputStream inputStream, String filename, FinanceSourceType sourceType, String batchName) {
        String batchId = IdGenerator.importBatchId();
        if (batchName == null || batchName.isBlank()) {
            batchName = sourceType.name() + " Import - " + filename;
        }

        ImportResult result = new ImportResult();
        result.batchId = batchId;
        result.batchName = batchName;
        result.sourceType = sourceType;

        List<FinanceRecordEntity> recordsToSave = new ArrayList<>();
        FinanceImportBatchEntity batch = new FinanceImportBatchEntity(
                batchId, batchName, sourceType, filename, 0, 0, 0, "PROCESSING", null
        );
        batch = batchRepository.saveAndFlush(batch);
        int[] physicalLine = {1};

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (PushbackReader reader = new PushbackReader(new BufferedReader(new InputStreamReader(
                    new DigestInputStream(inputStream, digest),
                    StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT))), 1)) {
                CsvRow headerRow = readCsvRow(reader, physicalLine);
                if (headerRow == null) {
                    return failBatch(batch, result, "The CSV file is empty.");
                }

                String[] headers = headerRow.values();
                if (headers.length > 0 && headers[0].startsWith("\uFEFF")) {
                    headers[0] = headers[0].substring(1);
                }
                Map<String, Integer> colIndex = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String header = headers[i].trim().toLowerCase(Locale.ROOT);
                    if (header.isEmpty() || colIndex.putIfAbsent(header, i) != null) {
                        return failBatch(batch, result, "CSV contains a blank or duplicate column name: " + header);
                    }
                }
                Set<String> missingHeaders = new TreeSet<>(REQUIRED_HEADERS.get(sourceType));
                missingHeaders.removeAll(colIndex.keySet());
                if (!missingHeaders.isEmpty()) {
                    return failBatch(batch, result, "CSV does not match source " + sourceType +
                            "; missing required columns: " + String.join(", ", missingHeaders));
                }

                CsvRow row;
                while ((row = readCsvRow(reader, physicalLine)) != null) {
                    if (row.values().length == 1 && row.values()[0].isBlank()) {
                        continue;
                    }
                    result.totalRows++;
                    try {
                        if (row.values().length != headers.length) {
                            throw new IllegalArgumentException("Expected " + headers.length +
                                    " columns but found " + row.values().length);
                        }
                        FinanceRecordEntity record = parseRecord(
                                row.values(), colIndex, sourceType, batch, row.startLine());
                        if (record != null) {
                            recordsToSave.add(record);
                            result.validRows++;
                        } else {
                            result.invalidRows++;
                            result.invalidRowErrors.add("Line " + row.startLine() +
                                    ": required record identifier is missing");
                        }
                    } catch (Exception e) {
                        result.invalidRows++;
                        result.invalidRowErrors.add("Line " + row.startLine() + ": " + e.getMessage());
                    }
                }

                if (result.totalRows == 0) {
                    return failBatch(batch, result, "CSV contains a header but no data rows.");
                }

                String contentSha256 = HexFormat.of().formatHex(digest.digest());
                Optional<FinanceImportBatchEntity> previousUpload =
                        batchRepository.findFirstBySourceTypeAndContentSha256(sourceType, contentSha256);
                if (previousUpload.isPresent()) {
                    FinanceImportBatchEntity existing = previousUpload.get();
                    batchRepository.delete(batch);
                    result.batchId = existing.getId();
                    result.batchName = existing.getBatchName();
                    result.status = existing.getStatus();
                    result.totalRows = existing.getTotalRows();
                    result.validRows = existing.getValidRows();
                    result.invalidRows = existing.getInvalidRows();
                    result.invalidRowErrors = existing.getErrorSummary() == null ||
                            existing.getErrorSummary().isBlank()
                            ? new ArrayList<>()
                            : new ArrayList<>(Arrays.asList(existing.getErrorSummary().split("\\R")));
                    result.duplicateUpload = true;
                    result.message = "Identical " + sourceType + " CSV already exists as batch " + existing.getId() +
                            "; no records were added.";
                    return result;
                }

                batch.setContentSha256(contentSha256);
                if (!recordsToSave.isEmpty()) {
                    recordRepository.saveAll(recordsToSave);
                }
            }

            batch.setTotalRows(result.totalRows);
            batch.setValidRows(result.validRows);
            batch.setInvalidRows(result.invalidRows);
            batch.setStatus("COMPLETED");
            result.status = "COMPLETED";
            result.message = "Import completed.";
            if (!result.invalidRowErrors.isEmpty()) {
                batch.setErrorSummary(String.join("\n", result.invalidRowErrors));
            }
            batch.setUpdatedAt(Instant.now());
            batchRepository.save(batch);

            log.info("Batch {} imported successfully: {} valid, {} invalid of {} total rows",
                    batchId, result.validRows, result.invalidRows, result.totalRows);

        } catch (IOException e) {
            log.error("Failed to parse CSV for source {}", sourceType, e);
            failBatch(batch, result, "Import error: invalid UTF-8 or malformed CSV near line " +
                    physicalLine[0] + ": " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }

        return result;
    }

    private ImportResult failBatch(FinanceImportBatchEntity batch, ImportResult result, String message) {
        batch.setStatus("FAILED");
        batch.setErrorSummary(message);
        batch.setTotalRows(result.totalRows);
        batch.setValidRows(result.validRows);
        batch.setInvalidRows(result.invalidRows);
        batch.setUpdatedAt(Instant.now());
        batchRepository.save(batch);
        result.status = "FAILED";
        result.message = message;
        result.invalidRowErrors.add(message);
        return result;
    }

    private record CsvRow(String[] values, int startLine) {
    }

    private CsvRow readCsvRow(PushbackReader reader, int[] physicalLine) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean closedQuote = false;
        boolean hasCharacters = false;
        int startLine = physicalLine[0];

        while (true) {
            int next = reader.read();
            if (next == -1) {
                if (inQuotes) {
                    throw new IOException("unclosed quoted field beginning on line " + startLine);
                }
                if (!hasCharacters && fields.isEmpty() && field.isEmpty()) {
                    return null;
                }
                fields.add(field.toString().trim());
                return new CsvRow(fields.toArray(String[]::new), startLine);
            }

            char current = (char) next;
            hasCharacters = true;
            if (inQuotes) {
                if (current == '"') {
                    int following = reader.read();
                    if (following == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        closedQuote = true;
                        if (following != -1) {
                            reader.unread(following);
                        }
                    }
                } else if (current == '\r' || current == '\n') {
                    consumeNewlineTail(reader, current);
                    field.append('\n');
                    physicalLine[0]++;
                } else {
                    field.append(current);
                }
                continue;
            }

            if (closedQuote && current != ',' && current != '\r' && current != '\n' &&
                    !Character.isWhitespace(current)) {
                throw new IOException("unexpected character after closing quote on line " + physicalLine[0]);
            }
            if (current == '"' && field.toString().isBlank() && !closedQuote) {
                if (!field.isEmpty()) {
                    field.setLength(0);
                }
                inQuotes = true;
                closedQuote = false;
            } else if (current == ',' ) {
                fields.add(field.toString().trim());
                field.setLength(0);
                closedQuote = false;
                hasCharacters = false;
            } else if (current == '\r' || current == '\n') {
                consumeNewlineTail(reader, current);
                physicalLine[0]++;
                fields.add(field.toString().trim());
                return new CsvRow(fields.toArray(String[]::new), startLine);
            } else if (closedQuote && Character.isWhitespace(current)) {
                continue;
            } else {
                if (current == '"') {
                    throw new IOException("quote inside unquoted field on line " + physicalLine[0]);
                }
                field.append(current);
            }
        }
    }

    private void consumeNewlineTail(PushbackReader reader, char first) throws IOException {
        if (first == '\r') {
            int following = reader.read();
            if (following != '\n' && following != -1) {
                reader.unread(following);
            }
        }
    }

    private FinanceRecordEntity parseRecord(String[] cols, Map<String, Integer> colIndex,
                                           FinanceSourceType sourceType, FinanceImportBatchEntity batch,
                                           int lineNum) {
        String recId = IdGenerator.financeRecordId();
        switch (sourceType) {
            case LEDGER: {
                String ledgerId = getCol(cols, colIndex, "ledger_id");
                if (ledgerId == null || ledgerId.isBlank()) return null;
                String orderId = getRequiredCol(cols, colIndex, "order_id");
                String customerId = getCol(cols, colIndex, "customer_id");
                String currency = getColDefault(cols, colIndex, "currency", "INR");
                validateCurrency(currency);
                long amountMinor = parseMinorUnits(getCol(cols, colIndex, "amount"));
                long feeMinor = parseMinorUnits(getColDefault(cols, colIndex, "fee", "0.00"));
                long taxMinor = parseMinorUnits(getColDefault(cols, colIndex, "tax", "0.00"));
                long netMinor = Math.subtractExact(Math.subtractExact(amountMinor, feeMinor), taxMinor);
                Instant timestamp = parseTimestamp(getCol(cols, colIndex, "created_at"));
                String desc = getColDefault(cols, colIndex, "description", "");
                String status = getColDefault(cols, colIndex, "status", "CAPTURED");

                Map<String, Object> meta = Map.of(
                        "description", desc,
                        "customer_id", customerId != null ? customerId : "",
                        "raw_status", status
                );

                return new FinanceRecordEntity(
                        recId, batch, sourceType, ledgerId, orderId, customerId,
                        currency, amountMinor, feeMinor, taxMinor, netMinor,
                        timestamp, null, FinanceRecordStatus.UNRECONCILED,
                        toJson(meta)
                );
            }
            case RAZORPAY: {
                String paymentId = getCol(cols, colIndex, "payment_id");
                if (paymentId == null || paymentId.isBlank()) return null;
                String orderId = getRequiredCol(cols, colIndex, "order_id");
                String settlementId = getCol(cols, colIndex, "settlement_id");
                String arnUtr = getCol(cols, colIndex, "arn_utr");
                String currency = getColDefault(cols, colIndex, "currency", "INR");
                validateCurrency(currency);
                long grossMinor = parseMinorUnits(getCol(cols, colIndex, "gross_amount"));
                long feeMinor = parseMinorUnits(getColDefault(cols, colIndex, "fee", "0.00"));
                long taxMinor = parseMinorUnits(getColDefault(cols, colIndex, "tax", "0.00"));
                long netMinor = parseMinorUnits(getColDefault(cols, colIndex, "net_amount", "0.00"));
                if (netMinor == 0) {
                    netMinor = Math.subtractExact(Math.subtractExact(grossMinor, feeMinor), taxMinor);
                }
                Instant settledAt = parseTimestamp(getCol(cols, colIndex, "settled_at"));
                String method = getColDefault(cols, colIndex, "payment_method", "upi");

                Map<String, Object> meta = Map.of(
                        "settlement_id", settlementId != null ? settlementId : "",
                        "payment_method", method,
                        "arn_utr", arnUtr != null ? arnUtr : ""
                );

                return new FinanceRecordEntity(
                        recId, batch, sourceType, paymentId, orderId, arnUtr,
                        currency, grossMinor, feeMinor, taxMinor, netMinor,
                        settledAt, settledAt, FinanceRecordStatus.UNRECONCILED,
                        toJson(meta)
                );
            }
            case BANK: {
                String bankTxnId = getCol(cols, colIndex, "bank_txn_id");
                if (bankTxnId == null || bankTxnId.isBlank()) return null;
                String accountNum = getColDefault(cols, colIndex, "account_number", "");
                Instant valueDate = parseTimestamp(getCol(cols, colIndex, "value_date"));
                long creditMinor = parseMinorUnits(getCol(cols, colIndex, "credit_amount"));
                String refUtr = getCol(cols, colIndex, "reference_utr");
                String description = getColDefault(cols, colIndex, "description", "");
                String currency = getColDefault(cols, colIndex, "currency", "INR");
                validateCurrency(currency);

                // Try extracting order ID from narration
                String extractedOrderId = null;
                Matcher matcher = ORDER_REGEX.matcher(description);
                if (matcher.find()) {
                    extractedOrderId = matcher.group(1);
                }

                Map<String, Object> meta = Map.of(
                        "account_number", accountNum,
                        "description", description,
                        "reference_utr", refUtr != null ? refUtr : ""
                );

                return new FinanceRecordEntity(
                        recId, batch, sourceType, bankTxnId, extractedOrderId, refUtr,
                        currency, creditMinor, 0L, 0L, creditMinor,
                        valueDate, valueDate, FinanceRecordStatus.UNRECONCILED,
                        toJson(meta)
                );
            }
            default:
                return null;
        }
    }

    private String getCol(String[] cols, Map<String, Integer> colIndex, String key) {
        Integer idx = colIndex.get(key.toLowerCase());
        if (idx == null || idx >= cols.length) return null;
        String val = cols[idx].trim();
        return val.isEmpty() ? null : val;
    }

    private String getColDefault(String[] cols, Map<String, Integer> colIndex, String key, String defaultVal) {
        String val = getCol(cols, colIndex, key);
        return val != null ? val : defaultVal;
    }

    private String getRequiredCol(String[] cols, Map<String, Integer> colIndex, String key) {
        String val = getCol(cols, colIndex, key);
        if (val == null) {
            throw new IllegalArgumentException("Required field is missing: " + key);
        }
        return val;
    }

    private long parseMinorUnits(String valStr) {
        if (valStr == null || valStr.isBlank()) {
            throw new IllegalArgumentException("Amount is required");
        }
        String cleaned = valStr.replace(",", "").trim();
        long minorUnits = new java.math.BigDecimal(cleaned)
                .setScale(2, java.math.RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        return minorUnits;
    }

    private void validateCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter ISO code");
        }
    }

    private Instant parseTimestamp(String valStr) {
        if (valStr == null || valStr.isBlank()) {
            throw new IllegalArgumentException("Timestamp is required");
        }
        try {
            return Instant.parse(valStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO-8601 timestamp: " + valStr, e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize import record metadata", e);
        }
    }

}
