package com.camwheeler.transactiongenerator.services;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

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

    private List<Person> sanctionedEntities;
    private List<Person> validEntities;

    @PostConstruct // Required so Spring does not error when constructing the bean! 
    public void init() {
        this.sanctionedEntities = getPeople(sanctionDataPath);
        this.validEntities = getPeople(validDataPath);
    }

    private ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private float sanctionProb = 0.2f;

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
    "Wire Transfer",
    "Debit Card Transaction",
    "Check Deposit",
    "Cryptocurrency Transfer"
);

    /*
    Main method utilised for generating the dummy transactions.
    Will generate a single transaction. Will place sanctioned entities within
    the transaction at a probability of sanctionProb.
    Sanctions individuals can be the sender, receiver or both.

    Usage: Placed inside of the kafka producer so we can compute transactions on the fly
           and send them off to the topic. 
    */
    public Transaction generateTransaction() throws RuntimeException{
        // Logic to generate transactions.
        TransactionParty sender = generateSenderOrReceiver("Sender");
        TransactionParty receiver = generateSenderOrReceiver("Receiver");
        Transaction transaction = generateSingleTransaction(sender, receiver);
        return transaction;
    }

    private TransactionParty generateSenderOrReceiver(String type) {
        UUID uuid = UUID.randomUUID();
        Person person = (Math.random() <= sanctionProb) ? getRandomPerson(sanctionedEntities) : getRandomPerson(validEntities);
        UUID accountId = UUID.randomUUID();
        String country = countries.get((int)(Math.random() * countries.size()));
        String bank = banks.get((int)(Math.random() * banks.size()));

        return new TransactionParty(type, uuid, person.name(), person.nationality(), accountId, bank, country);
    }

    private Transaction generateSingleTransaction(TransactionParty sender, TransactionParty receiver) {
        // Generates a transaction.
        UUID transactionId = UUID.randomUUID();
        double amount = Math.random() * (Math.random() * 10000);
        String currentDate = LocalDate.now().toString();
        String currentTime = LocalTime.now().toString();
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
    
    private List<Person> getPeople(Resource jsonPath) {
        // Reads in the sanctioned json file.
        try {
            List<Person> sanctionedEntities = mapper.readValue(
                jsonPath.getInputStream(),
                new TypeReference<List<Person>>(){}
            );
            return sanctionedEntities;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load people data from: " + jsonPath, e);
        }
    }
}
