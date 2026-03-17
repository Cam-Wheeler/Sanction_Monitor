package com.camwheeler.transactionanalyser.prompt;

import com.camwheeler.transactionanalyser.model.FilterResult;
import com.camwheeler.transactionanalyser.model.PartyMatch;
import com.camwheeler.transactionanalyser.model.SanctionInfo;
import com.camwheeler.transactionanalyser.model.Transaction;

import java.util.List;

public final class PromptBuilder {
    // Utility class for constructing the compliance analyst prompt sent to Claude.

    private PromptBuilder() {}

    /*
    Builds the full prompt string for a flagged transaction.
    Includes transaction details, match scores, sanctioned individual records,
    and instructions for the expected JSON response format.
    */
    public static String buildPrompt(FilterResult result) {
        Transaction tx = result.getTransaction();
        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are a financial compliance analyst. A transaction has been flagged by an \
                automated screening system as potentially involving a sanctioned individual.

                Analyse the following information and determine whether the flagged party in \
                the transaction is likely the same person as the sanctioned individual on record.

                ## Transaction Details
                """);

        sb.append("- Transaction ID: ").append(tx.getTransactionId()).append("\n");
        sb.append("- Amount: ").append(String.format("%.2f", tx.getAmount())).append("\n");
        sb.append("- Date: ").append(tx.getDate()).append(" ").append(tx.getTime()).append("\n");
        sb.append("- Type: ").append(tx.getType()).append("\n");
        sb.append("- Sender: ").append(tx.getSender().getName())
                .append(", nationality: ").append(tx.getSender().getNationality())
                .append(", bank: ").append(tx.getSender().getBank())
                .append(", location: ").append(tx.getSender().getLocation()).append("\n");
        sb.append("- Receiver: ").append(tx.getReceiver().getName())
                .append(", nationality: ").append(tx.getReceiver().getNationality())
                .append(", bank: ").append(tx.getReceiver().getBank())
                .append(", location: ").append(tx.getReceiver().getLocation()).append("\n");

        if (result.getSenderMatch() != null) {
            sb.append("\n");
            appendMatchSection(sb, "Sender", result.getSenderMatch());
        }

        if (result.getReceiverMatch() != null) {
            sb.append("\n");
            appendMatchSection(sb, "Receiver", result.getReceiverMatch());
        }

        appendInstructions(sb);

        return sb.toString();
    }

    // Builds the prompt with recent flagged activity history for the same party.
    public static String buildPrompt(FilterResult result, List<FilterResult> recentHistory) {
        Transaction tx = result.getTransaction();
        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are a financial compliance analyst. A transaction has been flagged by an \
                automated screening system as potentially involving a sanctioned individual.

                Analyse the following information and determine whether the flagged party in \
                the transaction is likely the same person as the sanctioned individual on record.

                ## Transaction Details
                """);

        sb.append("- Transaction ID: ").append(tx.getTransactionId()).append("\n");
        sb.append("- Amount: ").append(String.format("%.2f", tx.getAmount())).append("\n");
        sb.append("- Date: ").append(tx.getDate()).append(" ").append(tx.getTime()).append("\n");
        sb.append("- Type: ").append(tx.getType()).append("\n");
        sb.append("- Sender: ").append(tx.getSender().getName())
                .append(", nationality: ").append(tx.getSender().getNationality())
                .append(", bank: ").append(tx.getSender().getBank())
                .append(", location: ").append(tx.getSender().getLocation()).append("\n");
        sb.append("- Receiver: ").append(tx.getReceiver().getName())
                .append(", nationality: ").append(tx.getReceiver().getNationality())
                .append(", bank: ").append(tx.getReceiver().getBank())
                .append(", location: ").append(tx.getReceiver().getLocation()).append("\n");

        if (result.getSenderMatch() != null) {
            sb.append("\n");
            appendMatchSection(sb, "Sender", result.getSenderMatch());
        }

        if (result.getReceiverMatch() != null) {
            sb.append("\n");
            appendMatchSection(sb, "Receiver", result.getReceiverMatch());
        }

        sb.append("\n## Recent Flagged Activity\n");
        if (recentHistory == null || recentHistory.isEmpty()) {
            sb.append("No previous flagged transactions found for this individual.\n");
        } else {
            sb.append("The following recent transactions were also flagged for the same individual:\n\n");
            for (FilterResult hist : recentHistory) {
                Transaction htx = hist.getTransaction();
                float score = 0f;
                if (hist.getSenderMatch() != null) {
                    score = Math.max(score, hist.getSenderMatch().getFinalScore());
                }
                if (hist.getReceiverMatch() != null) {
                    score = Math.max(score, hist.getReceiverMatch().getFinalScore());
                }
                sb.append("- ID: ").append(htx.getTransactionId())
                        .append(", Date: ").append(htx.getDate()).append(" ").append(htx.getTime())
                        .append(", Amount: ").append(String.format("%.2f", htx.getAmount()))
                        .append(", Type: ").append(htx.getType())
                        .append(", ").append(htx.getSender().getName()).append(" → ").append(htx.getReceiver().getName())
                        .append(", Match score: ").append(String.format("%.4f", score))
                        .append("\n");
            }
        }

        appendInstructions(sb);

        return sb.toString();
    }

    private static void appendInstructions(StringBuilder sb) {
        sb.append("""

                ## Instructions
                Consider the name similarity score, nationality match, transaction context (amount, type, \
                location), and any other available information about the sanctioned individual to assess \
                whether this is likely the same person. Also consider any recent flagged activity patterns \
                (repeated flagging, escalating amounts, geographic patterns) if available.

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
    }

    // Appends the match details and sanctioned individual record for a flagged party.
    private static void appendMatchSection(StringBuilder sb, String party, PartyMatch match) {
        sb.append("## Flagged ").append(party).append(" Match\n");
        sb.append("- Party name in transaction: ").append(match.getPartyName()).append("\n");
        sb.append("- Name similarity score: ").append(String.format("%.4f", match.getBaseSimilarity())).append("\n");
        sb.append("- Nationality match boost applied: ").append(match.isNationalityBoosted()).append("\n");
        sb.append("- Final score: ").append(String.format("%.4f", match.getFinalScore())).append("\n");

        SanctionInfo info = match.getSanctionInfo();
        sb.append("\n### Sanctioned Individual Record\n");
        sb.append("- Name: ").append(info.getName()).append("\n");
        appendIfPresent(sb, "Nationality", info.getNationality());
        appendIfPresent(sb, "Gender", info.getGender());
        appendIfPresent(sb, "Date of Birth", info.getDob());
        appendIfPresent(sb, "Position", info.getPosition());
        appendIfPresent(sb, "Sanctions imposed", info.getSanctions());
        appendIfPresent(sb, "Reason", info.getReason());
        appendIfPresent(sb, "Other info", info.getOtherInfo());
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }
}
