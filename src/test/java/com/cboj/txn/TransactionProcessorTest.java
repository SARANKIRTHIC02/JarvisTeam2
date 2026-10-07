package com.cboj.txn;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionProcessorTest {

    private final TransactionProcessor processor =
            new TransactionProcessor(new BigDecimal("10000"), new BigDecimal("500"), 3, 10);
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private LocalDateTime clock = LocalDateTime.of(2026, 10, 15, 9, 0);

    @BeforeEach
    void setUp() {
        accounts.put("A1", new Account("A1", "Alice", new BigDecimal("1000"), "ACTIVE", null));
        accounts.put("A2", new Account("A2", "Bob", new BigDecimal("0"), "ACTIVE", null));
        accounts.put("RICH", new Account("RICH", "Carla", new BigDecimal("100000"), "ACTIVE", new BigDecimal("50000")));
        accounts.put("FROZEN", new Account("FROZEN", "Dev", new BigDecimal("1000"), "FROZEN", null));
    }

    @Test
    void movesMoneyForEachTransactionType() {
        add("T1", "PURCHASE", "A1", null, "100", 60);
        add("T2", "DEPOSIT", null, "A1", "50", 60);
        add("T3", "TRANSFER", "A1", "A2", "200", 60);
        add("T4", "withdrawal", "A1", null, "10", 60);

        BatchResult result = run();

        assertThat(result.approved()).isEqualTo(4);
        assertThat(accounts.get("A1").getBalance()).isEqualByComparingTo("740");
        assertThat(accounts.get("A2").getBalance()).isEqualByComparingTo("200");
    }

    @Test
    void rejectsInvalidTransactionsWithReason() {
        add("T1", "PURCHASE", "NOPE", null, "10", 60);
        add("T2", "TRANSFER", "A1", "FROZEN", "10", 60);
        add("T3", "PURCHASE", "A1", null, "0", 60);
        add("T4", "PURCHASE", "A1", null, "2000", 60);
        add("T5", "PURCHASE", "A1", null, "10", 60);
        add("T5", "PURCHASE", "A1", null, "10", 60);
        add("T6", "WITHDRAWAL", null, null, "10", 60);
        add("T7", "REVERSAL", "A1", null, "10", 60);
        transactions.add(new Transaction("T8", clock, TransactionType.PURCHASE, "PURCHASE", "A1", null, null, "POS", ""));

        assertThat(run().results()).extracting(TransactionResult::reason).containsExactly(
                "INVALID ACCOUNT", "INACTIVE ACCOUNT", "INVALID AMOUNT", "INSUFFICIENT FUNDS", null,
                "DUPLICATE TRANSACTION", "INVALID ACCOUNT", "UNSUPPORTED TRANSACTION TYPE", "INVALID RECORD");
        assertThat(accounts.get("A1").getBalance()).isEqualByComparingTo("990");
    }

    @Test
    void flagsButStillApprovesSuspiciousTransactions() {
        add("BIG", "PURCHASE", "RICH", null, "20000", 60);   // large amount
        add("D1", "PURCHASE", "A1", null, "300", 60);
        add("D2", "PURCHASE", "A1", null, "300", 1);         // 600 > daily limit of 500
        add("V1", "DEPOSIT", null, "A1", "1", 1);
        add("V2", "DEPOSIT", null, "A1", "1", 1);            // 4th tx on A1 within 10 minutes

        List<TransactionResult> results = run().results();

        assertThat(results).allMatch(r -> r.status().equals("APPROVED"));
        assertThat(results.get(0).flags()).containsExactly("LARGE AMOUNT");
        assertThat(results.get(1).flags()).isEmpty();
        assertThat(results.get(2).flags()).contains("DAILY LIMIT EXCEEDED");
        assertThat(results.get(4).flags()).contains("HIGH VELOCITY");
    }

    @Test
    void producesTextReportInRequiredFormat() {
        add("TX001", "PURCHASE", "A1", null, "10", 60);
        add("TX002", "PURCHASE", "NOPE", null, "10", 60);

        assertThat(run().report()).contains("TX001 APPROVED", "TX002 REJECTED - INVALID ACCOUNT",
                "Transactions Processed: 2", "Approved: 1", "Rejected: 1", "Flagged For Review: 0");
    }

    @Test
    void newTransactionBuildsOnBatchState() {
        add("T1", "PURCHASE", "A1", null, "900", 60);
        Ledger ledger = new Ledger(accounts);
        transactions.forEach(tx -> processor.apply(ledger, tx));

        clock = clock.plusMinutes(1);
        TransactionResult duplicate = processor.apply(ledger,
                new Transaction("T1", clock, TransactionType.DEPOSIT, "DEPOSIT", null, "A1", BigDecimal.TEN, "API", ""));
        TransactionResult overdraw = processor.apply(ledger,
                new Transaction("T2", clock, TransactionType.PURCHASE, "PURCHASE", "A1", null, new BigDecimal("150"), "API", ""));

        assertThat(duplicate.reason()).isEqualTo("DUPLICATE TRANSACTION");
        assertThat(overdraw.reason()).isEqualTo("INSUFFICIENT FUNDS");
        assertThat(processor.summarize(ledger).processed()).isEqualTo(3);
    }

    private BatchResult run() {
        return processor.process(accounts, transactions);
    }

    /** Adds a transaction {@code minutesLater} after the previous one. */
    private void add(String id, String type, String from, String to, String amount, int minutesLater) {
        clock = clock.plusMinutes(minutesLater);
        transactions.add(new Transaction(id, clock, TransactionType.parse(type), type, from, to,
                new BigDecimal(amount), "TEST", ""));
    }
}
