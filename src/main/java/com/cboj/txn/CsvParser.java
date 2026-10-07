package com.cboj.txn;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Expected columns:
 *   accounts:     accountId, customerName, accountType, status, balance, dailyLimit, currency, openedDate
 *   transactions: transactionId, timestamp, type, fromAccount, toAccount, amount, channel, description
 */
@Component
public class CsvParser {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).get();

    /** Accounts keyed by id. Rows with an unreadable balance are skipped. */
    public Map<String, Account> readAccounts(InputStream in) throws IOException {
        Map<String, Account> accounts = new LinkedHashMap<>();
        for (CSVRecord row : parse(in)) {
            BigDecimal balance = toMoney(row.get("balance"));
            if (balance != null) {
                String id = row.get("accountId");
                accounts.put(id, new Account(id, row.get("customerName"), balance, row.get("status"),
                        toMoney(row.get("dailyLimit"))));
            }
        }
        return accounts;
    }

    public List<Transaction> readTransactions(InputStream in) throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        for (CSVRecord row : parse(in)) {
            transactions.add(new Transaction(
                    row.get("transactionId"),
                    toTimestamp(row.get("timestamp")),
                    TransactionType.parse(row.get("type")),
                    row.get("type"),
                    blankToNull(row.get("fromAccount")),
                    blankToNull(row.get("toAccount")),
                    toMoney(row.get("amount")),
                    row.get("channel"),
                    row.get("description")));
        }
        return transactions;
    }

    private static List<CSVRecord> parse(InputStream in) throws IOException {
        try (CSVParser parser = CSVParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8), FORMAT)) {
            return parser.getRecords();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal toMoney(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime toTimestamp(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
