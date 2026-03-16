package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// Output of the Anthropic analysis. Contains the verdict, confidence, and reasoning.
public record AnalysisResult(
        @JsonProperty("transaction_id") UUID transactionId,
        @JsonProperty("filter_result") FilterResult filterResult,
        String verdict,
        String reasoning,
        double confidence,
        String model,
        @JsonProperty("analysed_at") String analysedAt
) {}
