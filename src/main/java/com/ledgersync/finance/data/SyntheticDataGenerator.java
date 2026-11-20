package com.ledgersync.finance.data;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class SyntheticDataGenerator {

    public static class DatasetResult {
        public String ledgerCsv;
        public String razorpayCsv;
        public String bankCsv;
        public String groundTruthJson;
    }

    public static DatasetResult generate() {
        StringBuilder ledgerCsv = new StringBuilder();
        StringBuilder razorpayCsv = new StringBuilder();
        StringBuilder bankCsv = new StringBuilder();
        StringBuilder groundTruthJson = new StringBuilder();

        // CSV Headers
        ledgerCsv.append("ledger_id,order_id,customer_id,currency,amount,fee,tax,created_at,description,status\n");
        razorpayCsv.append("payment_id,settlement_id,order_id,arn_utr,currency,gross_amount,fee,tax,net_amount,settled_at,payment_method,status\n");
        bankCsv.append("bank_txn_id,account_number,value_date,credit_amount,debit_amount,currency,reference_utr,description,balance\n");

        groundTruthJson.append("{\n  \"generated_at\": \"").append(Instant.now()).append("\",\n  \"total_payments\": 165,\n  \"cases\": [\n");

        Instant baseTime = Instant.parse("2026-11-10T08:00:00Z");
        BigDecimal runningBalance = BigDecimal.valueOf(100000000L, 2);

        List<String> groundTruthEntries = new ArrayList<>();

        for (int i = 1; i <= 165; i++) {
            String paymentRef = String.format("PAY-%04d", i);
            String orderRef = String.format("ORD-%04d", i);
            String rzpPaymentId = String.format("pay_Rzp%04d", i);
            String settlementId = String.format("setl_%04d", i);
            String utr = String.format("UTR9876%04d", i);
            String customerId = String.format("CUST-%03d", (i % 50) + 1);

            Instant txnTime = baseTime.plus(i * 45, ChronoUnit.MINUTES);
            Instant settlementTime = txnTime.plus(24, ChronoUnit.HOURS); // T+1
            Instant bankTime = settlementTime.plus(2, ChronoUnit.HOURS);

            // Base amount in INR (between ₹200 and ₹25,000)
            long amountPaise = (200 + ((i * 137) % 24800)) * 100L;
            BigDecimal grossAmount = BigDecimal.valueOf(amountPaise, 2);
            // Standard fee: 2% + 18% GST on fee
            BigDecimal fee = percentage(grossAmount, "0.02");
            BigDecimal tax = percentage(fee, "0.18");
            BigDecimal netAmount = grossAmount.subtract(fee).subtract(tax);

            String category;
            String expectedDiscrepancy = null;

            if (i <= 110) {
                // Category 1: CLEAN 3-WAY MATCH (110 records)
                category = "CLEAN_3WAY_MATCH";
                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,upi,settled\n",
                        rzpPaymentId, settlementId, orderRef, utr, grossAmount, fee, tax, netAmount, settlementTime));
                runningBalance = runningBalance.add(netAmount);
                bankCsv.append(String.format("BNK-TXN-%04d,XXXXXX9821,%s,%.2f,0.00,INR,%s,CMS/RAZORPAY/%s/%s,%.2f\n",
                        i, bankTime, netAmount, utr, orderRef, utr, runningBalance));

            } else if (i <= 118) {
                // Category 2: MISSING_GATEWAY (8 records) - Ledger exists, but never reached Razorpay/Bank
                category = "MISSING_GATEWAY";
                expectedDiscrepancy = "MISSING_GATEWAY";
                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                // Omitted from razorpayCsv and bankCsv

            } else if (i <= 126) {
                // Category 3: MISSING_BANK (8 records) - In Ledger and Razorpay, missing from Bank
                category = "MISSING_BANK";
                expectedDiscrepancy = "MISSING_BANK";
                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,netbanking,settled\n",
                        rzpPaymentId, settlementId, orderRef, utr, grossAmount, fee, tax, netAmount, settlementTime));
                // Omitted from bankCsv

            } else if (i <= 133) {
                // Category 4: AMOUNT_MISMATCH (7 records) - Ledger gross differs from Razorpay gross
                category = "AMOUNT_MISMATCH";
                expectedDiscrepancy = "AMOUNT_MISMATCH";
                BigDecimal rzpGross = grossAmount.add(BigDecimal.valueOf(15000, 2)); // Cart change or surcharged
                BigDecimal rzpFee = percentage(rzpGross, "0.02");
                BigDecimal rzpTax = percentage(rzpFee, "0.18");
                BigDecimal rzpNet = rzpGross.subtract(rzpFee).subtract(rzpTax);

                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,card,settled\n",
                        rzpPaymentId, settlementId, orderRef, utr, rzpGross, rzpFee, rzpTax, rzpNet, settlementTime));
                runningBalance = runningBalance.add(rzpNet);
                bankCsv.append(String.format("BNK-TXN-%04d,XXXXXX9821,%s,%.2f,0.00,INR,%s,CMS/RAZORPAY/%s/%s,%.2f\n",
                        i, bankTime, rzpNet, utr, orderRef, utr, runningBalance));

            } else if (i <= 139) {
                // Category 5: DUPLICATE_RECORD (6 records) - Duplicate settlement records in gateway file
                category = "DUPLICATE_RECORD";
                expectedDiscrepancy = "DUPLICATE_RECORD";
                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,upi,settled\n",
                        rzpPaymentId, settlementId, orderRef, utr, grossAmount, fee, tax, netAmount, settlementTime));
                // Duplicate entry with same payment ID and order ID
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,upi,settled\n",
                        rzpPaymentId, settlementId + "_DUP", orderRef, utr + "D", grossAmount, fee, tax, netAmount, settlementTime.plus(30, ChronoUnit.MINUTES)));
                runningBalance = runningBalance.add(netAmount);
                bankCsv.append(String.format("BNK-TXN-%04d,XXXXXX9821,%s,%.2f,0.00,INR,%s,CMS/RAZORPAY/%s/%s,%.2f\n",
                        i, bankTime, netAmount, utr, orderRef, utr, runningBalance));

            } else if (i <= 145) {
                // Category 6: FEE_MISMATCH / TAX_MISMATCH (6 records) - Gateway deducted 3.8% fee instead of 2.0%
                category = "FEE_MISMATCH";
                expectedDiscrepancy = "FEE_MISMATCH";
                BigDecimal inflatedFee = percentage(grossAmount, "0.038");
                BigDecimal inflatedTax = percentage(inflatedFee, "0.18");
                BigDecimal reducedNet = grossAmount.subtract(inflatedFee).subtract(inflatedTax);

                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,credit_card,settled\n",
                        rzpPaymentId, settlementId, orderRef, utr, grossAmount, inflatedFee, inflatedTax, reducedNet, settlementTime));
                runningBalance = runningBalance.add(reducedNet);
                bankCsv.append(String.format("BNK-TXN-%04d,XXXXXX9821,%s,%.2f,0.00,INR,%s,CMS/RAZORPAY/%s/%s,%.2f\n",
                        i, bankTime, reducedNet, utr, orderRef, utr, runningBalance));

            } else if (i <= 151) {
                // Category 7: PARTIAL_SETTLEMENT (6 records) - Gateway settled only 60% of gross
                category = "PARTIAL_SETTLEMENT";
                expectedDiscrepancy = "PARTIAL_SETTLEMENT";
                BigDecimal partialGross = grossAmount.multiply(new BigDecimal("0.60")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal partialFee = percentage(partialGross, "0.02");
                BigDecimal partialTax = percentage(partialFee, "0.18");
                BigDecimal partialNet = partialGross.subtract(partialFee).subtract(partialTax);

                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,upi,settled\n",
                        rzpPaymentId, settlementId, orderRef, utr, partialGross, partialFee, partialTax, partialNet, settlementTime));
                runningBalance = runningBalance.add(partialNet);
                bankCsv.append(String.format("BNK-TXN-%04d,XXXXXX9821,%s,%.2f,0.00,INR,%s,CMS/RAZORPAY/%s/%s,%.2f\n",
                        i, bankTime, partialNet, utr, orderRef, utr, runningBalance));

            } else if (i <= 157) {
                // Category 8: DELAYED_SETTLEMENT (6 records) - Settled 8 days later (exceeding T+2 SLA)
                category = "DELAYED_SETTLEMENT";
                expectedDiscrepancy = "DELAYED_SETTLEMENT";
                Instant delayedSettlementTime = txnTime.plus(8, ChronoUnit.DAYS);
                Instant delayedBankTime = delayedSettlementTime.plus(4, ChronoUnit.HOURS);

                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,netbanking,settled\n",
                        rzpPaymentId, settlementId, orderRef, utr, grossAmount, fee, tax, netAmount, delayedSettlementTime));
                runningBalance = runningBalance.add(netAmount);
                bankCsv.append(String.format("BNK-TXN-%04d,XXXXXX9821,%s,%.2f,0.00,INR,%s,CMS/RAZORPAY/%s/%s,%.2f\n",
                        i, delayedBankTime, netAmount, utr, orderRef, utr, runningBalance));

            } else if (i <= 162) {
                // Category 9: REFERENCE_MISMATCH (5 records) - Bank credit reference corrupted/missing
                category = "REFERENCE_MISMATCH";
                expectedDiscrepancy = "REFERENCE_MISMATCH";
                String corruptedUtr = "UNKNOWN-REF-" + i;

                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, grossAmount, fee, tax, txnTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,%s,INR,%.2f,%.2f,%.2f,%.2f,%s,upi,settled\n",
                        rzpPaymentId, settlementId, orderRef, utr, grossAmount, fee, tax, netAmount, settlementTime));
                runningBalance = runningBalance.add(netAmount);
                bankCsv.append(String.format("BNK-TXN-%04d,XXXXXX9821,%s,%.2f,0.00,INR,%s,INTERNAL-CLEARING/GENERIC-BULK-CREDIT,%.2f\n",
                        i, bankTime, netAmount, corruptedUtr, runningBalance));

            } else {
                // Category 10: AMBIGUOUS_MATCH (3 records) - Identical amount at same timestamp with generic reference
                category = "AMBIGUOUS_MATCH";
                expectedDiscrepancy = "AMBIGUOUS_MATCH";
                BigDecimal identicalAmount = new BigDecimal("2500.00");
                BigDecimal idFee = new BigDecimal("50.00");
                BigDecimal idTax = new BigDecimal("9.00");
                BigDecimal idNet = new BigDecimal("2441.00");
                Instant identicalTime = Instant.parse("2026-11-18T14:00:00Z");

                ledgerCsv.append(String.format("%s,%s,%s,INR,%.2f,%.2f,%.2f,%s,Merchant Order %s,CAPTURED\n",
                        paymentRef, orderRef, customerId, identicalAmount, idFee, idTax, identicalTime, orderRef));
                razorpayCsv.append(String.format("%s,%s,%s,GENERIC-UPI,INR,%.2f,%.2f,%.2f,%.2f,%s,upi,settled\n",
                        rzpPaymentId, settlementId, "ORD-AMBIGUOUS", identicalAmount, idFee, idTax, idNet, identicalTime.plus(24, ChronoUnit.HOURS)));
                runningBalance = runningBalance.add(idNet);
                bankCsv.append(String.format("BNK-TXN-%04d,XXXXXX9821,%s,%.2f,0.00,INR,GENERIC-UPI,UPI-PAYMENT-INWARD,%.2f\n",
                        i, identicalTime.plus(26, ChronoUnit.HOURS), idNet, runningBalance));
            }

            groundTruthEntries.add(String.format(
                    "    {\n" +
                    "      \"payment_index\": %d,\n" +
                    "      \"payment_ref\": \"%s\",\n" +
                    "      \"order_ref\": \"%s\",\n" +
                    "      \"gateway_payment_id\": \"%s\",\n" +
                    "      \"category\": \"%s\",\n" +
                    "      \"expected_discrepancy\": %s\n" +
                    "    }",
                    i, paymentRef, orderRef, rzpPaymentId, category,
                    expectedDiscrepancy == null ? "null" : "\"" + expectedDiscrepancy + "\""
            ));
        }

        groundTruthJson.append(String.join(",\n", groundTruthEntries));
        groundTruthJson.append("\n  ]\n}");

        DatasetResult res = new DatasetResult();
        res.ledgerCsv = ledgerCsv.toString();
        res.razorpayCsv = razorpayCsv.toString();
        res.bankCsv = bankCsv.toString();
        res.groundTruthJson = groundTruthJson.toString();
        return res;
    }

    public static void writeFiles(Path targetDir) throws IOException {
        DatasetResult res = generate();
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("ledger.csv"), res.ledgerCsv);
        Files.writeString(targetDir.resolve("razorpay_settlements.csv"), res.razorpayCsv);
        Files.writeString(targetDir.resolve("bank_statement.csv"), res.bankCsv);
        Files.writeString(targetDir.resolve("ground_truth.json"), res.groundTruthJson);
    }

    private static BigDecimal percentage(BigDecimal amount, String rate) {
        return amount.multiply(new BigDecimal(rate)).setScale(2, RoundingMode.HALF_UP);
    }

    public static void main(String[] args) throws IOException {
        Path target = Paths.get("src/main/resources/demo-data");
        writeFiles(target);
        System.out.println("Generated synthetic dataset in " + target.toAbsolutePath());
    }
}
