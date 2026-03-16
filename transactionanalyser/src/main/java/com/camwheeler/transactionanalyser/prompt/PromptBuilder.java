package com.camwheeler.transactionanalyser.prompt;

import com.camwheeler.transactionanalyser.model.FilterResult;
import com.camwheeler.transactionanalyser.model.PartyMatch;
import com.camwheeler.transactionanalyser.model.SanctionInfo;
import com.camwheeler.transactionanalyser.model.Transaction;

public final class PromptBuilder {
    // Utility class for constructing the compliance analyst prompt sent to Claude.

    private PromptBuilder() {}

    /*
    Builds the full prompt string for a flagged transaction.
    Includes transaction details, match scores, sanctioned individual records,
    and instructions for the expected JSON response format.
    */
    public static String buildPrompt(FilterResult result) {
        Transaction tx = result.transaction();
        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are a financial compliance analyst. A transaction has been flagged by an \
                automated screening system as potentially involving a sanctioned individual.

                Analyse the following information and determine whether the flagged party in \
                the transaction is likely the same person as the sanctioned individual on record.

                ## Transaction Details
                """);

        sb.append("- Transaction ID: ").append(tx.transactionId()).append("\n");
        sb.append("- Amount: ").append(String.format("%.2f", tx.amount())).append("\n");
        sb.append("- Date: ").append(tx.date()).append(" ").append(tx.time()).append("\n");
        sb.append("- Type: ").append(tx.type()).append("\n");
        sb.append("- Sender: ").append(tx.sender().name())
                .append(", nationality: ").append(tx.sender().nationality())
                .append(", bank: ").append(tx.sender().bank())
                .append(", location: ").append(tx.sender().location()).append("\n");
        sb.append("- Receiver: ").append(tx.receiver().name())
                .append(", nationality: ").append(tx.receiver().nationality())
                .append(", bank: ").append(tx.receiver().bank())
                .append(", location: ").append(tx.receiver().location()).append("\n");

        if (result.senderMatch() != null) {
            sb.append("\n");
            appendMatchSection(sb, "Sender", result.senderMatch());
        }

        if (result.receiverMatch() != null) {
            sb.append("\n");
            appendMatchSection(sb, "Receiver", result.receiverMatch());
        }

        sb.append("""

                ## Instructions
                Consider the name similarity score, nationality match, transaction context (amount, type, \
                location), and any other available information about the sanctioned individual to assess \
                whether this is likely the same person.

                Respond with ONLY a JSON object in exactly this format:
                {
                  "verdict": "CONFIRMED" | "POSSIBLE" | "CLEARED",
                  "confidence": <number between 0.0 and 1.0>,
                  "reasoning": "<your detailed analysis>"
                }

                Verdict definitions:
                - CONFIRMED: the flagged party is very likely the sanctioned individual
                - POSSIBLE: there is meaningful overlap but not enough to be certain
                - CLEARED: the flagged party is unlikely to be the sanctioned individual
                """);

        return sb.toString();
    }

    // Appends the match details and sanctioned individual record for a flagged party.
    private static void appendMatchSection(StringBuilder sb, String party, PartyMatch match) {
        sb.append("## Flagged ").append(party).append(" Match\n");
        sb.append("- Party name in transaction: ").append(match.partyName()).append("\n");
        sb.append("- Name similarity score: ").append(String.format("%.4f", match.baseSimilarity())).append("\n");
        sb.append("- Nationality match boost applied: ").append(match.nationalityBoosted()).append("\n");
        sb.append("- Final score: ").append(String.format("%.4f", match.finalScore())).append("\n");

        SanctionInfo info = match.sanctionInfo();
        sb.append("\n### Sanctioned Individual Record\n");
        sb.append("- Name: ").append(info.name()).append("\n");
        appendIfPresent(sb, "Nationality", info.nationality());
        appendIfPresent(sb, "Gender", info.gender());
        appendIfPresent(sb, "Date of Birth", info.dob());
        appendIfPresent(sb, "Position", info.position());
        appendIfPresent(sb, "Sanctions imposed", info.sanctions());
        appendIfPresent(sb, "Reason", info.reason());
        appendIfPresent(sb, "Other info", info.otherInfo());
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }
}
