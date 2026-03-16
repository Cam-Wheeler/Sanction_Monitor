package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// Incoming result from the Transaction Filter. Contains the transaction and any match details.
public record FilterResult(
        Transaction transaction,
        boolean flagged,
        @JsonProperty("sender_match") PartyMatch senderMatch,
        @JsonProperty("receiver_match") PartyMatch receiverMatch
) {}
