package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// Match result from the Transaction Filter's fuzzy name lookup against the sanctions database.
public record PartyMatch(
        @JsonProperty("party_name") String partyName,
        @JsonProperty("base_similarity") float baseSimilarity,
        @JsonProperty("nationality_boosted") boolean nationalityBoosted,
        @JsonProperty("final_score") float finalScore,
        @JsonProperty("sanction_info") SanctionInfo sanctionInfo
) {}
