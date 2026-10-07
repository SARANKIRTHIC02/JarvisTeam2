package com.cboj.txn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything we need to remember from one transaction to the next. It's all in one place so a batch and
 * any transactions added afterwards share the same state.
 */
public class Ledger {

    final Map<String, Account> accounts;
    final Set<String> seenIds = new HashSet<>();                              // every ID we've seen, to catch duplicates
    final Map<String, BigDecimal> dailyDebits = new HashMap<>();              // "ACC1001|2026-10-15" -> money out that day
    final Map<String, List<LocalDateTime>> recentActivity = new HashMap<>();  // recent timestamps per account, for velocity
    final List<TransactionResult> results = new ArrayList<>();

    public Ledger(Map<String, Account> accounts) {
        this.accounts = accounts;
    }
}
