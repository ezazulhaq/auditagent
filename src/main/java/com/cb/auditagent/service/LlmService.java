package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.domain.Skill;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.graph.RemediationGraph;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Consumer;

/**
 * Service orchestrating the autonomous vulnerability remediation agentic loop.
 * Decomposed in Phase 3:
 * - Interactive chat migrated to {@link ChatConversationService}
 * - Tool specifications and dispatching migrated to {@link RemediationToolRegistry}
 * - Token auditing migrated to {@link LlmTokenAuditService}
 * - Positive/negative reflection patterns migrated to {@link MemoryReflectionService}
 * Standardized exclusively on LangChain4j.
 */
@Service
public class LlmService implements ChatService {
    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    private final ChatConversationService chatConversationService;
    private final RemediationToolRegistry toolRegistry;
    private final LlmTokenAuditService tokenAuditService;
    private final MemoryReflectionService memoryReflectionService;

    private final SkillManagerService skillManager;
    private final ChatModel agentModel;
    private final AgentToolService agentToolService;
    private final ConversationMemoryService memoryService;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper;
    private final DatabaseService databaseService;
    private final RepositoryMemoryService repositoryMemoryService;
    private final MemoryRedactor memoryRedactor;
    private final MeterRegistry meterRegistry;
    private UserLlmClientFactory clientFactory;

