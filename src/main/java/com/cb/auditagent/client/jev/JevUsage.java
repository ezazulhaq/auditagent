package com.cb.auditagent.client.jev;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JevUsage {
    @JsonProperty("prompt_tokens")
    @JsonAlias({"input_tokens", "prompt_tokens"})
    private Integer promptTokens;

    @JsonProperty("completion_tokens")
    @JsonAlias({"output_tokens", "completion_tokens"})
    private Integer completionTokens;

    @JsonProperty("total_tokens")
    @JsonAlias({"total_tokens"})
    private Integer totalTokens;

    private Double cost;

    public JevUsage() {
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        if (totalTokens != null) {
            return totalTokens;
        }
        int in = promptTokens != null ? promptTokens : 0;
        int out = completionTokens != null ? completionTokens : 0;
        return in + out > 0 ? (in + out) : null;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }
}
