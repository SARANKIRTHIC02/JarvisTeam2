package com.cboj.txn;

import java.math.BigDecimal;

public class Account {

    private final String id;
    private final String customerName;
    private final String status;
    private final BigDecimal dailyLimit;
    private final BigDecimal openingBalance;
    private BigDecimal balance;

    public Account(String id, String customerName, BigDecimal balance, String status, BigDecimal dailyLimit) {
        this.id = id;
        this.customerName = customerName;
        this.openingBalance = balance;
        this.balance = balance;
        this.status = status;
        this.dailyLimit = dailyLimit;
    }

    /** Only ACTIVE accounts can transact; FROZEN, CLOSED and DORMANT cannot. */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public String getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getStatus() { return status; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
