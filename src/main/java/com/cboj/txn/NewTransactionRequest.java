package com.cboj.txn;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// What the "New transaction" form sends. transactionId and timestamp can be left out.
public record NewTransactionRequest(String transactionId, String type, String fromAccount, String toAccount,
                                    BigDecimal amount, String channel, String description,
                                    LocalDateTime timestamp) {
}
