package com.cboj.txn;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the transactions file. amount/timestamp are null when unparseable; type is null when unsupported.
 * Blank account columns are stored as null.
 */
public record Transaction(String id, LocalDateTime timestamp, TransactionType type, String rawType,
                          String fromAccount, String toAccount, BigDecimal amount, String channel,
                          String description) {
}
