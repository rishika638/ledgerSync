package com.ledgersync.finance.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyntheticDataGeneratorTest {

    @Test
    void generatesSettlementAmountsThatBalanceToThePaise() {
        SyntheticDataGenerator.DatasetResult dataset = SyntheticDataGenerator.generate();
        String[] rows = dataset.razorpayCsv.split("\\R");

        for (int i = 1; i < rows.length; i++) {
            String[] columns = rows[i].split(",", -1);
            BigDecimal gross = new BigDecimal(columns[5]);
            BigDecimal fee = new BigDecimal(columns[6]);
            BigDecimal tax = new BigDecimal(columns[7]);
            BigDecimal net = new BigDecimal(columns[8]);

            assertEquals(0, gross.subtract(fee).subtract(tax).compareTo(net), rows[i]);
        }
        assertEquals(165, dataset.groundTruthJson.split("\"payment_index\"").length - 1);
    }
}
