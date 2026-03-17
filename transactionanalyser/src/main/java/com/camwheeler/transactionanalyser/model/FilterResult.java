package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// Incoming result from the Transaction Filter. Contains the transaction and any match details.
// POJO (not a record) with no-arg constructor for Flink serialisation compatibility.
public class FilterResult {

    private Transaction transaction;
    private boolean flagged;
    @JsonProperty("sender_match")
    private PartyMatch senderMatch;
    @JsonProperty("receiver_match")
    private PartyMatch receiverMatch;

    public FilterResult() {}

    public FilterResult(Transaction transaction, boolean flagged,
                        PartyMatch senderMatch, PartyMatch receiverMatch) {
        this.transaction = transaction;
        this.flagged = flagged;
        this.senderMatch = senderMatch;
        this.receiverMatch = receiverMatch;
    }

    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    @JsonProperty("sender_match")
    public PartyMatch getSenderMatch() { return senderMatch; }
    public void setSenderMatch(PartyMatch senderMatch) { this.senderMatch = senderMatch; }

    @JsonProperty("receiver_match")
    public PartyMatch getReceiverMatch() { return receiverMatch; }
    public void setReceiverMatch(PartyMatch receiverMatch) { this.receiverMatch = receiverMatch; }
}
