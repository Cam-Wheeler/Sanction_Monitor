package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// Mirrors the Transaction structure from the Transaction Generator.
// POJO (not a record) with no-arg constructor for Flink serialisation compatibility.
public class Transaction {

    private UUID transactionId;
    private double amount;
    private String date;
    private String time;
    @JsonProperty("type")
    private String type;
    private TransactionParty sender;
    private TransactionParty receiver;

    public Transaction() {}

    public Transaction(UUID transactionId, double amount, String date, String time,
                       String type, TransactionParty sender, TransactionParty receiver) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.date = date;
        this.time = time;
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
    }

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    @JsonProperty("type")
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public TransactionParty getSender() { return sender; }
    public void setSender(TransactionParty sender) { this.sender = sender; }

    public TransactionParty getReceiver() { return receiver; }
    public void setReceiver(TransactionParty receiver) { this.receiver = receiver; }
}
