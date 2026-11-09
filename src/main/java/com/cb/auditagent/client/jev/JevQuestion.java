package com.cb.auditagent.client.jev;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JevQuestion {
    @JsonIgnore
    private String id;

    private String type; // "choice", "noul", "score"

    @JsonProperty("instructions")
    @JsonAlias({"text", "question", "instructions"})
    private String instructions;

    private Object criteria; // Map<String, String> for choice/noul, List<String> for score

    @JsonIgnore
    private List<String> options;

    public JevQuestion() {
    }

    public JevQuestion(String id, String type, String instructions, Object criteria, List<String> options) {
        this.id = id;
        this.type = type;
        this.instructions = instructions;
        this.criteria = criteria;
        this.options = options;
    }

    public static JevQuestion choice(String id, String instructions, List<String> options) {
        Map<String, String> criteriaMap = new LinkedHashMap<>();
        if (options != null) {
            for (String opt : options) {
                criteriaMap.put(opt, opt);
            }
        }
        return new JevQuestion(id, "choice", instructions, criteriaMap, options);
    }

    public static JevQuestion choice(String id, String instructions, Map<String, String> criteria) {
        List<String> opts = criteria != null ? new ArrayList<>(criteria.keySet()) : List.of();
        return new JevQuestion(id, "choice", instructions, criteria, opts);
    }

    public static JevQuestion noul(String id, String instructions) {
        Map<String, String> noulCriteria = Map.of(
                "true", "Condition holds or is true",
                "false", "Condition does not hold or is false"
        );
        return new JevQuestion(id, "noul", instructions, noulCriteria, null);
    }

    public static JevQuestion noul(String id, String instructions, String trueCondition, String falseCondition) {
        Map<String, String> noulCriteria = Map.of(
                "true", trueCondition != null ? trueCondition : "True",
                "false", falseCondition != null ? falseCondition : "False"
        );
        return new JevQuestion(id, "noul", instructions, noulCriteria, null);
    }

    public static JevQuestion score(String id, String instructions, List<String> rubric) {
        return new JevQuestion(id, "score", instructions, rubric, rubric);
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

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getText() {
        return instructions;
    }

    public void setText(String text) {
        this.instructions = text;
    }

    public Object getCriteria() {
        return criteria;
    }

    public void setCriteria(Object criteria) {
        this.criteria = criteria;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}
