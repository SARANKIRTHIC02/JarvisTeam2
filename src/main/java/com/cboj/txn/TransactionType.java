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

    // Case-insensitive, because the file has a lowercase "purchase" in it. Anything we don't recognise
    // (like REVERSAL) comes back null and gets rejected. We weren't sure which account a reversal should credit.
    public static TransactionType parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
