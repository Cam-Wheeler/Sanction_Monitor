package com.camwheeler.transactiongenerator.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.camwheeler.transactiongenerator.services.ProducerService;

@RestController
public class MainController {
    /*
    Main controller class for the generator service. 

    Endpoints:
        - /start: Generates n new transactions.
    */
    private final ProducerService producerService;

    public MainController(ProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/start/{n}")
    public String start(@PathVariable("n") int iterations) {
        producerService.produceTransactions(iterations);
        return "Transactions generated";
    }

    @PostMapping("/test-sequence")
    public String testSequence() {
        producerService.produceTestSequence();
        return "Test sequence generated: 3 small + 1 large transaction from the same sanctioned individual";
    }
}
