package com.cb.auditagent.client.jev;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JevResponse {
    private String id;
    private String model;
    private Map<String, JevAnswer> answers;
    private JevUsage usage;

    public JevResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Map<String, JevAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, JevAnswer> answers) {
        this.answers = answers;
    }

    public JevUsage getUsage() {
        return usage;
    }

    public void setUsage(JevUsage usage) {
        this.usage = usage;
    }

    public String getAnswerValue(String questionId) {
        if (answers != null && answers.containsKey(questionId) && answers.get(questionId) != null) {
            return answers.get(questionId).getValue();
        }
        return null;
    }

    public Double getAnswerConfidence(String questionId) {
        if (answers != null && answers.containsKey(questionId) && answers.get(questionId) != null) {
            return answers.get(questionId).getConfidence();
        }
        return null;
    }
}

