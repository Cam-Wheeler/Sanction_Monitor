package com.camwheeler.transactionanalyser.function;

import com.camwheeler.transactionanalyser.model.FilterResult;
import org.apache.flink.api.java.functions.KeySelector;

// Keys FilterResult by the UID of the flagged party. When both parties are flagged,
// keys by the higher-scoring match.
public class FlaggedPartyKeySelector implements KeySelector<FilterResult, String> {

    @Override
    public String getKey(FilterResult value) {
        boolean hasSender = value.getSenderMatch() != null;
        boolean hasReceiver = value.getReceiverMatch() != null;

        if (hasSender && hasReceiver) {
            if (value.getSenderMatch().getFinalScore() >= value.getReceiverMatch().getFinalScore()) {
                return value.getTransaction().getSender().getUid().toString();
            } else {
                return value.getTransaction().getReceiver().getUid().toString();
            }
        } else if (hasSender) {
            return value.getTransaction().getSender().getUid().toString();
        } else {
            return value.getTransaction().getReceiver().getUid().toString();
        }
    }
}
