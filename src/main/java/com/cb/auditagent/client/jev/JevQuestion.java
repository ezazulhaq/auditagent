package com.cb.auditagent.client.jev;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JevQuestion {
    private String id;
    private String type; // "choice", "noul", "score"
    private String text;
    private List<String> options;

    public JevQuestion() {
    }

    public JevQuestion(String id, String type, String text, List<String> options) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.options = options;
    }

    public static JevQuestion choice(String id, String text, List<String> options) {
        return new JevQuestion(id, "choice", text, options);
    }

    public static JevQuestion noul(String id, String text) {
        return new JevQuestion(id, "noul", text, null);
    }

    public static JevQuestion score(String id, String text) {
        return new JevQuestion(id, "score", text, null);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}

