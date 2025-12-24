package com.camwheeler.transactiongenerator.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.camwheeler.transactiongenerator.services.GeneratorService;

@RestController
public class MainController {
    /*
    Main controller class for the generator service. 

    Endpoints:
        - /start: Generates n new transactions.
    */
    private final GeneratorService generatorService;

    public MainController(GeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @PostMapping("/start/{n}")
    public String start(@PathVariable("n") int iterations) {
        // Starts the transaction generation process.
        generatorService.generateTransactions(iterations);
        return "Transactions generated";
    } 
}
