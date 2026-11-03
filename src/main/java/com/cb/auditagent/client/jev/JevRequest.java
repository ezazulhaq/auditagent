package com.cb.auditagent.client.jev;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JevRequest {
    private String model;
    private String state;
    private List<JevQuestion> questions;

    public JevRequest() {
    }

    public JevRequest(String model, String state, List<JevQuestion> questions) {
        this.model = model;
        this.state = state;
        this.questions = questions;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public List<JevQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<JevQuestion> questions) {
        this.questions = questions;
    }
}

