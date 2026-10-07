package com.cboj.txn;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final BankService bankService;

    public TransactionController(BankService bankService) {
        this.bankService = bankService;
    }

    // Loads the CSVs we ship in resources/sample and runs them through the engine
    @PostMapping("/process/sample")
    public BatchResult processSample() throws IOException {
        return bankService.processBatch(new ClassPathResource("sample/accounts.csv").getInputStream(),
                new ClassPathResource("sample/transactions.csv").getInputStream());
    }

    // Handy for the demo: the same results as plain text, matching the example output in the brief
    @GetMapping(value = "/process/sample/report", produces = MediaType.TEXT_PLAIN_VALUE)
    public String sampleReport() throws IOException {
        return processSample().report();
    }

    // Adds a single transaction on top of whichever batch was loaded last
    @PostMapping("/transactions")
    public NewTransactionResponse newTransaction(@RequestBody NewTransactionRequest request) {
        return bankService.submit(request);
    }

    // Lets the page refresh without rerunning the batch
    @GetMapping("/current")
    public BatchResult current() {
        return bankService.current();
    }
}
