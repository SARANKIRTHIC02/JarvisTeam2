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
 * This is where the business rules live.
 *
 * Each transaction goes through two stages. First the hard checks in validate(), which can reject it
 * outright. Then the review checks, which only flag it. A flagged transaction still goes through, because
 * the brief says valid transactions should be processed even if someone needs to look at them later.
 *
 * Rows are processed in the order they appear in the file. The provided file is already sorted by
 * timestamp, so we didn't add a sort.
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

    // Only the tests call this. The app goes through BankService, because it needs to keep the ledger.
    public BatchResult process(Map<String, Account> accounts, List<Transaction> transactions) {
        Ledger ledger = new Ledger(accounts);
        transactions.forEach(tx -> apply(ledger, tx));
        return summarize(ledger);
    }

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

        // Review checks. We look at the account the money leaves. Deposits don't have a "from" account,
        // so for those we use the account receiving the money.
        Account primary = tx.type().debitsFromAccount() ? from : to;
        List<String> flags = new ArrayList<>();
        if (tx.amount().compareTo(largeAmountThreshold) > 0) {
            flags.add("LARGE AMOUNT");
        }
        // The daily limit only counts money going out. A deposit shouldn't eat into someone's spending limit.
        if (tx.type().debitsFromAccount()) {
            String dayKey = from.getId() + "|" + tx.timestamp().toLocalDate();
            BigDecimal limit = from.getDailyLimit() != null ? from.getDailyLimit() : defaultDailyLimit;
            BigDecimal dayTotal = ledger.dailyDebits.getOrDefault(dayKey, BigDecimal.ZERO).add(tx.amount());
            if (dayTotal.compareTo(limit) > 0) {
                flags.add("DAILY LIMIT EXCEEDED");
            }
            ledger.dailyDebits.put(dayKey, dayTotal);
        }
        // Velocity check as a sliding window: drop anything older than the window, then count what's left
        // (including this transaction).
        List<LocalDateTime> recent = ledger.recentActivity.computeIfAbsent(primary.getId(), k -> new ArrayList<>());
        LocalDateTime windowStart = tx.timestamp().minusMinutes(velocityWindowMinutes);
        recent.removeIf(time -> !time.isAfter(windowStart));
        recent.add(tx.timestamp());
        if (recent.size() > velocityMaxTransactions) {
            flags.add("HIGH VELOCITY");
        }

        // It passed every check, so move the money. A transfer does both steps.
        if (tx.type().debitsFromAccount()) {
            from.setBalance(from.getBalance().subtract(tx.amount()));
        }
        if (tx.type().creditsToAccount()) {
            to.setBalance(to.getBalance().add(tx.amount()));
        }
        return new TransactionResult(tx, "APPROVED", null, flags, primary.getBalance());
    }

    /**
     * The hard rules. Returns the reason for the first rule that fails, or null if the transaction is fine.
     *
     * The ID is recorded as soon as the record is readable, even if a later rule rejects the transaction.
     * That way, resending a rejected transaction with the same ID still counts as a duplicate.
     */
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

    // Counts plus the plain-text report, in the same format as the example in the brief (TX001 APPROVED, ...)
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