    @Autowired(required = false)
    public void setClientFactory(UserLlmClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Value("${auditagent.llm.openai.model-name:${auditagent.llm.model-name:anthropic/claude-3.5-sonnet}}")
    private String agentModelName;

    public String getChatModelName() {
        return getAgentModelName();
    }

    public String getAgentModelName() {
        return agentModelName;
    }

    @Autowired
    public LlmService(
            ChatConversationService chatConversationService,
            RemediationToolRegistry toolRegistry,
            LlmTokenAuditService tokenAuditService,
            MemoryReflectionService memoryReflectionService,
            SkillManagerService skillManager,
            ChatModel agentModel,
            AgentToolService agentToolService,
            ConversationMemoryService memoryService,
            AgentConfig agentConfig,
            ObjectMapper objectMapper,
            @Autowired(required = false) DatabaseService databaseService,
            @Autowired(required = false) RepositoryMemoryService repositoryMemoryService,
            @Lazy RemediationGraph remediationGraph,
            @Autowired(required = false) MemoryRedactor memoryRedactor,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.chatConversationService = chatConversationService;
        this.toolRegistry = toolRegistry;
        this.tokenAuditService = tokenAuditService;
        this.memoryReflectionService = memoryReflectionService;
        this.skillManager = skillManager;
        this.agentModel = agentModel;
        this.agentToolService = agentToolService;
        this.memoryService = memoryService;
        this.agentConfig = agentConfig;
        this.objectMapper = objectMapper;
        this.databaseService = databaseService;
        this.repositoryMemoryService = repositoryMemoryService;
        this.memoryRedactor = memoryRedactor;
        this.meterRegistry = meterRegistry;
    }

    public void recordTokens(String modelName, Integer tokens, String threadId, String runId, String vulnId) {
        if (tokenAuditService != null) {
            tokenAuditService.recordTokens(modelName, tokens, threadId, runId, vulnId);
        }
    }

    // =========================================================================
    // Delegated ChatService implementation
    // =========================================================================

    @Override
    public String chat(String userMessage, List<Vulnerability> findings) {
        return chatConversationService.chat(userMessage, findings);
    }

    @Override
    public String chat(String threadId, String repoPath, String userMessage, List<Vulnerability> findings) {
        return chatConversationService.chat(threadId, repoPath, userMessage, findings);
    }

    @Override
    public Flux<String> chatStream(String threadId, String repoPath, String userMessage, List<Vulnerability> findings) {
        return chatConversationService.chatStream(threadId, repoPath, userMessage, findings);
    }

    @Override
    public Flux<String> chatStream(String userId, String threadId, String repoPath, String userMessage, List<Vulnerability> findings) {
        return chatConversationService.chatStream(userId, threadId, repoPath, userMessage, findings);
    }

    @Override
    public Optional<String> matchFindingForIssue(String issueTitle, String issueBody, List<Vulnerability> findings) {
        return chatConversationService.matchFindingForIssue(issueTitle, issueBody, findings);
    }

    // =========================================================================
    // Delegated RemediationToolRegistry methods
    // =========================================================================

    public List<ToolSpecification> buildToolSpecifications() {
        return toolRegistry.buildToolSpecifications();
    }

    public String executeToolCall(String toolName, String argsJson, String repoPath, Vulnerability vulnerability) {
        return toolRegistry.executeToolCall(toolName, argsJson, repoPath, vulnerability);
    }

    public boolean isSuccessfulToolResult(String toolName, String result) {
        return toolRegistry.isSuccessfulToolResult(toolName, result);
    }

    public boolean isMutatingTool(String toolName) {
        return toolRegistry.isMutatingTool(toolName);
    }

    public String sha256(String value) {
        return toolRegistry.sha256(value);
    }

    // =========================================================================
    // Delegated MemoryReflectionService methods
    // =========================================================================

    public void summarizeDiffForGlobalPattern(Vulnerability vuln, String diff, String runId, String threadId) {
        memoryReflectionService.summarizeDiffForGlobalPattern(vuln, diff, runId, threadId);
    }

    public void reflectOnFailure(Vulnerability vuln, String runId, String threadId, List<Map<String, Object>> trajectory) {
        memoryReflectionService.reflectOnFailure(vuln, runId, threadId, trajectory);
    }

    // =========================================================================
    // AgentResult DTO
    // =========================================================================

    public static class AgentResult {
        private final String finalResponse;
        private final int iterationsUsed;
        private final boolean maxIterationsReached;
        private final boolean patchApplied;
        private final List<Map<String, Object>> trajectory;
        private final String runId;
        private final boolean verified;
        private final boolean compilePassed;
        private final boolean rescanPassed;
        private final boolean testsPassed;

        public AgentResult(String finalResponse, int iterationsUsed,
                boolean maxIterationsReached, boolean patchApplied,
                List<Map<String, Object>> trajectory) {
            this.finalResponse = finalResponse;
            this.iterationsUsed = iterationsUsed;
            this.maxIterationsReached = maxIterationsReached;
            this.patchApplied = patchApplied;
            this.trajectory = trajectory;
            this.runId = null;
            this.verified = false;
            this.compilePassed = false;
            this.rescanPassed = false;
            this.testsPassed = false;
        }

        public AgentResult(String finalResponse, int iterationsUsed, boolean maxIterationsReached,
                boolean patchApplied, List<Map<String, Object>> trajectory, String runId,
                boolean verified, boolean compilePassed, boolean rescanPassed,
                boolean testsPassed) {
            this.finalResponse = finalResponse;
            this.iterationsUsed = iterationsUsed;
            this.maxIterationsReached = maxIterationsReached;
            this.patchApplied = patchApplied;
            this.trajectory = trajectory;
            this.runId = runId;
            this.verified = verified;
            this.compilePassed = compilePassed;
            this.rescanPassed = rescanPassed;
            this.testsPassed = testsPassed;
        }

        public String getFinalResponse() {
            return finalResponse;
        }

        public int getIterationsUsed() {
            return iterationsUsed;
        }

        public boolean isMaxIterationsReached() {
            return maxIterationsReached;
        }

        public boolean isPatchApplied() {
            return patchApplied;
        }

        public List<Map<String, Object>> getTrajectory() {
            return trajectory;
        }

        public String getRunId() {
            return runId;
        }

        public boolean isVerified() {
            return verified;
        }

        public boolean isCompilePassed() {
            return compilePassed;
        }

        public boolean isRescanPassed() {
            return rescanPassed;
        }

        public boolean isTestsPassed() {
            return testsPassed;
        }
    }

    // =========================================================================
    // Agentic Loop — Multi-turn tool-calling remediation (LangChain4j)
    // =========================================================================

    public AgentResult runAgentLoop(String threadId, Vulnerability vuln, String repoPath,
            Consumer<Map<String, Object>> progressCallback) {
        return runAgentLoop(null, threadId, vuln, repoPath, false, 0, progressCallback);
    }

    public AgentResult runAgentLoop(String runId, String threadId, Vulnerability vuln, String repoPath,
            boolean resume, int startingIteration,
            Consumer<Map<String, Object>> progressCallback) {
        return runAgentLoop(runId, threadId, vuln, repoPath, repoPath, resume, startingIteration, progressCallback);
    }

    public AgentResult runAgentLoop(String runId, String threadId, Vulnerability vuln, String repoPath,
            String repositoryMemoryKey, boolean resume, int startingIteration,
            Consumer<Map<String, Object>> progressCallback) {
        logger.info("Starting agent loop for vulnerability {} in repo {}", vuln.getId(), repoPath);

        // 1. Build system prompt with iteration budget
        String systemPrompt = buildAgenticSystemPrompt(vuln, repoPath);
        if (repositoryMemoryService != null) {
            systemPrompt += "\n\n## Approved Repository Memory\n" +
                    repositoryMemoryService.retrieveForPrompt(repositoryMemoryKey, vuln);
        }
        if (!resume)
            memoryService.initConversation(threadId, runId, systemPrompt);

        // 2. Build task prompt
        String taskPrompt = buildTaskPrompt(vuln);
        if (!resume)
            memoryService.addUserMessage(threadId, runId, taskPrompt);

        // 3. Build tool specifications
        List<ToolSpecification> toolSpecs = buildToolSpecifications();

        // 4. Loop state
        int iteration = startingIteration;
        int maxIterations = agentConfig.getMaxIterations();
        String finalResponse = null;
        boolean hitMaxIterations = false;
        boolean patchApplied = false;
        int consecutiveEmptyResponses = 0;
        String lastToolName = null;
        int consecutiveSameToolCalls = 0;
        boolean hasCalledApplyPatch = false;
        boolean compilePassed = false;
        boolean rescanPassed = false;
        boolean testsPassed = false;
        boolean testsAttempted = false;
        List<Map<String, Object>> trajectory = new ArrayList<>();
        if (resume && databaseService != null && runId != null) {
            var recovered = databaseService.getAgentRun(runId).orElse(null);
            if (recovered != null) {
                patchApplied = recovered.patchApplied();
                hasCalledApplyPatch = patchApplied;
                compilePassed = recovered.compilePassed();
                rescanPassed = recovered.rescanPassed();
                testsPassed = recovered.testsPassed();
                testsAttempted = recovered.testsPassed();
            }
        }

        // 5. Agent loop
        while (iteration < maxIterations) {
            iteration++;
            logger.info("Agent loop iteration {} / {} for vuln {}", iteration, maxIterations, vuln.getId());
            checkpointRun(runId, iteration, patchApplied, compilePassed, rescanPassed, testsPassed,
                    AgentPhase.GATHERING_CONTEXT, AgentRunStatus.ACTIVE, "BEFORE_MODEL_CALL", null, null);

            emitProgress(progressCallback, "agent_thinking",
                    String.format("🤖 Agent reasoning (iteration %d/%d)...", iteration, maxIterations),
                    iteration, maxIterations);

            try {
                // Build request with windowed conversation history
                List<ChatMessage> history = memoryService.getHistory(threadId, runId);

                ChatRequest request = ChatRequest.builder()
                        .messages(history)
                        .toolSpecifications(toolSpecs)
                        .build();

                // LLM call with timeout
                ChatResponse response;
                CompletableFuture<ChatResponse> llmFuture = CompletableFuture
                        .supplyAsync(() -> agentModel.chat(request));
                try {
                    response = llmFuture.get(agentConfig.getLlmCallTimeoutSeconds(), TimeUnit.SECONDS);
                    if (response != null && response.tokenUsage() != null
                            && response.tokenUsage().totalTokenCount() != null) {
                        recordTokens(getAgentModelName(), response.tokenUsage().totalTokenCount(), threadId, runId,
                                vuln != null ? vuln.getId() : null);
                    }
                } catch (TimeoutException te) {
                    llmFuture.cancel(true);
                    logger.error("LLM call timed out at iteration {} for vuln {}", iteration, vuln.getId());
                    emitProgress(progressCallback, "agent_error",
                            "⚠️ LLM call timed out after " + agentConfig.getLlmCallTimeoutSeconds() +
                                    "s. Retrying...",
                            iteration, maxIterations);
                    memoryService.addUserMessage(threadId, runId,
                            "The previous LLM call timed out. Please provide a concise response and avoid unnecessary analysis.");

                    trajectory.add(trajectoryStep(iteration, "TIMEOUT", null, "LLM call timed out"));
                    checkpointRun(runId, iteration, patchApplied, compilePassed, rescanPassed, testsPassed,
                            AgentPhase.FAILED, AgentRunStatus.FAILED, "TIMEOUT", null, "LLM_CALL_TIMEOUT");
                    continue;
                }

                if (response == null || response.aiMessage() == null) {
                    consecutiveEmptyResponses++;
                    logger.error("Empty response from LLM at iteration {} (consecutive: {})",
                            iteration, consecutiveEmptyResponses);

                    if (consecutiveEmptyResponses >= agentConfig.getMaxConsecutiveEmptyResponses()) {
                        finalResponse = "Agent aborted: received " + consecutiveEmptyResponses +
                                " consecutive empty responses from the LLM. This may indicate a model issue.";
                        logger.error("Aborting agent loop after {} consecutive empty responses",
                                consecutiveEmptyResponses);
                        emitProgress(progressCallback, "agent_error",
                                "❌ " + finalResponse, iteration, maxIterations);
                        checkpointRun(runId, iteration, patchApplied, compilePassed, rescanPassed, testsPassed,
                                AgentPhase.FAILED, AgentRunStatus.FAILED, "CONSECUTIVE_EMPTY_RESPONSES",
                                finalResponse, "EMPTY_MODEL_RESPONSE");
                        break;
                    }

                    emitProgress(progressCallback, "agent_error",
                            "⚠️ Empty response from LLM. Retrying (" + consecutiveEmptyResponses +
                                    "/" + agentConfig.getMaxConsecutiveEmptyResponses() + ")...",
                            iteration, maxIterations);
                    memoryService.addUserMessage(threadId, runId,
                            "The previous LLM call returned an empty response. Please try again.");

                    trajectory.add(trajectoryStep(iteration, "EMPTY_RESPONSE", null,
                            "Empty response #" + consecutiveEmptyResponses));
                    checkpointRun(runId, iteration, patchApplied, compilePassed, rescanPassed, testsPassed,
                            AgentPhase.FAILED, AgentRunStatus.FAILED, "EMPTY_RESPONSE", null,
                            "EMPTY_MODEL_RESPONSE");
                    continue;
                }

                consecutiveEmptyResponses = 0;
                AiMessage aiMessage = response.aiMessage();
                memoryService.addAiMessage(threadId, runId, aiMessage);

                if (!aiMessage.hasToolExecutionRequests()) {
                    String proposedFinalResponse = Optional.ofNullable(aiMessage.text())
                            .map(String::trim)
                            .filter(t -> !t.isEmpty())
                            .orElse("Agent completed without a final response.");

                    if (patchApplied && (!compilePassed || !rescanPassed || !testsAttempted || !testsPassed)) {
                        String missing = "The run cannot complete yet. Required verification is missing or failed: " +
                                "compile=" + compilePassed + ", target-rescan=" + rescanPassed +
                                ", tests=" + (testsAttempted ? testsPassed : "not-run") +
                                ". Continue with verification or repair the patch.";
                        memoryService.addUserMessage(threadId, runId, missing);
                        trajectory.add(trajectoryStep(iteration, "VERIFICATION_GATE", null, missing));
                        emitProgress(progressCallback, "agent_nudge",
                                "Verification gate prevented early completion.", iteration, maxIterations);
                        continue;
                    }
                    finalResponse = proposedFinalResponse;

                    trajectory.add(trajectoryStep(iteration, "FINAL_RESPONSE", null,
                            finalResponse.length() > 200 ? finalResponse.substring(0, 200) + "..." : finalResponse));

                    logger.info("Agent loop completed after {} iterations for vuln {}", iteration, vuln.getId());
                    emitProgress(progressCallback, "agent_complete",
                            "✅ Agent completed analysis and remediation.", iteration, maxIterations);
                    break;
                }

                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                List<String> toolResults = new ArrayList<>();

                for (ToolExecutionRequest toolReq : toolRequests) {
                    String toolName = toolReq.name();
                    String toolArgs = toolReq.arguments();

                    if (toolName.equals(lastToolName)) {
                        consecutiveSameToolCalls++;
                    } else {
                        consecutiveSameToolCalls = 0;
                        lastToolName = toolName;
                    }

                    if (RemediationToolRegistry.TOOL_APPLY_PATCH.equals(toolName)) {
                        hasCalledApplyPatch = true;
                    }

                    injectStuckNudgeIfNeeded(threadId, runId, iteration, maxIterations,
                            consecutiveSameToolCalls, lastToolName, hasCalledApplyPatch,
                            progressCallback);

                    logger.info("Agent calling tool '{}' with args: {}", toolName, toolArgs);
                    emitProgress(progressCallback, "tool_call",
                            String.format("🔧 Calling tool: **%s**", toolName),
                            iteration, maxIterations);

                    long startToolTime = System.currentTimeMillis();
                    String toolResult = executeToolCall(toolName, toolArgs, repoPath, vuln);
                    long toolDuration = System.currentTimeMillis() - startToolTime;

                    String idempotencyKey = sha256(toolName + "\n" + Optional.ofNullable(toolArgs).orElse("{}"));
                    String resultStatus = isSuccessfulToolResult(toolName, toolResult) ? "SUCCESS" : "FAILURE";
                    persistToolStep(runId, iteration, toolName, toolArgs, toolResult, resultStatus, toolDuration,
                            idempotencyKey);

                    if (RemediationToolRegistry.TOOL_APPLY_PATCH.equals(toolName)) {
                        if (toolResult != null && toolResult.startsWith("SUCCESS")) {
                            patchApplied = true;
                            hasCalledApplyPatch = true;
                            persistRunChange(runId, toolArgs, toolResult);
                            checkpointRun(runId, iteration, patchApplied, compilePassed, rescanPassed, testsPassed,
                                    AgentPhase.PATCHING, AgentRunStatus.ACTIVE, "PATCH_APPLIED", null, null);
                        }
                    } else if (RemediationToolRegistry.TOOL_ROLLBACK_FILE.equals(toolName)) {
                        if (toolResult != null && toolResult.startsWith("SUCCESS")) {
                            patchApplied = false;
                            compilePassed = false;
                            rescanPassed = false;
                            checkpointRun(runId, iteration, patchApplied, compilePassed, rescanPassed, testsPassed,
                                    AgentPhase.PATCHING, AgentRunStatus.ACTIVE, "ROLLBACK_APPLIED", null, null);
                        }
                    } else if (RemediationToolRegistry.TOOL_COMPILE_PROJECT.equals(toolName)) {
                        boolean compileSkipped = toolResult != null && toolResult.startsWith("BUILD SKIPPED");
                        compilePassed = toolResult != null && (toolResult.startsWith("BUILD SUCCESS") || compileSkipped);
                        persistVerification(runId, "BUILD", compilePassed, compileSkipped, toolResult);
                    } else if (RemediationToolRegistry.TOOL_RESCAN_FILE.equals(toolName)) {
                        rescanPassed = toolResult != null && toolResult.startsWith("Rescan complete: 0 vulnerabilities");
                        persistVerification(runId, "RESCAN", rescanPassed, false, toolResult);
                    } else if (RemediationToolRegistry.TOOL_RUN_TESTS.equals(toolName)) {
                        testsAttempted = true;
                        boolean testsSkipped = toolResult != null && toolResult.startsWith("TEST SKIPPED");
                        testsPassed = toolResult != null && (toolResult.startsWith("TEST SUCCESS") || testsSkipped);
                        persistVerification(runId, "TEST", testsPassed, testsSkipped, toolResult);
                    }

                    String uiResult = (toolResult != null && toolResult.length() > 500)
                            ? toolResult.substring(0, 500) + "... [truncated]"
                            : toolResult;
                    emitProgress(progressCallback, "tool_result",
                            String.format("📋 **%s** result: %s", toolName, uiResult),
                            iteration, maxIterations);

                    int maxResultChars = agentConfig.getMaxToolResultChars();
                    String memoryResult = (toolResult != null && toolResult.length() > maxResultChars)
                            ? toolResult.substring(0, maxResultChars) + "\n... [truncated to " + maxResultChars + " chars]"
                            : toolResult;
                    toolResults.add(memoryResult);

                    trajectory.add(trajectoryStep(iteration, toolName, toolArgs, uiResult));
                }

                memoryService.addToolResults(threadId, runId, toolRequests, toolResults);

            } catch (Exception e) {
                logger.error("Error during agent loop iteration {}", iteration, e);
                emitProgress(progressCallback, "agent_error",
                        "⚠️ Error: " + e.getMessage(), iteration, maxIterations);
                trajectory.add(trajectoryStep(iteration, "ERROR", null, e.getMessage()));
                memoryService.addUserMessage(threadId, runId,
                        "An error occurred: " + e.getMessage() + ". Please adjust your approach.");
            }
        }

        if (iteration >= maxIterations && finalResponse == null) {
            hitMaxIterations = true;
            finalResponse = "Agent reached maximum iterations (" + maxIterations +
                    ") without producing a final response. The last applied changes may need manual review.";
            logger.warn("Agent reached max iterations for vuln {}", vuln.getId());
            reflectOnFailure(vuln, runId, threadId, trajectory);
        }

        logTrajectory(vuln.getId(), trajectory);

        boolean verified = patchApplied && compilePassed && rescanPassed && testsAttempted && testsPassed
                && !hitMaxIterations;
        checkpointRun(runId, iteration, patchApplied, compilePassed, rescanPassed, testsPassed,
                verified ? AgentPhase.AWAITING_APPROVAL : AgentPhase.FAILED,
                verified ? AgentRunStatus.AWAITING_APPROVAL : AgentRunStatus.FAILED,
                "LOOP_COMPLETE", finalResponse, verified ? null : "VERIFICATION_INCOMPLETE");

        return new AgentResult(finalResponse, iteration, hitMaxIterations, patchApplied, trajectory,
                runId, verified, compilePassed, rescanPassed, testsPassed);
    }

    public void checkpointRun(String runId, int iteration, boolean patchApplied,
            boolean compilePassed, boolean rescanPassed, boolean testsPassed,
            AgentPhase phase, AgentRunStatus status, String checkpoint,
            String summary, String errorCode) {
        if (databaseService == null || runId == null)
            return;
        try {
            String checkpointJson = objectMapper.writeValueAsString(Map.of(
                    "checkpoint", checkpoint,
                    "iteration", iteration,
                    "timestamp", Instant.now().toString()));
            databaseService.updateAgentRun(runId, phase, status, iteration, 0, patchApplied,
                    compilePassed, rescanPassed, testsPassed, checkpointJson, summary,
                    errorCode, errorCode == null ? null : summary);
        } catch (Exception e) {
            logger.error("Could not checkpoint durable run {}", runId, e);
        }
    }

    public void persistToolStep(String runId, int iteration, String toolName, String args,
            String result, String resultStatus, long duration, String idempotencyKey) {
        if (databaseService == null || runId == null)
            return;
        String safeArgs = memoryRedactor == null ? args : memoryRedactor.tool(args);
        String safeResult = memoryRedactor == null ? result : memoryRedactor.tool(result);
        databaseService.appendAgentStep(runId, iteration, "TOOL", toolName, safeArgs, safeResult,
                resultStatus, duration, idempotencyKey);
    }

    public void persistVerification(String runId, String kind, boolean passed, String evidence) {
        persistVerification(runId, kind, passed, false, evidence);
    }

    public void persistVerification(String runId, String kind, boolean passed, boolean skipped, String evidence) {
        if (databaseService == null || runId == null)
            return;
        String safeEvidence = memoryRedactor == null ? evidence : memoryRedactor.tool(evidence);
        databaseService.addVerificationResult(
                runId, kind, skipped ? "SKIPPED" : passed ? "PASS" : "FAIL", safeEvidence, null);
    }

    public void persistRunChange(String runId, String argsJson, String toolResult) {
        if (databaseService == null || runId == null)
            return;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
            databaseService.addRunChange(runId, Objects.toString(args.get("filePath"), ""),
                    resultLabel(toolResult, "BEFORE_HASH"), resultLabel(toolResult, "AFTER_HASH"),
                    resultLabel(toolResult, "BACKUP_PATH"));
        } catch (Exception e) {
            logger.error("Could not persist file change for run {}", runId, e);
        }
    }

