package com.cboj.txn;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final BankService bankService;

    public TransactionController(BankService bankService) {
        this.bankService = bankService;
    }

    /** Upload accounts.csv and transactions.csv, get back the processing results. */
    @PostMapping("/process")
    public BatchResult process(@RequestParam MultipartFile accounts,
                               @RequestParam MultipartFile transactions) throws IOException {
        return bankService.processBatch(accounts.getInputStream(), transactions.getInputStream());
    }

    /** Process the sample files bundled with the app. */
    @PostMapping("/process/sample")
    public BatchResult processSample() throws IOException {
        return bankService.processBatch(new ClassPathResource("sample/accounts.csv").getInputStream(),
                new ClassPathResource("sample/transactions.csv").getInputStream());
    }

    /** Plain-text report for the sample files, in the format from the requirements. */
    @GetMapping(value = "/process/sample/report", produces = MediaType.TEXT_PLAIN_VALUE)
    public String sampleReport() throws IOException {
        return processSample().report();
    }

    /** Apply one new transaction to the most recently processed batch. */
    @PostMapping("/transactions")
    public NewTransactionResponse newTransaction(@RequestBody NewTransactionRequest request) {
        return bankService.submit(request);
    }

    /** Current state: all results so far (batch + new transactions) and balances. */
    @GetMapping("/current")
    public BatchResult current() {
        return bankService.current();
    }
}
