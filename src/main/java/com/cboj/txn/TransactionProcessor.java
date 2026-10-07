package com.cboj.txn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Applies the business rules to transactions. Transactions are processed in file order (the file is chronological).
 * Rejected transactions move no money; flagged ones are still processed.
 */
@Service
public class TransactionProcessor {

    private final BigDecimal largeAmountThreshold;
    private final BigDecimal defaultDailyLimit;
    private final int velocityMaxTransactions;
    private final int velocityWindowMinutes;

    public TransactionProcessor(@Value("${rules.large-amount-threshold}") BigDecimal largeAmountThreshold,
                                @Value("${rules.default-daily-limit}") BigDecimal defaultDailyLimit,
                                @Value("${rules.velocity-max-transactions}") int velocityMaxTransactions,
                                @Value("${rules.velocity-window-minutes}") int velocityWindowMinutes) {
        this.largeAmountThreshold = largeAmountThreshold;
        this.defaultDailyLimit = defaultDailyLimit;
        this.velocityMaxTransactions = velocityMaxTransactions;
        this.velocityWindowMinutes = velocityWindowMinutes;
    }

    /** Processes a whole batch against a fresh ledger. */
    public BatchResult process(Map<String, Account> accounts, List<Transaction> transactions) {
        Ledger ledger = new Ledger(accounts);
        transactions.forEach(tx -> apply(ledger, tx));
        return summarize(ledger);
    }

    /** Validates, reviews and applies one transaction, recording the result in the ledger. */
    public TransactionResult apply(Ledger ledger, Transaction tx) {
        TransactionResult result = evaluate(ledger, tx);
        ledger.results.add(result);
        return result;
    }

    private TransactionResult evaluate(Ledger ledger, Transaction tx) {
        Account from = tx.fromAccount() == null ? null : ledger.accounts.get(tx.fromAccount());
        Account to = tx.toAccount() == null ? null : ledger.accounts.get(tx.toAccount());

        String rejection = validate(tx, from, to, ledger.seenIds);
        if (rejection != null) {
            return new TransactionResult(tx, "REJECTED", rejection, List.of(), null);
        }

        // Review checks (against the account the money leaves; for deposits, the receiving account)
        Account primary = tx.type().debitsFromAccount() ? from : to;
        List<String> flags = new ArrayList<>();
        if (tx.amount().compareTo(largeAmountThreshold) > 0) {
            flags.add("LARGE AMOUNT");
        }
        if (tx.type().debitsFromAccount()) {
            String dayKey = from.getId() + "|" + tx.timestamp().toLocalDate();
            BigDecimal limit = from.getDailyLimit() != null ? from.getDailyLimit() : defaultDailyLimit;
            BigDecimal dayTotal = ledger.dailyDebits.getOrDefault(dayKey, BigDecimal.ZERO).add(tx.amount());
            if (dayTotal.compareTo(limit) > 0) {
                flags.add("DAILY LIMIT EXCEEDED");
            }
            ledger.dailyDebits.put(dayKey, dayTotal);
        }
        List<LocalDateTime> recent = ledger.recentActivity.computeIfAbsent(primary.getId(), k -> new ArrayList<>());
        LocalDateTime windowStart = tx.timestamp().minusMinutes(velocityWindowMinutes);
        recent.removeIf(time -> !time.isAfter(windowStart));
        recent.add(tx.timestamp());
        if (recent.size() > velocityMaxTransactions) {
            flags.add("HIGH VELOCITY");
        }

        // Move the money
        if (tx.type().debitsFromAccount()) {
            from.setBalance(from.getBalance().subtract(tx.amount()));
        }
        if (tx.type().creditsToAccount()) {
            to.setBalance(to.getBalance().add(tx.amount()));
        }
        return new TransactionResult(tx, "APPROVED", null, flags, primary.getBalance());
    }

    /** Returns the rejection reason, or null if the transaction is valid. */
    private String validate(Transaction tx, Account from, Account to, Set<String> seenIds) {
        if (tx.amount() == null || tx.timestamp() == null) {
            return "INVALID RECORD";
        }
        if (tx.type() == null) {
            return "UNSUPPORTED TRANSACTION TYPE";
        }
        if (!seenIds.add(tx.id())) {
            return "DUPLICATE TRANSACTION";
        }
        if ((tx.type().debitsFromAccount() && from == null) || (tx.type().creditsToAccount() && to == null)) {
            return "INVALID ACCOUNT";
        }
        if ((from != null && !from.isActive()) || (to != null && !to.isActive())) {
            return "INACTIVE ACCOUNT";
        }
        if (tx.amount().signum() <= 0) {
            return "INVALID AMOUNT";
        }
        if (tx.type().debitsFromAccount() && from.getBalance().compareTo(tx.amount()) < 0) {
            return "INSUFFICIENT FUNDS";
        }
        return null;
    }

    public BatchResult summarize(Ledger ledger) {
        List<TransactionResult> results = List.copyOf(ledger.results);
        int approved = (int) results.stream().filter(r -> r.status().equals("APPROVED")).count();
        List<TransactionResult> flagged = results.stream().filter(TransactionResult::isFlagged).toList();

        StringBuilder report = new StringBuilder();
        for (TransactionResult r : results) {
            report.append(r.transaction().id()).append(' ').append(r.status());
            if (r.reason() != null) {
                report.append(" - ").append(r.reason());
            }
            report.append('\n');
        }
        report.append("\nTransactions Processed: ").append(results.size())
                .append("\nApproved: ").append(approved)
                .append("\nRejected: ").append(results.size() - approved)
                .append("\nFlagged For Review: ").append(flagged.size())
                .append("\n\nFLAGGED TRANSACTIONS\n");
        for (TransactionResult r : flagged) {
            Transaction t = r.transaction();
            String account = t.fromAccount() != null ? t.fromAccount() : t.toAccount();
            report.append(t.id()).append(' ').append(account).append(' ').append(t.amount())
                    .append(" - ").append(String.join(", ", r.flags())).append('\n');
        }

        return new BatchResult(results.size(), approved, results.size() - approved, flagged.size(),
                results, flagged, List.copyOf(ledger.accounts.values()), report.toString());
    }
}
