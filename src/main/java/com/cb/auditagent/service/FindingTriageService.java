package com.cb.auditagent.service;

import com.cb.auditagent.client.jev.JevClient;
import com.cb.auditagent.client.jev.JevQuestion;
import com.cb.auditagent.client.jev.JevRequest;
import com.cb.auditagent.client.jev.JevResponse;
import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FindingTriageService {
    private static final Logger logger = LoggerFactory.getLogger(FindingTriageService.class);

    private final JevClient jevClient;
    private final JevProperties jevProperties;
    private final MeterRegistry meterRegistry;
    private final LlmService llmService;

    public FindingTriageService(
            @Autowired(required = false) JevClient jevClient,
            JevProperties jevProperties,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) @Lazy LlmService llmService) {
        this.jevClient = jevClient;
        this.jevProperties = jevProperties;
        this.meterRegistry = meterRegistry;
        this.llmService = llmService;
    }

    public List<Vulnerability> triageFindings(List<Vulnerability> findings, String workspacePath) {
        if (findings == null || findings.isEmpty()) {
            return findings;
        }

        if (jevClient == null || !jevClient.isEnabled() || !jevProperties.getTriage().isEnabled()) {
            logger.debug("Jev finding triage is disabled or unconfigured; skipping triage.");
            return findings;
        }

        logger.info("Starting Jev finding triage for {} findings...", findings.size());
        long startTime = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = findings.stream()
                    .map(v -> CompletableFuture.runAsync(() -> triageSingleFinding(v), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            logger.warn("Parallel Jev finding triage encountered error: {}. Remaining findings marked as fallback.", e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startTime;
        long actionableCount = findings.stream().filter(v -> Boolean.TRUE.equals(v.getTriageActionable())).count();
        long fpCount = findings.stream().filter(v -> Boolean.FALSE.equals(v.getTriageActionable())).count();

        logger.info("Jev finding triage completed in {}ms. Actionable: {}, Likely False Positives: {}",
                elapsed, actionableCount, fpCount);

        if (meterRegistry != null) {
            meterRegistry.counter("jev.triage.results", "result", "actionable").increment(actionableCount);
            meterRegistry.counter("jev.triage.results", "result", "false_positive").increment(fpCount);
        }

        return findings;
    }

    public void triageSingleFinding(Vulnerability v) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Rule ID: ").append(v.getRuleId() != null ? v.getRuleId() : "N/A").append("\n");
            prompt.append("Vulnerability Type: ").append(v.getVulnType() != null ? v.getVulnType() : "Security Finding").append("\n");
            prompt.append("File Path: ").append(v.getFilePath() != null ? v.getFilePath() : "unknown").append("\n");
            prompt.append("Line Number: ").append(v.getLineNumber()).append("\n");
            prompt.append("Language: ").append(v.getLanguage() != null ? v.getLanguage() : "unknown").append("\n");
            if (v.getDescription() != null && !v.getDescription().isBlank()) {
                prompt.append("Description: ").append(v.getDescription()).append("\n");
            }
            if (v.getCodeSnippet() != null && !v.getCodeSnippet().isBlank()) {
                prompt.append("Code Snippet:\n").append(v.getCodeSnippet()).append("\n");
            }

            JevQuestion actionabilityQuestion = JevQuestion.choice(
                    "actionability",
                    "Is this static analysis finding an actionable security vulnerability in production code, or is it a false positive or test/mock fixture?",
                    List.of("ACTIONABLE_VULNERABILITY", "FALSE_POSITIVE", "TEST_MOCK_FIXTURE")
            );

            JevRequest request = new JevRequest(
                    jevProperties.getModelName(),
                    prompt.toString(),
                    List.of(actionabilityQuestion)
            );

            JevResponse response = jevClient.execute(request, "finding_triage");
            String result = response.getAnswerValue("actionability");
            Double confidence = response.getAnswerConfidence("actionability");
            if (confidence == null) {
                confidence = 0.5;
            }

            if (response.getUsage() != null && response.getUsage().getTotalTokens() != null && llmService != null) {
                llmService.recordTokens(jevProperties.getModelName(), response.getUsage().getTotalTokens(), null, null, v.getId());
            }

            double requiredConfidence = (v.getSeverity() == Severity.HIGH)
                    ? jevProperties.getTriage().getCriticalConfidenceThreshold()
                    : jevProperties.getTriage().getConfidenceThreshold();

            if ("ACTIONABLE_VULNERABILITY".equalsIgnoreCase(result)) {
                v.setTriageActionable(true);
                v.setTriageClassification("ACTIONABLE_VULNERABILITY");
                v.setTriageConfidence(confidence);
            } else if (("FALSE_POSITIVE".equalsIgnoreCase(result) || "TEST_MOCK_FIXTURE".equalsIgnoreCase(result)) && confidence >= requiredConfidence) {
                v.setTriageActionable(false);
                v.setTriageClassification(result);
                v.setTriageConfidence(confidence);
            } else {
                v.setTriageActionable(true);
                v.setTriageClassification("ACTIONABLE_LOW_CONFIDENCE");
                v.setTriageConfidence(confidence);
            }
        } catch (Exception e) {
            logger.warn("Failed to triage finding {}: {}", v.getId(), e.getMessage());
            v.setTriageActionable(true);
            v.setTriageClassification("TRIAGE_FALLBACK");
            v.setTriageConfidence(0.0);
        }
    }
}
