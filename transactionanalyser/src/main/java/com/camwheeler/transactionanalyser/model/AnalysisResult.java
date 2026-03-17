package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// Output of the Anthropic analysis. Contains the verdict, confidence, and reasoning.
// POJO (not a record) with no-arg constructor for Flink serialisation compatibility.
public class AnalysisResult {

    @JsonProperty("transaction_id")
    private UUID transactionId;
    @JsonProperty("filter_result")
    private FilterResult filterResult;
    private String verdict;
    private String reasoning;
    private double confidence;
    private String model;
    @JsonProperty("analysed_at")
    private String analysedAt;

    public AnalysisResult() {}

    public AnalysisResult(UUID transactionId, FilterResult filterResult, String verdict,
                          String reasoning, double confidence, String model, String analysedAt) {
        this.transactionId = transactionId;
        this.filterResult = filterResult;
        this.verdict = verdict;
        this.reasoning = reasoning;
        this.confidence = confidence;
        this.model = model;
        this.analysedAt = analysedAt;
    }

    @JsonProperty("transaction_id")
    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    @JsonProperty("filter_result")
    public FilterResult getFilterResult() { return filterResult; }
    public void setFilterResult(FilterResult filterResult) { this.filterResult = filterResult; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    @JsonProperty("analysed_at")
    public String getAnalysedAt() { return analysedAt; }
    public void setAnalysedAt(String analysedAt) { this.analysedAt = analysedAt; }
}
