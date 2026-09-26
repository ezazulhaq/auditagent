package com.cb.auditagent.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Runtime limits for repository security scans.
 */
@Validated
@Component
@ConfigurationProperties(prefix = "auditagent.scanner")
public class ScannerConfig {

    /** Maximum wall-clock time allowed for one Semgrep process. */
    @Min(1)
    private int timeoutSeconds = 900;

    /** Interval between liveness progress events while Semgrep is running. */
    @Min(1)
    private int heartbeatSeconds = 15;

    @Min(1)
    private int maxTargetBytes = 500000;

    @Min(0)
    private int maxMemoryMb = 1024;

    @Min(1)
    private int ruleTimeoutSeconds = 2;

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getHeartbeatSeconds() {
        return heartbeatSeconds;
    }

    public void setHeartbeatSeconds(int heartbeatSeconds) {
        this.heartbeatSeconds = heartbeatSeconds;
    }

    public int getMaxTargetBytes() {
        return maxTargetBytes;
    }

    public void setMaxTargetBytes(int maxTargetBytes) {
        this.maxTargetBytes = maxTargetBytes;
    }

    public int getMaxMemoryMb() {
        return maxMemoryMb;
    }

    public void setMaxMemoryMb(int maxMemoryMb) {
        this.maxMemoryMb = maxMemoryMb;
    }

    public int getRuleTimeoutSeconds() {
        return ruleTimeoutSeconds;
    }

    public void setRuleTimeoutSeconds(int ruleTimeoutSeconds) {
        this.ruleTimeoutSeconds = ruleTimeoutSeconds;
    }
}
