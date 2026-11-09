package com.cb.auditagent.client.jev;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JevAnswer {
    private String type; // "choice", "noul", "score"

    @JsonAlias({"choice", "value"})
    private String value;

    private String choice;
    private Double noul;
    private Double score;
    private Double confidence;
    private Map<String, Double> probabilities;

    public JevAnswer() {
    }

    public JevAnswer(String value, Double confidence) {
        this.value = value;
        this.choice = value;
        this.confidence = confidence;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        if (choice != null && !choice.isBlank()) {
            return choice;
        }
        if (value != null && !value.isBlank()) {
            return value;
        }
        if (noul != null) {
            return String.valueOf(noul);
        }
        if (score != null) {
            return String.valueOf(score);
        }
        return null;
    }

    public void setValue(String value) {
        this.value = value;
        if (this.choice == null) {
            this.choice = value;
        }
    }

    public String getChoice() {
        return choice != null ? choice : value;
    }

    public void setChoice(String choice) {
        this.choice = choice;
        if (this.value == null) {
            this.value = choice;
        }
    }

    public Double getNoul() {
        return noul;
    }

    public void setNoul(Double noul) {
        this.noul = noul;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getConfidence() {
        if (confidence != null) {
            return confidence;
        }
        if (noul != null) {
            return noul;
        }
        if (choice != null && probabilities != null && probabilities.containsKey(choice)) {
            return probabilities.get(choice);
        }
        return null;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Double> getProbabilities() {
        return probabilities;
    }

    public void setProbabilities(Map<String, Double> probabilities) {
        this.probabilities = probabilities;
    }
}
