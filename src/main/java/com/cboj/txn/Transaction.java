package com.cboj.txn;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row from the transactions file. Anything we couldn't parse is left as null, so the processor can
 * reject it with a proper reason instead of the parser throwing. Blank account columns are null too.
 */
public record Transaction(String id, LocalDateTime timestamp, TransactionType type, String rawType,
                          String fromAccount, String toAccount, BigDecimal amount, String channel,
                          String description) {
}
