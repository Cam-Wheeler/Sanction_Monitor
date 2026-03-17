package com.camwheeler.transactionanalyser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// Details of a sanctioned individual from the sanctions database.
// POJO (not a record) with no-arg constructor for Flink serialisation compatibility.
public class SanctionInfo {

    private String name;
    private String nationality;
    private String gender;
    private String dob;
    private String position;
    private String sanctions;
    @JsonProperty("sanction_creator")
    private String sanctionCreator;
    private String reason;
    @JsonProperty("other_info")
    private String otherInfo;

    public SanctionInfo() {}

    public SanctionInfo(String name, String nationality, String gender, String dob,
                        String position, String sanctions, String sanctionCreator,
                        String reason, String otherInfo) {
        this.name = name;
        this.nationality = nationality;
        this.gender = gender;
        this.dob = dob;
        this.position = position;
        this.sanctions = sanctions;
        this.sanctionCreator = sanctionCreator;
        this.reason = reason;
        this.otherInfo = otherInfo;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getSanctions() { return sanctions; }
    public void setSanctions(String sanctions) { this.sanctions = sanctions; }

    @JsonProperty("sanction_creator")
    public String getSanctionCreator() { return sanctionCreator; }
    public void setSanctionCreator(String sanctionCreator) { this.sanctionCreator = sanctionCreator; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @JsonProperty("other_info")
    public String getOtherInfo() { return otherInfo; }
    public void setOtherInfo(String otherInfo) { this.otherInfo = otherInfo; }
}
