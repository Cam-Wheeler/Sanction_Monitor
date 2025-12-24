package com.camwheeler.transactiongenerator.services;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;

import com.camwheeler.transactiongenerator.model.Person;
import com.camwheeler.transactiongenerator.model.Transaction;
import com.camwheeler.transactiongenerator.model.TransactionParty;

@Service
public class GeneratorService {
    // Service class for generating transactions.

    @Value("${data.sanction.json}")
    private Resource sanctionDataPath;
    
    @Value("${data.valid.json}")
    private Resource validDataPath;

    private ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private double sanctionProb = 2;

    private int maxWaitBetweenTransactions = 100;

    private List<Person> sanctionedEntities;
    private List<Person> validEntities;

    private List<String> countries = List.of(
    "UnitedKingdom",
    "UnitedStates",
    "Russia",
    "China",
    "Syria",
    "India",
    "Iran",
    "France",
    "Germany",
    "Spain",
    "Italy",
    "Portugal"
    );

    private List<String> banks = List.of(
    "BankOfEngland",
    "JPMorganChase",
    "HSBC",
    "StandardChartered",
    "Citibank",
    "DeutscheBank",
    "SocieteGenerale",
    "UniCredit",
    "Raiffeisen",
    "Santander",
    "CaixaBank",
    "BancoSabadell"
    );

private List<String> transactionTypes = List.of(
    "Cash Deposit",
    "Cash Withdrawal",
    "Wire Transfer",
    "Credit Card Purchase",
    "Debit Card Transaction",
    "Check Deposit",
    "ATM Transaction",
    "Cryptocurrency Transfer"
);

    /*
    Main method utilised for generating the dummy transactions.
    Will generate transactions with a break of 0 -> N seconds between each transaction.
    Will place sanctioned entities within the transaction at a probability of x%.
    Sanctions individuals can be the sender, receiver or both.

    Usage: Placed in the controller and will start indefinitely when the start endpoint
    is hit until stopped by hitting the stop endpoint.
    */
    public void generateTransactions(int iterations) {
        // Logic to generate transactions.
        collectDataFromJson();
        int i = 0;
        do {
            TransactionParty sender = generateSenderOrReceiver("Sender");
            TransactionParty receiver = generateSenderOrReceiver("Receiver");
            Transaction transaction = generateSingleTransaction(sender, receiver);
            System.out.println(transaction);
            
            // Just delay a small amount each transaction. 
            long wait = (long)(Math.random() * maxWaitBetweenTransactions);
            try {
                Thread.sleep(wait);
            } 
            catch (Exception e) {
                continue;
            }
            i++;
            
        } while (i <= iterations);
    }

    public void collectDataFromJson() {
        // Collects data from JSON files.
        sanctionedEntities = getPeople(sanctionDataPath);
        validEntities = getPeople(validDataPath);
    }

    private List<Person> getPeople(Resource jsonPath) {
        // Reads in the sanctioned json file.
        try {
            List<Person> sanctionedEntities = mapper.readValue(
                jsonPath.getInputStream(),
                new TypeReference<List<Person>>(){}
            );
            return sanctionedEntities;
        } catch (Exception e) {
            System.out.println("This is where we are going wrong");
            e.printStackTrace();
            return null;
        }
    }

    private TransactionParty generateSenderOrReceiver(String typeToGen) {
        // Create either a sender or receiver of the transaction.
        UUID uuid = UUID.randomUUID();
        Person person = (Math.random() <= sanctionProb) ? getRandomPerson(sanctionedEntities) : getRandomPerson(validEntities);
        UUID accountId = UUID.randomUUID();
        String country = countries.get((int)(Math.random() * countries.size()));
        String bank = banks.get((int)(Math.random() * banks.size()));

        if (typeToGen == "Sender") {
            return new TransactionParty(
                typeToGen,
                uuid,
                person.name(),
                person.nationality(),
                accountId,
                bank, 
                country
            );
        } else {
            return new TransactionParty(
                typeToGen,
                uuid,
                person.name(),
                person.nationality(),
                accountId,
                bank, 
                country
            );
        }
    }

    private Transaction generateSingleTransaction(TransactionParty sender, TransactionParty receiver) {
        // Generates a transaction.
        UUID transactionId = UUID.randomUUID();
        double amount = Math.random() * (Math.random() * 10000);
        LocalDate currentDate = LocalDate.now();
        LocalTime currentTime = LocalTime.now();
        String transactionType = transactionTypes.get((int)(Math.random() * transactionTypes.size()));

        return new Transaction(
            transactionId,
            amount,
            currentDate,
            currentTime,
            transactionType,
            sender,
            receiver
        );
    }

    private Person getRandomPerson(List<Person> people) {
        // Selects a random person from a list of people.
        return people.get((int) (Math.random() * people.size()));
    }
}
