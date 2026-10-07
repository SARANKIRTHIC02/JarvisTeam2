package com.cboj.txn;

public enum TransactionType {
    PURCHASE,    // money out of fromAccount
    WITHDRAWAL,  // money out of fromAccount
    DEPOSIT,     // money into toAccount
    TRANSFER;    // fromAccount -> toAccount

    public boolean debitsFromAccount() {
        return this != DEPOSIT;
    }

    public boolean creditsToAccount() {
        return this == DEPOSIT || this == TRANSFER;
    }

    /** Case-insensitive; returns null for unsupported types (e.g. REVERSAL). */
    public static TransactionType parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
