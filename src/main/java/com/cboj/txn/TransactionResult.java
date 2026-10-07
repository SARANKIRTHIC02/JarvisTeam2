package com.cboj.txn;

import java.math.BigDecimal;
import java.util.List;

/**
 * status is APPROVED or REJECTED. reason is only filled in for rejections, and flags holds whatever
 * the review checks raised.
 */
public record TransactionResult(Transaction transaction, String status, String reason, List<String> flags,
                                BigDecimal balanceAfter) {

    public boolean isFlagged() {
        return !flags.isEmpty();
    }
}
