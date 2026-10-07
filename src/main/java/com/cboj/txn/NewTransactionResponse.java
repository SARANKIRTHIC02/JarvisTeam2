package com.cboj.txn;

/** The outcome of the new transaction plus the updated batch totals. */
public record NewTransactionResponse(TransactionResult result, BatchResult batch) {
}
