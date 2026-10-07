# CBOJ Transaction Engine (basic MVP)

Spring Boot app that reads an accounts CSV and a transactions CSV, approves or rejects each transaction,
flags suspicious ones for review, updates balances and prints a summary.

## Run

```bash
./mvnw spring-boot:run        # then open http://localhost:8080
./mvnw test                   # run tests
```

API:
- `POST /api/process` (multipart `accounts`, `transactions`) → JSON results
- `POST /api/process/sample` → runs the bundled sample files
- `POST /api/transactions` (JSON) → apply one new transaction on top of the last processed batch, e.g.
  `{"type":"TRANSFER","fromAccount":"ACC1001","toAccount":"ACC1002","amount":100}`.
  `transactionId` (default `TXN-00001`, …) and `timestamp` (default now) are optional.
- `GET  /api/current` → all results so far and current balances
- `GET  /api/process/sample/report` → plain-text report (`TX001 APPROVED`, `TX002 REJECTED - INVALID ACCOUNT`, …)

## Code (src/main/java/com/cboj/txn)

| File | Purpose |
|---|---|
| `CsvParser` | Reads the two CSVs into `Account`s and `Transaction`s |
| `TransactionProcessor` | All business rules + summary/report |
| `TransactionController` | REST endpoints |
| `BankService` | Keeps the latest `Ledger` so new transactions build on the batch |
| `Ledger` | Balances, seen IDs, daily totals and recent activity |
| `Account`, `Transaction`, `TransactionType`, `TransactionResult`, `BatchResult` | Data model |

## Rules

**Input:** `accounts.csv` (accountId, customerName, accountType, status, balance, dailyLimit, …) and
`transactions.csv` (transactionId, timestamp, type, fromAccount, toAccount, amount, channel, description).
PURCHASE/WITHDRAWAL debit `fromAccount`, DEPOSIT credits `toAccount`, TRANSFER does both.

**Rejected:** invalid record (unparseable amount/timestamp, e.g. `12.5O`), unsupported type (e.g. REVERSAL),
duplicate ID, unknown or missing account, inactive account (FROZEN/CLOSED/DORMANT), amount ≤ 0,
debit larger than balance.

**Flagged for review (still approved):** amount > 10,000; same-day debits over the account's
`daily_limit` (default 5,000); more than 5 transactions in 10 minutes. Thresholds live in `application.properties`.

## Data structures
- `Map<String, Account>`: O(1) account lookup
- `Set<String>`: seen transaction IDs, for duplicate detection
- `Map<String, BigDecimal>`: running debit total per account per day
- `Map<String, List<LocalDateTime>>`: recent transaction times per account, for the velocity check

## Assumptions
- Transactions are processed in file order (assumed chronological).
- A repeated transaction ID is rejected even if the first one was rejected.
- No overdraft: the balance can reach 0 but not go below it.
- The daily limit counts debits only.
- Account rows with an unreadable balance are skipped, so their transactions show as INVALID ACCOUNT.
- Type is case-insensitive (`purchase` = PURCHASE). REVERSAL is rejected as unsupported: it's unclear which
  account it should credit (a question to ask the judges).
- A transfer needs both accounts to exist and be active. Its daily-limit and velocity checks use the sending account.
- "Exceeds the threshold" means strictly greater, so exactly 10,000.00 is not flagged.
- Money uses `BigDecimal`, never `double`.
