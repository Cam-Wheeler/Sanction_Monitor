package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// Details of a sanctioned individual from the sanctions database.
public record SanctionInfo(
        String name,
        String nationality,
        String gender,
        String dob,
        String position,
        String sanctions,
        @JsonProperty("sanction_creator") String sanctionCreator,
        String reason,
        @JsonProperty("other_info") String otherInfo
) {}
