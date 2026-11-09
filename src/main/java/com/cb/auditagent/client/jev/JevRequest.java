package com.cb.auditagent.client.jev;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JevRequest {
    private String model;
    private Object state;
    private Map<String, JevQuestion> questions;

    public JevRequest() {
        this.questions = new LinkedHashMap<>();
    }

    public JevRequest(String model, Object state, Map<String, JevQuestion> questions) {
        this.model = model;
        this.state = state;
        this.questions = questions != null ? questions : new LinkedHashMap<>();
    }

    public JevRequest(String model, Object state, Collection<JevQuestion> questionList) {
        this.model = model;
        this.state = state;
        this.questions = new LinkedHashMap<>();
        if (questionList != null) {
            for (JevQuestion q : questionList) {
                if (q != null && q.getId() != null) {
                    this.questions.put(q.getId(), q);
                }
            }
        }
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Object getState() {
        return state;
    }

    public void setState(Object state) {
        this.state = state;
    }

    public Map<String, JevQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(Map<String, JevQuestion> questions) {
        this.questions = questions != null ? questions : new LinkedHashMap<>();
    }

    public void addQuestion(JevQuestion question) {
        if (question != null && question.getId() != null) {
            if (this.questions == null) {
                this.questions = new LinkedHashMap<>();
            }
            this.questions.put(question.getId(), question);
        }
    }
}
