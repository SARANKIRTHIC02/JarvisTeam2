package com.cboj.txn;

import java.util.List;

public record BatchResult(int processed, int approved, int rejected, int flaggedForReview,
                          List<TransactionResult> results, List<TransactionResult> flagged,
                          List<Account> accounts, String report) {
}
