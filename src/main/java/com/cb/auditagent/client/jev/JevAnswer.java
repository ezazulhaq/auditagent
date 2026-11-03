package com.cb.auditagent.client.jev;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JevAnswer {
    private String value;
    private Double confidence;

    public JevAnswer() {
    }

    public JevAnswer(String value, Double confidence) {
        this.value = value;
        this.confidence = confidence;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
}

