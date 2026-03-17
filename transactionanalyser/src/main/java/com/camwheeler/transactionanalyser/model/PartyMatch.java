package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// Match result from the Transaction Filter's fuzzy name lookup against the sanctions database.
// POJO (not a record) with no-arg constructor for Flink serialisation compatibility.
public class PartyMatch {

    @JsonProperty("party_name")
    private String partyName;
    @JsonProperty("base_similarity")
    private float baseSimilarity;
    @JsonProperty("nationality_boosted")
    private boolean nationalityBoosted;
    @JsonProperty("final_score")
    private float finalScore;
    @JsonProperty("sanction_info")
    private SanctionInfo sanctionInfo;

    public PartyMatch() {}

    public PartyMatch(String partyName, float baseSimilarity, boolean nationalityBoosted,
                      float finalScore, SanctionInfo sanctionInfo) {
        this.partyName = partyName;
        this.baseSimilarity = baseSimilarity;
        this.nationalityBoosted = nationalityBoosted;
        this.finalScore = finalScore;
        this.sanctionInfo = sanctionInfo;
    }

    @JsonProperty("party_name")
    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    @JsonProperty("base_similarity")
    public float getBaseSimilarity() { return baseSimilarity; }
    public void setBaseSimilarity(float baseSimilarity) { this.baseSimilarity = baseSimilarity; }

    @JsonProperty("nationality_boosted")
    public boolean isNationalityBoosted() { return nationalityBoosted; }
    public void setNationalityBoosted(boolean nationalityBoosted) { this.nationalityBoosted = nationalityBoosted; }

    @JsonProperty("final_score")
    public float getFinalScore() { return finalScore; }
    public void setFinalScore(float finalScore) { this.finalScore = finalScore; }

    @JsonProperty("sanction_info")
    public SanctionInfo getSanctionInfo() { return sanctionInfo; }
    public void setSanctionInfo(SanctionInfo sanctionInfo) { this.sanctionInfo = sanctionInfo; }
}
