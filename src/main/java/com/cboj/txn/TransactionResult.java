package com.cboj.txn;

import java.math.BigDecimal;
import java.util.List;

/** status is APPROVED or REJECTED; reason is set only when rejected; flags lists review reasons. */
public record TransactionResult(Transaction transaction, String status, String reason, List<String> flags,
                                BigDecimal balanceAfter) {

    public boolean isFlagged() {
        return !flags.isEmpty();
    }
}
