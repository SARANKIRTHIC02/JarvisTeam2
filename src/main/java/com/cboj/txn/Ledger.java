package com.cboj.txn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** State that carries over between transactions: balances, seen IDs, daily totals and recent activity. */
public class Ledger {

    final Map<String, Account> accounts;
    final Set<String> seenIds = new HashSet<>();                              // duplicate detection
    final Map<String, BigDecimal> dailyDebits = new HashMap<>();              // "accountId|date" -> total out
    final Map<String, List<LocalDateTime>> recentActivity = new HashMap<>();  // accountId -> recent tx times
    final List<TransactionResult> results = new ArrayList<>();

    public Ledger(Map<String, Account> accounts) {
        this.accounts = accounts;
    }
}
