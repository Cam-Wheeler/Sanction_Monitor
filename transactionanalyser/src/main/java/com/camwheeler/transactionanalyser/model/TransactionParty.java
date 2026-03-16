package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// Sender or receiver party within a transaction.
public record TransactionParty(
        String partyType,
        @JsonProperty("UID") UUID uid,
        String name,
        String nationality,
        UUID accountNumber,
        String bank,
        String location
) {}
