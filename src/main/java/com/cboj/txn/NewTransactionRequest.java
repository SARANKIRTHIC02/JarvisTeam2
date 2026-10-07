package com.cboj.txn;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** JSON body for a single new transaction. transactionId and timestamp are optional. */
public record NewTransactionRequest(String transactionId, String type, String fromAccount, String toAccount,
                                    BigDecimal amount, String channel, String description,
                                    LocalDateTime timestamp) {
}
