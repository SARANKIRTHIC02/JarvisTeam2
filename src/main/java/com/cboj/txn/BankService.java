package com.cboj.txn;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Holds the ledger from the most recent batch so new transactions can be applied on top of it.
 * Methods are synchronized because balance updates must happen one at a time.
 */
@Service
public class BankService {

    private final CsvParser csvParser;
    private final TransactionProcessor processor;
    private final Clock clock;
    private final AtomicInteger idSequence = new AtomicInteger();
    private Ledger ledger;

    public BankService(CsvParser csvParser, TransactionProcessor processor) {
        this.csvParser = csvParser;
        this.processor = processor;
        this.clock = Clock.systemDefaultZone();
    }

    public synchronized BatchResult processBatch(InputStream accountsCsv, InputStream transactionsCsv) throws IOException {
        try (accountsCsv; transactionsCsv) {
            Ledger fresh = new Ledger(csvParser.readAccounts(accountsCsv));
            csvParser.readTransactions(transactionsCsv).forEach(tx -> processor.apply(fresh, tx));
            ledger = fresh;
            return processor.summarize(ledger);
        } catch (IllegalArgumentException e) {
            // Commons CSV throws this when an expected column is missing
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unexpected CSV format: " + e.getMessage());
        }
    }

    public synchronized NewTransactionResponse submit(NewTransactionRequest request) {
        Ledger current = requireLedger();
        Transaction tx = new Transaction(
                isBlank(request.transactionId()) ? nextId() : request.transactionId().trim(),
                request.timestamp() != null ? request.timestamp() : LocalDateTime.now(clock),
                request.type() == null ? null : TransactionType.parse(request.type()),
                request.type(),
                blankToNull(request.fromAccount()),
                blankToNull(request.toAccount()),
                request.amount(),
                isBlank(request.channel()) ? "API" : request.channel(),
                request.description());
        TransactionResult result = processor.apply(current, tx);
        return new NewTransactionResponse(result, processor.summarize(current));
    }

    public synchronized BatchResult current() {
        return processor.summarize(requireLedger());
    }

    private Ledger requireLedger() {
        if (ledger == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Process a batch first, then add transactions to it");
        }
        return ledger;
    }

    private String nextId() {
        return "TXN-%05d".formatted(idSequence.incrementAndGet());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
