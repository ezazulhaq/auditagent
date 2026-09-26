package com.cb.auditagent.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@ConfigurationProperties(prefix = "auditagent.memory")
public class MemoryConfig {
    private boolean enabled = true;
    private boolean ftsEnabled = true;
    @Min(1)
    private int retentionDays = 30;
    @Min(1000)
    private int contextTokenBudget = 12_000;
    @Min(1)
    private int candidateLimit = 50;
    @Min(1)
    private int topK = 8;
    @Min(100)
    private int maxChatChars = 8_000;
    @Min(100)
    private int maxToolChars = 4_000;

    /** Whether to use LLM-based summarization for dropped context turns */
    private boolean summarizationEnabled = true;

    /**
     * Maximum characters to keep per individual tool output before aggressive
     * truncation
     */
    @Min(100)
    private int maxToolOutputChars = 2_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFtsEnabled() {
        return ftsEnabled;
    }

    public void setFtsEnabled(boolean ftsEnabled) {
        this.ftsEnabled = ftsEnabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getContextTokenBudget() {
        return contextTokenBudget;
    }

    public void setContextTokenBudget(int contextTokenBudget) {
        this.contextTokenBudget = contextTokenBudget;
    }

    public int getCandidateLimit() {
        return candidateLimit;
    }

    public void setCandidateLimit(int candidateLimit) {
        this.candidateLimit = candidateLimit;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxChatChars() {
        return maxChatChars;
    }

    public void setMaxChatChars(int maxChatChars) {
        this.maxChatChars = maxChatChars;
    }

    public int getMaxToolChars() {
        return maxToolChars;
    }

    public void setMaxToolChars(int maxToolChars) {
        this.maxToolChars = maxToolChars;
    }

    public boolean isSummarizationEnabled() {
        return summarizationEnabled;
    }

    public void setSummarizationEnabled(boolean summarizationEnabled) {
        this.summarizationEnabled = summarizationEnabled;
    }

    public int getMaxToolOutputChars() {
        return maxToolOutputChars;
    }

    public void setMaxToolOutputChars(int maxToolOutputChars) {
        this.maxToolOutputChars = maxToolOutputChars;
    }
}
