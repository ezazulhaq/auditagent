package com.cb.auditagent.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the agentic vulnerability remediation loop.
 */
@Validated
@Component
@ConfigurationProperties(prefix = "auditagent.agent")
public class AgentConfig {

    /** Maximum number of LLM reasoning iterations before the agent gives up */
    @Min(1)
    private int maxIterations = 15;

    /** Timeout in seconds for project compilation commands */
    @Min(1)
    private int compileTimeoutSeconds = 120;

    /** Timeout in seconds for running tests */
    @Min(1)
    private int testTimeoutSeconds = 180;

    /** Directory (relative to repo root) where file backups are stored */
    @NotBlank
    private String backupDir = ".auditagent/backups";

    /** Maximum number of file lines to return in a single read_file tool call */
    @Min(1)
    private int maxFileReadLines = 200;

    /** Maximum number of retry attempts when a patch fails verification */
    @Min(0)
    private int maxRetries = 3;

    /** Timeout in seconds for individual LLM calls (Gap #6) */
    @Min(1)
    private int llmCallTimeoutSeconds = 120;

    /** Max characters to store per tool result in conversation memory (Gap #2) */
    @Min(100)
    private int maxToolResultChars = 4000;

    /** Max messages to keep in conversation history sliding window (Gap #1) */
    @Min(5)
    private int maxHistoryMessages = 40;

    /** Max consecutive empty LLM responses before aborting (Gap #5) */
    @Min(1)
    private int maxConsecutiveEmptyResponses = 3;

    private boolean useMultiAgent = true;

    /** Whether to execute agent shell commands inside a Docker container */
    private boolean sandboxEnabled = false;

    /** Docker image to use for sandboxed command execution */
    private String sandboxImage = "auditagent-sandbox:latest";

    /** Memory limit for sandbox containers (Docker format, e.g. "2g") */
    private String sandboxMemoryLimit = "2g";

    /** CPU limit for sandbox containers (e.g. "2.0" for 2 CPUs) */
    private String sandboxCpuLimit = "2.0";

    // Getters and Setters

    public boolean isUseMultiAgent() {
        return useMultiAgent;
    }

    public void setUseMultiAgent(boolean useMultiAgent) {
        this.useMultiAgent = useMultiAgent;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getCompileTimeoutSeconds() {
        return compileTimeoutSeconds;
    }

    public void setCompileTimeoutSeconds(int compileTimeoutSeconds) {
        this.compileTimeoutSeconds = compileTimeoutSeconds;
    }

    public int getTestTimeoutSeconds() {
        return testTimeoutSeconds;
    }

    public void setTestTimeoutSeconds(int testTimeoutSeconds) {
        this.testTimeoutSeconds = testTimeoutSeconds;
    }

    public String getBackupDir() {
        return backupDir;
    }

    public void setBackupDir(String backupDir) {
        this.backupDir = backupDir;
    }

    public int getMaxFileReadLines() {
        return maxFileReadLines;
    }

    public void setMaxFileReadLines(int maxFileReadLines) {
        this.maxFileReadLines = maxFileReadLines;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getLlmCallTimeoutSeconds() {
        return llmCallTimeoutSeconds;
    }

    public void setLlmCallTimeoutSeconds(int llmCallTimeoutSeconds) {
        this.llmCallTimeoutSeconds = llmCallTimeoutSeconds;
    }

    public int getMaxToolResultChars() {
        return maxToolResultChars;
    }

    public void setMaxToolResultChars(int maxToolResultChars) {
        this.maxToolResultChars = maxToolResultChars;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public int getMaxConsecutiveEmptyResponses() {
        return maxConsecutiveEmptyResponses;
    }

    public void setMaxConsecutiveEmptyResponses(int maxConsecutiveEmptyResponses) {
        this.maxConsecutiveEmptyResponses = maxConsecutiveEmptyResponses;
    }

    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }

    public void setSandboxEnabled(boolean sandboxEnabled) {
        this.sandboxEnabled = sandboxEnabled;
    }

    public String getSandboxImage() {
        return sandboxImage;
    }

    public void setSandboxImage(String sandboxImage) {
        this.sandboxImage = sandboxImage;
    }

    public String getSandboxMemoryLimit() {
        return sandboxMemoryLimit;
    }

    public void setSandboxMemoryLimit(String sandboxMemoryLimit) {
        this.sandboxMemoryLimit = sandboxMemoryLimit;
    }

    public String getSandboxCpuLimit() {
        return sandboxCpuLimit;
    }

    public void setSandboxCpuLimit(String sandboxCpuLimit) {
        this.sandboxCpuLimit = sandboxCpuLimit;
    }
}
