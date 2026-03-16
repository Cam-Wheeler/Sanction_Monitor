package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// Mirrors the Transaction structure from the Transaction Generator.
public record Transaction(
        UUID transactionId,
        double amount,
        String date,
        String time,
        @JsonProperty("type") String type,
        TransactionParty sender,
        TransactionParty receiver
) {}