    private String resultLabel(String result, String label) {
        String prefix = label + ": ";
        return result.lines().filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim()).findFirst().orElse(null);
    }

    private void injectStuckNudgeIfNeeded(String threadId, String runId, int iteration, int maxIterations,
            int consecutiveSameToolCalls, String lastToolName,
            boolean hasCalledApplyPatch,
            Consumer<Map<String, Object>> progressCallback) {
        if (consecutiveSameToolCalls >= 3) {
            String nudge = "You've called '" + lastToolName + "' " + (consecutiveSameToolCalls + 1) +
                    " times consecutively. Consider whether you have enough context and should move " +
                    "to the next phase (patching, compiling, or rescanning).";
            memoryService.addUserMessage(threadId, runId, nudge);
            logger.info("Stuck nudge injected at iteration {}: consecutive {} calls", iteration, lastToolName);
            emitProgress(progressCallback, "agent_nudge",
                    "🔄 Agent nudged: repetitive tool calls detected.", iteration, maxIterations);
        }

        if (iteration > maxIterations * 2 / 3 && !hasCalledApplyPatch) {
            String nudge = "You are at iteration " + iteration + " of " + maxIterations +
                    " (past the 2/3 mark). You have NOT yet applied a patch. " +
                    "Please prioritize applying a fix now — you may not have enough iterations " +
                    "remaining for extensive exploration.";
            memoryService.addUserMessage(threadId, runId, nudge);
            logger.info("Stuck nudge injected at iteration {}: 2/3 budget exceeded without patch", iteration);
            emitProgress(progressCallback, "agent_nudge",
                    "⚠️ Agent nudged: iteration budget running low, prioritizing patch.",
                    iteration, maxIterations);
        }
    }

    private String buildAgenticSystemPrompt(Vulnerability vuln, String repoPath) {
        Skill skill = skillManager != null ? skillManager.getSkill("patch-engineer") : null;
        String basePrompt = Optional.ofNullable(skill)
                .map(Skill::getInstructions)
                .filter(inst -> !inst.trim().isEmpty())
                .orElse("You are a Security Remediation Agent. You have tools to read files, " +
                        "search the codebase, apply patches, compile the project, rescan for vulnerabilities, " +
                        "and run tests. Your goal is to fix the given vulnerability and verify the fix.");

        int maxIter = agentConfig.getMaxIterations();
        int urgentThreshold = maxIter * 2 / 3;

        return basePrompt + "\n\n## Context\n" +
                "- Repository path: " + repoPath + "\n" +
                "- Target vulnerability ID: " + vuln.getId() + "\n" +
                "- All file paths in tool calls should be relative to the repository root.\n" +
                "\n## Iteration Budget\n" +
                "You have a maximum of **" + maxIter + " iterations** (tool call rounds).\n" +
                "Budget your iterations wisely:\n" +
                "- **Iterations 1-3**: Read the vulnerable file and gather essential context.\n" +
                "- **Iterations 4-" + urgentThreshold + "**: Apply the patch.\n" +
                "- **Iterations " + (urgentThreshold + 1) + "-" + maxIter
                + "**: Compile, rescan, and run tests to verify.\n" +
                "- If you reach iteration " + urgentThreshold + " without applying a patch, " +
                "**stop exploring and apply your best fix immediately**.\n" +
                "- Do NOT spend more than 3 iterations reading files without making progress toward a fix.\n";
    }

    private String buildTaskPrompt(Vulnerability vuln) {
        String language = Optional.ofNullable(vuln.getLanguage()).orElse("Unknown");
        return String.format(
                "Please fix this security vulnerability:\n\n" +
                        "**Vulnerability ID:** %s\n" +
                        "**File Path:** %s\n" +
                        "**Line Number:** %d\n" +
                        "**Language:** %s\n" +
                        "**Type:** %s\n" +
                        "**Severity:** %s\n" +
                        "**Description:** %s\n\n" +
                        "**Vulnerable Code Snippet:**\n```%s\n%s\n```\n\n" +
                        "Start by reading the full file to understand the context, then fix the vulnerability, " +
                        "and verify your fix compiles and the security scanner no longer flags the issue.",
                vuln.getId(),
                vuln.getFilePath(),
                vuln.getLineNumber(),
                language,
                vuln.getVulnType(),
                vuln.getSeverity() != null ? vuln.getSeverity().name() : "UNKNOWN",
                vuln.getDescription(),
                language.toLowerCase(),
                vuln.getCodeSnippet());
    }

    private void emitProgress(Consumer<Map<String, Object>> callback, String step,
            String message, int iteration, int maxIterations) {
        if (callback != null) {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "agent_progress");
            event.put("step", step);
            event.put("message", message);
            event.put("iteration", iteration);
            event.put("maxIterations", maxIterations);
            callback.accept(event);
        }
    }

    private Map<String, Object> trajectoryStep(int iteration, String action, String input, String output) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("iteration", iteration);
        step.put("action", action);
        if (input != null)
            step.put("input", input);
        if (output != null) {
            String cappedOutput = output.length() > 2000 ? output.substring(0, 2000) + "... [truncated]" : output;
            step.put("output", cappedOutput);
        }
        return step;
    }

    private void logTrajectory(String vulnId, List<Map<String, Object>> trajectory) {
        try {
            String json = objectMapper.writeValueAsString(trajectory);
            logger.info("Agent remediation trajectory for {} ({} steps): {}", vulnId, trajectory.size(), json);
        } catch (Exception e) {
            logger.warn("Failed to serialize trajectory for vuln {}", vulnId, e);
        }
    }
}
