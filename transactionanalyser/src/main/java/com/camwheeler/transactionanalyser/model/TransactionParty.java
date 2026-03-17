package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// Sender or receiver party within a transaction.
// POJO (not a record) with no-arg constructor for Flink serialisation compatibility.
public class TransactionParty {

    private String partyType;
    @JsonProperty("UID")
    private UUID uid;
    private String name;
    private String nationality;
    private UUID accountNumber;
    private String bank;
    private String location;

    public TransactionParty() {}

    public TransactionParty(String partyType, UUID uid, String name, String nationality,
                            UUID accountNumber, String bank, String location) {
        this.partyType = partyType;
        this.uid = uid;
        this.name = name;
        this.nationality = nationality;
        this.accountNumber = accountNumber;
        this.bank = bank;
        this.location = location;
    }

    public String getPartyType() { return partyType; }
    public void setPartyType(String partyType) { this.partyType = partyType; }

    @JsonProperty("UID")
    public UUID getUid() { return uid; }
    public void setUid(UUID uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public UUID getAccountNumber() { return accountNumber; }
    public void setAccountNumber(UUID accountNumber) { this.accountNumber = accountNumber; }

    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
