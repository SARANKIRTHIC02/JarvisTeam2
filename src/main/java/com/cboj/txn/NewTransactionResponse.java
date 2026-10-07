package com.cboj.txn;

// The result for the new transaction, plus refreshed totals so the page can redraw everything at once
public record NewTransactionResponse(TransactionResult result, BatchResult batch) {
}
