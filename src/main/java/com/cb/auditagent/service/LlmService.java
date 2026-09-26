package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.AgentPhase;
import com.cb.auditagent.domain.AgentRunStatus;
import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.Skill;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.graph.RemediationGraph;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Consumer;

/**
 * LLM service that provides both a simple chat interface (via Spring AI) and
 * an agentic ReAct-style tool-calling loop (via LangChain4j) for autonomous
 * vulnerability remediation.
 *
 * <p>
 * Loop Engineering features:
 * </p>
 * <ul>
 * <li>Gap #1: Sliding window via ConversationMemoryService</li>
 * <li>Gap #2: Tool result capping before memory storage</li>
 * <li>Gap #3: Iteration budget in system prompt</li>
 * <li>Gap #4: Mid-loop stuck detection and nudging</li>
 * <li>Gap #5: Consecutive empty response cap</li>
 * <li>Gap #6: LLM call timeout via CompletableFuture</li>
 * <li>Gap #7: Patch-applied tracking in AgentResult</li>
 * <li>Gap #8: Structured trajectory logging</li>
 * </ul>
 */
@Service
public class LlmService {
    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    // Tool name constants
    private static final String TOOL_READ_FILE = "read_file";
    private static final String TOOL_SEARCH_CODEBASE = "search_codebase";
    private static final String TOOL_APPLY_PATCH = "apply_patch";
    private static final String TOOL_COMPILE_PROJECT = "compile_project";
    private static final String TOOL_RESCAN_FILE = "rescan_file";
    private static final String TOOL_RUN_TESTS = "run_tests";
    private static final String TOOL_ROLLBACK_FILE = "rollback_file";
    private static final String TOOL_RUN_LINTER = "run_linter";
    private static final String TOOL_RUN_STATIC_ANALYSIS = "run_static_analysis";
    private static final String TOOL_SEARCH_REJECTED_MEMORIES = "search_rejected_memories";

    // Spring AI — used only for simple chat
    private final ChatModel chatModel;
    private final SkillManagerService skillManager;

    // LangChain4j — used for the agentic tool-calling loop
    private final dev.langchain4j.model.chat.ChatModel agentModel;
    private final AgentToolService agentToolService;
    private final ConversationMemoryService memoryService;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper;
    private final DatabaseService databaseService;
    private final RepositoryMemoryService repositoryMemoryService;
    private final MemoryRedactor memoryRedactor;
    private final MeterRegistry meterRegistry;

    @Value("${spring.ai.openai.chat.model:anthropic.claude-3-5-sonnet-20241022-v2:0}")
    private String chatModelName;

    @Value("${auditagent.llm.openai.model-name:anthropic/claude-3.5-sonnet}")
    private String agentModelName;

    public String getChatModelName() {
        return chatModelName;
    }

    public String getAgentModelName() {
        return agentModelName;
    }

    @Autowired
    public LlmService(
            ChatModel chatModel,
            SkillManagerService skillManager,
            dev.langchain4j.model.chat.ChatModel agentModel,
            AgentToolService agentToolService,
            ConversationMemoryService memoryService,
            AgentConfig agentConfig,
            ObjectMapper objectMapper,
            DatabaseService databaseService,
            RepositoryMemoryService repositoryMemoryService,
            @Lazy RemediationGraph remediationGraph,
            MemoryRedactor memoryRedactor,
            MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
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
        if (meterRegistry != null && tokens != null && tokens > 0) {
            meterRegistry.counter("ai.tokens.consumed", "model", modelName).increment(tokens);
        }
        if (databaseService != null && tokens != null && tokens > 0 && threadId != null) {
            databaseService.saveTokenUsage(threadId, runId, vulnId, modelName, tokens);
        }
    }

    public LlmService(ChatModel chatModel, SkillManagerService skillManager,
            dev.langchain4j.model.chat.ChatModel agentModel, AgentToolService agentToolService,
            ConversationMemoryService memoryService, AgentConfig agentConfig,
            ObjectMapper objectMapper) {
        this(chatModel, skillManager, agentModel, agentToolService, memoryService, agentConfig,
                objectMapper, null, null, null, null, null);
    }

    // =========================================================================
    // AgentResult — includes patchApplied flag (Gap #7)
    // =========================================================================

    public static class AgentResult {
        private final String finalResponse;
        private final int iterationsUsed;
        private final boolean maxIterationsReached;
        private final boolean patchApplied; // Gap #7
        private final List<Map<String, Object>> trajectory; // Gap #8
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

        // 1. Build system prompt with iteration budget (Gap #3)
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
        boolean patchApplied = false; // Gap #7
        int consecutiveEmptyResponses = 0; // Gap #5
        String lastToolName = null; // Gap #4
        int consecutiveSameToolCalls = 0; // Gap #4
        boolean hasCalledApplyPatch = false; // Gap #4
        boolean compilePassed = false;
        boolean rescanPassed = false;
        boolean testsPassed = false;
        boolean testsAttempted = false;
        List<Map<String, Object>> trajectory = new ArrayList<>(); // Gap #8
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
                // Build request with windowed conversation history (Gap #1 via memoryService)
                List<ChatMessage> history = memoryService.getHistory(threadId, runId);

                ChatRequest request = ChatRequest.builder()
                        .messages(history)
                        .toolSpecifications(toolSpecs)
                        .build();

                // Gap #6: LLM call with timeout
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

                    // Log timeout in trajectory (Gap #8)
                    trajectory.add(trajectoryStep(iteration, "TIMEOUT", null, "LLM call timed out"));
                    continue;
                }

                // Gap #5: Handle empty responses with cap
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
                    continue;
                }
                consecutiveEmptyResponses = 0; // Reset on valid response

                AiMessage aiMessage = response.aiMessage();
                memoryService.addAiMessage(threadId, runId, aiMessage);

                // Check for tool execution requests
                if (aiMessage.hasToolExecutionRequests()) {
                    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                    logger.info("Agent requested {} tool call(s) at iteration {}", toolRequests.size(), iteration);

                    List<String> toolResults = new ArrayList<>();

                    for (ToolExecutionRequest toolRequest : toolRequests) {
                        String toolName = toolRequest.name();
                        String toolArgs = toolRequest.arguments();

                        // Gap #4: Track consecutive same-tool calls
                        if (toolName.equals(lastToolName)) {
                            consecutiveSameToolCalls++;
                        } else {
                            consecutiveSameToolCalls = 0;
                            lastToolName = toolName;
                        }

                        // Emit tool call event to UI
                        emitProgress(progressCallback, "tool_call",
                                String.format("🔧 Calling tool: **%s**", toolName),
                                iteration, maxIterations);

                        // Execute the tool with durable idempotency and trajectory recording.
                        long toolStarted = System.currentTimeMillis();
                        String idempotencyKey = sha256(toolName + "\n" + Optional.ofNullable(toolArgs).orElse("{}"));
                        if (databaseService != null && runId != null && isMutatingTool(toolName)
                                && databaseService.hasCompletedStep(runId, idempotencyKey)) {
                            toolResults.add("SUCCESS: Previously completed tool step was not repeated.");
                            continue;
                        }
                        String toolResult = executeToolCall(toolName, toolArgs, repoPath, vuln);
                        long toolDuration = System.currentTimeMillis() - toolStarted;
                        String resultStatus = isSuccessfulToolResult(toolName, toolResult) ? "SUCCESS" : "FAILURE";
                        persistToolStep(runId, iteration, toolName, toolArgs, toolResult, resultStatus,
                                toolDuration, idempotencyKey);

                        // Gap #7: Track whether apply_patch was called successfully
                        if (TOOL_APPLY_PATCH.equals(toolName) && toolResult.startsWith("SUCCESS")) {
                            patchApplied = true;
                            hasCalledApplyPatch = true;
                            persistRunChange(runId, toolArgs, toolResult);
                        } else if (TOOL_ROLLBACK_FILE.equals(toolName) && toolResult.startsWith("SUCCESS")) {
                            patchApplied = false;
                            compilePassed = false;
                            rescanPassed = false;
                        } else if (TOOL_COMPILE_PROJECT.equals(toolName)) {
                            boolean compileSkipped = toolResult.startsWith("BUILD SKIPPED");
                            compilePassed = toolResult.startsWith("BUILD SUCCESS") || compileSkipped;
                            persistVerification(runId, "BUILD", compilePassed, compileSkipped, toolResult);
                        } else if (TOOL_RESCAN_FILE.equals(toolName)) {
                            rescanPassed = toolResult.startsWith("Rescan complete: 0 vulnerabilities");
                            persistVerification(runId, "RESCAN", rescanPassed, toolResult);
                        } else if (TOOL_RUN_TESTS.equals(toolName)) {
                            testsAttempted = true;
                            boolean testsSkipped = toolResult.startsWith("TEST SKIPPED");
                            testsPassed = toolResult.startsWith("TEST SUCCESS") || testsSkipped;
                            persistVerification(runId, "TEST", testsPassed, testsSkipped, toolResult);
                        }

                        // Emit tool result event (truncated for UI)
                        String uiResult = toolResult.length() > 500
                                ? toolResult.substring(0, 500) + "... [truncated]"
                                : toolResult;
                        emitProgress(progressCallback, "tool_result",
                                String.format("📋 **%s** result: %s", toolName, uiResult),
                                iteration, maxIterations);

                        // Gap #2: Cap tool result for memory storage
                        int maxResultChars = agentConfig.getMaxToolResultChars();
                        String memoryResult = toolResult.length() > maxResultChars
                                ? toolResult.substring(0, maxResultChars) +
                                        "\n... [truncated to " + maxResultChars + " chars]"
                                : toolResult;
                        toolResults.add(memoryResult);

                        // Gap #8: Record in trajectory
                        trajectory.add(trajectoryStep(iteration, toolName, toolArgs, uiResult));
                    }

                    // Add all tool results to conversation memory
                    memoryService.addToolResults(threadId, runId, toolRequests, toolResults);

                    // Gap #4: Stuck detection — inject nudge if needed
                    injectStuckNudgeIfNeeded(threadId, runId, iteration, maxIterations,
                            consecutiveSameToolCalls, lastToolName, hasCalledApplyPatch, progressCallback);

                } else {
                    // No tool calls → agent is done reasoning
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

                    // Gap #8: Record final response in trajectory
                    trajectory.add(trajectoryStep(iteration, "FINAL_RESPONSE", null,
                            finalResponse.length() > 200 ? finalResponse.substring(0, 200) + "..." : finalResponse));

                    logger.info("Agent loop completed after {} iterations for vuln {}", iteration, vuln.getId());
                    emitProgress(progressCallback, "agent_complete",
                            "✅ Agent completed analysis and remediation.", iteration, maxIterations);
                    break;
                }

            } catch (Exception e) {
                logger.error("Error in agent loop iteration {} for vuln {}", iteration, vuln.getId(), e);
                emitProgress(progressCallback, "agent_error",
                        "⚠️ Error in iteration " + iteration + ": " + e.getMessage(),
                        iteration, maxIterations);
                memoryService.addUserMessage(threadId, runId,
                        "An error occurred during tool execution: " + e.getMessage() +
                                ". Please try a different approach.");

                trajectory.add(trajectoryStep(iteration, "ERROR", null, e.getMessage()));
            }
        }

        if (finalResponse == null) {
            hitMaxIterations = true;
            finalResponse = "Agent reached maximum iterations (" + maxIterations +
                    ") without producing a final response. The last applied changes may need manual review.";
            logger.warn("Agent reached max iterations for vuln {}", vuln.getId());
            reflectOnFailure(vuln, trajectory, runId, threadId);
        }

        // Gap #8: Log the full trajectory
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

    private void reflectOnFailure(Vulnerability vuln, List<Map<String, Object>> trajectory, String runId,
            String threadId) {
        // Run asynchronously so the agent loop can checkpoint and return immediately
        final List<Map<String, Object>> cappedTrajectory = trajectory.size() > 5
                ? trajectory.subList(trajectory.size() - 5, trajectory.size())
                : trajectory;
        Thread.startVirtualThread(() -> {
            try {
                String trajectoryJson = objectMapper.writeValueAsString(cappedTrajectory);
                // Cap the serialized trajectory to avoid exceeding model context
                if (trajectoryJson.length() > 8000) {
                    trajectoryJson = trajectoryJson.substring(0, 8000) + "\n... [truncated]";
                }
                String promptText = "You are a senior security engineer. An automated remediation agent tried to fix " +
                        "a vulnerability but failed after maximum iterations.\n" +
                        "Vulnerability Rule ID: " + vuln.getRuleId() + "\n" +
                        "Vulnerability Type: " + vuln.getVulnType() + "\n" +
                        "Write a concise negative anti-pattern warning (max 500 characters) for future agents " +
                        "explaining exactly what NOT to do for this rule ID. Be specific. " +
                        "Do NOT include any proprietary code, variable names, file paths, or class names.\n\n" +
                        "Last 5 iterations of the failed trajectory:\n" + trajectoryJson;

                org.springframework.ai.chat.prompt.Prompt prompt = new org.springframework.ai.chat.prompt.Prompt(
                        promptText);
                org.springframework.ai.chat.model.ChatResponse aiResp = chatModel.call(prompt);
                if (aiResp.getMetadata() != null && aiResp.getMetadata().getUsage() != null
                        && aiResp.getMetadata().getUsage().getTotalTokens() != null) {
                    recordTokens(getChatModelName(), aiResp.getMetadata().getUsage().getTotalTokens().intValue(),
                            threadId, runId, vuln != null ? vuln.getId() : null);
                }
                String negativePattern = aiResp.getResult().getOutput().getText();
                // Redact and cap before persistence
                final String redactedNegative = memoryRedactor.redactAndCap(negativePattern, 2000);

                databaseService.withTransaction(conn -> {
                    try {
                        databaseService.saveGlobalPatternTx(conn, vuln.getRuleId(), null, redactedNegative, runId);
                    } catch (Exception e) {
                        logger.warn("Failed to save negative pattern for rule {}", vuln.getRuleId(), e);
                    }
                });
                logger.debug("Saved negative reflection pattern for rule {}", vuln.getRuleId());
            } catch (Exception e) {
                logger.warn("Failure reflection failed for rule {}", vuln.getRuleId(), e);
            }
        });
    }

    public void summarizeDiffForGlobalPattern(Vulnerability vuln, String diff, String runId, String threadId) {
        try {
            // Cap diff to avoid exceeding model context window
            String cappedDiff = diff.length() > 10_000 ? diff.substring(0, 10_000) + "\n... [truncated]" : diff;
            String promptText = "You are a senior security engineer. A developer has fixed a vulnerability in a pull request.\n"
                    +
                    "Vulnerability Rule ID: " + vuln.getRuleId() + "\n" +
                    "Vulnerability Type: " + vuln.getVulnType() + "\n" +
                    "Write a concise, abstract positive remediation pattern (max 500 characters) explaining " +
                    "how to fix this type of vulnerability. Do NOT include any proprietary code, variable names, " +
                    "file paths, or class names. Focus purely on the security mechanism.\n\n" +
                    "Diff:\n" + cappedDiff;

            org.springframework.ai.chat.prompt.Prompt prompt = new org.springframework.ai.chat.prompt.Prompt(
                    promptText);
            org.springframework.ai.chat.model.ChatResponse aiResp = chatModel.call(prompt);
            if (aiResp.getMetadata() != null && aiResp.getMetadata().getUsage() != null
                    && aiResp.getMetadata().getUsage().getTotalTokens() != null) {
                recordTokens(getChatModelName(), aiResp.getMetadata().getUsage().getTotalTokens().intValue(), threadId,
                        runId, vuln != null ? vuln.getId() : null);
            }
            String positivePattern = aiResp.getResult().getOutput().getText();
            // Redact and cap before persistence
            final String redactedPositive = memoryRedactor.redactAndCap(positivePattern, 2000);

            databaseService.withTransaction(conn -> {
                try {
                    databaseService.saveGlobalPatternTx(conn, vuln.getRuleId(), redactedPositive, null, runId);
                } catch (Exception e) {
                    logger.warn("Failed to save positive pattern for rule {}", vuln.getRuleId(), e);
                }
            });
            logger.debug("Saved positive reflection pattern for rule {}", vuln.getRuleId());
        } catch (Exception e) {
            logger.warn("Positive reflection failed for rule {}", vuln.getRuleId(), e);
        }
    }

    // =========================================================================
    // Gap #4: Stuck Detection & Mid-Loop Nudging
    // =========================================================================

    private void injectStuckNudgeIfNeeded(String threadId, String runId, int iteration, int maxIterations,
            int consecutiveSameToolCalls, String lastToolName,
            boolean hasCalledApplyPatch,
            Consumer<Map<String, Object>> progressCallback) {
        // Nudge if calling the same tool 3+ times in a row
        if (consecutiveSameToolCalls >= 3) {
            String nudge = "You've called '" + lastToolName + "' " + (consecutiveSameToolCalls + 1) +
                    " times consecutively. Consider whether you have enough context and should move " +
                    "to the next phase (patching, compiling, or rescanning).";
            memoryService.addUserMessage(threadId, runId, nudge);
            logger.info("Stuck nudge injected at iteration {}: consecutive {} calls", iteration, lastToolName);
            emitProgress(progressCallback, "agent_nudge",
                    "🔄 Agent nudged: repetitive tool calls detected.", iteration, maxIterations);
        }

        // Nudge if past halfway and haven't patched yet
        if (iteration > maxIterations * 2 / 3 && !hasCalledApplyPatch) {
            String nudge = "You are at iteration " + iteration + " of " + maxIterations +
                    " (past the 2/3 mark). You have NOT yet applied a patch. " +
                    "Please prioritize applying a fix now — you may not have enough iterations " +
                    "remaining for extensive exploration.";
            memoryService.addUserMessage(threadId, runId, nudge);
            logger.info("Budget nudge injected at iteration {}: no patch applied yet", iteration);
            emitProgress(progressCallback, "agent_nudge",
                    "⏰ Agent nudged: past 2/3 budget without a patch.", iteration, maxIterations);
        }
    }

    // =========================================================================
    // Gap #8: Structured Trajectory Logging
    // =========================================================================

    private Map<String, Object> trajectoryStep(int iteration, String action, String args, String resultPreview) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("iteration", iteration);
        step.put("action", action);
        step.put("timestamp", Instant.now().toString());
        if (args != null) {
            step.put("args_preview", args.length() > 200 ? args.substring(0, 200) + "..." : args);
        }
        if (resultPreview != null) {
            step.put("result_preview", resultPreview);
        }
        return step;
    }

    private void logTrajectory(String vulnId, List<Map<String, Object>> trajectory) {
        List<String> actions = trajectory.stream().map(step -> String.valueOf(step.get("action"))).toList();
        logger.info("Agent trajectory for {} completed with {} step(s): {}", vulnId, trajectory.size(), actions);
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

    public boolean isSuccessfulToolResult(String toolName, String result) {
        if (result == null)
            return false;
        return switch (toolName) {
            case TOOL_COMPILE_PROJECT -> result.startsWith("BUILD SUCCESS") || result.startsWith("BUILD SKIPPED");
            case TOOL_RUN_TESTS -> result.startsWith("TEST SUCCESS") || result.startsWith("TEST SKIPPED");
            case TOOL_RESCAN_FILE -> result.startsWith("Rescan complete:");
            case TOOL_RUN_LINTER -> result.startsWith("LINT SUCCESS") || result.startsWith("LINT SKIPPED");
            case TOOL_RUN_STATIC_ANALYSIS ->
                result.startsWith("ANALYSIS SUCCESS") || result.startsWith("ANALYSIS SKIPPED");
            default -> result.startsWith("SUCCESS") || !result.startsWith("ERROR");
        };
    }

    public boolean isMutatingTool(String toolName) {
        return TOOL_APPLY_PATCH.equals(toolName) || TOOL_ROLLBACK_FILE.equals(toolName);
    }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not create idempotency key", e);
        }
    }

    // =========================================================================
    // Tool Specifications (LangChain4j — schema only, no execution)
    // =========================================================================

    public List<ToolSpecification> buildToolSpecifications() {
        return List.of(
                ToolSpecification.builder()
                        .name(TOOL_READ_FILE)
                        .description("Read the contents of a file. Use startLine=0, endLine=0 to read the entire file.")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("filePath", "Relative path to the file from the repository root")
                                .addIntegerProperty("startLine", "Start line (1-indexed). Use 0 for beginning of file.")
                                .addIntegerProperty("endLine", "End line (1-indexed). Use 0 for end of file.")
                                .required("filePath")
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_SEARCH_CODEBASE)
                        .description(
                                "Search the codebase for a text pattern. Returns matching file paths and line numbers.")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("query", "The text pattern to search for")
                                .addStringProperty("fileGlob", "Optional file glob filter, e.g. '*.java'")
                                .required("query")
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_APPLY_PATCH)
                        .description("Apply a code change to a file by replacing exact text. " +
                                "A backup is created automatically before modification.")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("filePath", "Relative path to the file")
                                .addStringProperty("originalCode",
                                        "Exact code text to find and replace, including whitespace")
                                .addStringProperty("replacementCode", "New code to replace the original with")
                                .required("filePath", "originalCode", "replacementCode")
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_COMPILE_PROJECT)
                        .description(
                                "Compile the project to check for build errors. Returns BUILD SUCCESS or BUILD FAILURE with details.")
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_RESCAN_FILE)
                        .description("Re-run the security scanner (Semgrep) on a specific file to verify " +
                                "whether vulnerabilities have been resolved.")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("filePath", "Relative path to the file to rescan")
                                .required("filePath")
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_RUN_TESTS)
                        .description("Run unit tests to check for regressions. Leave testGlob empty to run all tests.")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("testGlob",
                                        "Optional test class name filter, e.g. 'UserControllerTest'")
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_ROLLBACK_FILE)
                        .description(
                                "Rollback a file to its state before the last patch was applied. Use if a patch caused issues.")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("filePath", "Relative path to the file to rollback")
                                .required("filePath")
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_RUN_LINTER)
                        .description(
                                "Run a deterministic linter (e.g. Checkstyle) to check formatting, syntax, and style regressions.")
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_RUN_STATIC_ANALYSIS)
                        .description(
                                "Run a deterministic static analysis tool (e.g. PMD) to check types, symbols, flow, and logic.")
                        .build(),

                ToolSpecification.builder()
                        .name(TOOL_SEARCH_REJECTED_MEMORIES)
                        .description(
                                "Search for previously rejected fixes for this specific vulnerability to learn what NOT to do. Call this tool to avoid repeating past mistakes.")
                        .build());
    }

    // =========================================================================
    // Tool Execution — Single dispatch point
    // =========================================================================

    public String executeToolCall(String toolName, String argsJson, String repoPath, Vulnerability vulnerability) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (argsJson != null && !argsJson.isBlank())
                    ? objectMapper.readValue(argsJson, Map.class)
                    : Map.of();

            return switch (toolName) {
                case TOOL_READ_FILE -> {
                    String filePath = getStringArg(args, "filePath");
                    if (filePath == null)
                        yield "ERROR: Missing required argument 'filePath'.";
                    int startLine = args.containsKey("startLine") ? ((Number) args.get("startLine")).intValue() : 0;
                    int endLine = args.containsKey("endLine") ? ((Number) args.get("endLine")).intValue() : 0;
                    yield agentToolService.readFile(repoPath, filePath, startLine, endLine);
                }
                case TOOL_SEARCH_CODEBASE -> {
                    String query = getStringArg(args, "query");
                    if (query == null)
                        yield "ERROR: Missing required argument 'query'.";
                    String fileGlob = getStringArg(args, "fileGlob", "");
                    yield agentToolService.searchCodebase(repoPath, query, fileGlob);
                }
                case TOOL_APPLY_PATCH -> {
                    String filePath = getStringArg(args, "filePath");
                    String originalCode = getStringArg(args, "originalCode");
                    String replacementCode = getStringArg(args, "replacementCode");
                    if (filePath == null || originalCode == null || replacementCode == null)
                        yield "ERROR: Missing required arguments. 'filePath', 'originalCode', and 'replacementCode' are all required.";
                    yield agentToolService.applyPatch(repoPath, filePath, originalCode, replacementCode);
                }
                case TOOL_COMPILE_PROJECT -> agentToolService.compileProject(repoPath);
                case TOOL_RESCAN_FILE -> {
                    String filePath = getStringArg(args, "filePath");
                    if (filePath == null)
                        yield "ERROR: Missing required argument 'filePath'.";
                    yield agentToolService.rescanFile(repoPath, filePath);
                }
                case TOOL_RUN_TESTS -> {
                    String testGlob = getStringArg(args, "testGlob", "");
                    yield agentToolService.runTests(repoPath, testGlob);
                }
                case TOOL_ROLLBACK_FILE -> {
                    String filePath = getStringArg(args, "filePath");
                    if (filePath == null)
                        yield "ERROR: Missing required argument 'filePath'.";
                    yield agentToolService.rollbackFile(repoPath, filePath);
                }
                case TOOL_RUN_LINTER -> agentToolService.runLinter(repoPath);
                case TOOL_RUN_STATIC_ANALYSIS -> agentToolService.runStaticAnalysis(repoPath);
                case TOOL_SEARCH_REJECTED_MEMORIES -> {
                    if (databaseService == null)
                        yield "ERROR: Database service is not available.";
                    List<RepositoryMemory> memories = databaseService.searchRejectedMemories(repoPath, vulnerability);
                    if (memories.isEmpty()) {
                        yield "No rejected memories found for this vulnerability. You are clear to proceed with your proposed fix.";
                    }
                    StringBuilder sb = new StringBuilder(
                            "Found past rejected fixes. DO NOT propose the same fixes again:\n\n");
                    for (int i = 0; i < memories.size(); i++) {
                        RepositoryMemory m = memories.get(i);
                        sb.append(i + 1).append(". Rejection Reason: ").append(m.summary()).append("\n")
                                .append("   Rejected Patch:\n").append(m.remediationPattern()).append("\n\n");
                    }
                    yield sb.toString();
                }
                default -> "ERROR: Unknown tool: " + toolName;
            };
        } catch (Exception e) {
            logger.error("Tool '{}' failed with {}", toolName, e.getClass().getSimpleName());
            return "ERROR executing tool '" + toolName + "'.";
        }
    }

    private String getStringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    // =========================================================================
    // Prompt Construction — includes iteration budget (Gap #3)
    // =========================================================================

    private String buildAgenticSystemPrompt(Vulnerability vuln, String repoPath) {
        Skill skill = skillManager.getSkill("patch-engineer");
        String basePrompt = Optional.ofNullable(skill)
                .map(Skill::getInstructions)
                .filter(inst -> !inst.trim().isEmpty())
                .orElse("You are a Security Remediation Agent. You have tools to read files, " +
                        "search the codebase, apply patches, compile the project, rescan for vulnerabilities, " +
                        "and run tests. Your goal is to fix the given vulnerability and verify the fix.");

        int maxIter = agentConfig.getMaxIterations();
        int urgentThreshold = maxIter * 2 / 3;

        // Gap #3: Include iteration budget awareness in the system prompt
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

    // =========================================================================
    // Progress Event Emission
    // =========================================================================

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

    // =========================================================================
    // Simple Chat (Spring AI — unchanged)
    // =========================================================================

    public String chat(String userMessage, List<com.cb.auditagent.domain.Vulnerability> findings) {
        return chat(null, null, userMessage, findings);
    }

    public String chat(String threadId, String repoPath, String userMessage,
            List<com.cb.auditagent.domain.Vulnerability> findings) {
        logger.info("Processing redacted chat message with Bedrock ({} characters)",
                userMessage == null ? 0 : userMessage.length());
        String conversationContext = "";
        if (threadId != null && !threadId.isBlank()) {
            memoryService.addChatMessage(threadId, "USER", userMessage, repoPath);
            List<ChatMessage> history = new ArrayList<>(memoryService.getHistory(threadId));
            if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
                history.remove(history.size() - 1);
            }
            conversationContext = history.stream().map(this::renderChatMessage)
                    .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining("\n"));
        }

        StringBuilder findingsContext = new StringBuilder();
        if (findings == null || findings.isEmpty()) {
            findingsContext.append(
                    "No active vulnerabilities found in the project currently. The user should run a scan first.");
        } else {
            findingsContext.append("The following vulnerabilities are currently active in the project:\n");
            for (com.cb.auditagent.domain.Vulnerability v : findings) {
                findingsContext.append(String.format(
                        "- ID: %s | Severity: %s | Type: %s | File: %s | Line: %d | Status: %s | Description: %s\n",
                        v.getId(), v.getSeverity(), v.getVulnType(), v.getFilePath(), v.getLineNumber(), v.getStatus(),
                        v.getDescription()));
            }
        }

        String systemInstructions = "You are the Intelligent Audit and Compliance Agent. You help developers find and remediate security vulnerabilities in their codebase.\n"
                +
                "You are given a list of vulnerabilities found in the project. " +
                "If the user wants to scan, fix, or inspect a vulnerability, you should guide them and insert specific command tags in your response.\n\n"
                +
                "COMMAND TAGS:\n" +
                "- To initiate a scan: [TRIGGER_SCAN]\n" +
                "- To initiate a fix/analysis for a vulnerability ID: [TRIGGER_FIX:VULN-ID] (replace VULN-ID with the exact ID from the findings list, e.g. VULN-000001). You MUST match the user's intent to one of the active findings in the list.\n\n"
                +
                "ACTIVE FINDINGS:\n" +
                findingsContext.toString() + "\n\n" +
                "RECENT CONVERSATION:\n" + conversationContext + "\n\n" +
                "If the user asks to fix a vulnerability (e.g. \"fix SQL injection\" or \"fix VULN-123456\" or \"fix the issue in Database.java\"), check the findings list. Find the one that matches best. If you find a match, say something like: \"I am going to analyze and generate a patch for VULN-123456 (SQL Injection) in Database.java. [TRIGGER_FIX:VULN-123456]\"\n"
                +
                "Always output the exact tag [TRIGGER_FIX:VULN-XXXXXX] at the end or within your response. If there are multiple potential matches, list them and ask the user to clarify which ID they want to fix.\n"
                +
                "If the user asks to scan the project/repo, include [TRIGGER_SCAN].\n" +
                "If you cannot identify which vulnerability they want to fix or if they ask a general question, explain the vulnerabilities, ask for clarification, or discuss security best practices. Be helpful, concise, and professional.";

        try {
            org.springframework.ai.chat.messages.SystemMessage systemMsg = new org.springframework.ai.chat.messages.SystemMessage(
                    systemInstructions);
            org.springframework.ai.chat.messages.UserMessage userMsg = new org.springframework.ai.chat.messages.UserMessage(
                    userMessage);
            Prompt prompt = new Prompt(List.of(systemMsg, userMsg));

            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null
                    && response.getMetadata().getUsage().getTotalTokens() != null) {
                recordTokens(getChatModelName(), response.getMetadata().getUsage().getTotalTokens().intValue(),
                        threadId, null, null);
            }
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return "Agent error: received empty response from LLM.";
            }
            String answer = response.getResult().getOutput().getText();
            persistChatAnswer(threadId, answer, repoPath);
            return answer;
        } catch (Exception e) {
            logger.error("Chat generation failed with {}", e.getClass().getSimpleName());
            return "Error: Could not process the chat message through Amazon Bedrock.";
        }
    }

    private void persistChatAnswer(String threadId, String answer, String repoPath) {
        if (threadId != null && !threadId.isBlank()) {
            Thread.startVirtualThread(() -> {
                try {
                    memoryService.addChatMessage(threadId, "AI", answer, repoPath);
                } catch (Exception e) {
                    logger.warn("Failed to persist assistant chat message for thread {}", threadId, e);
                }
            });
        }
    }

    @SuppressWarnings("unused")
    private void persistChatAnswer(String threadId, String answer) {
        persistChatAnswer(threadId, answer, "");
    }

    public Flux<String> chatStream(String threadId, String repoPath, String userMessage,
            List<com.cb.auditagent.domain.Vulnerability> findings) {
        logger.info("Processing redacted chat message with Bedrock ({} characters) - Streaming",
                userMessage == null ? 0 : userMessage.length());
        String conversationContext = "";
        if (threadId != null && !threadId.isBlank()) {
            memoryService.addChatMessage(threadId, "USER", userMessage, repoPath);
            List<ChatMessage> history = new ArrayList<>(memoryService.getHistory(threadId));
            if (!history.isEmpty() && history.get(history.size() - 1) instanceof UserMessage) {
                history.remove(history.size() - 1);
            }
            conversationContext = history.stream().map(this::renderChatMessage)
                    .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining("\n"));
        }

        StringBuilder findingsContext = new StringBuilder();
        if (findings == null || findings.isEmpty()) {
            findingsContext.append(
                    "No active vulnerabilities found in the project currently. The user should run a scan first.");
        } else {
            findingsContext.append("The following vulnerabilities are currently active in the project:\n");
            for (com.cb.auditagent.domain.Vulnerability v : findings) {
                findingsContext.append(String.format(
                        "- ID: %s | Severity: %s | Type: %s | File: %s | Line: %d | Status: %s | Description: %s\n",
                        v.getId(), v.getSeverity(), v.getVulnType(), v.getFilePath(), v.getLineNumber(), v.getStatus(),
                        v.getDescription()));
            }
        }

        String systemInstructions = "You are the Intelligent Audit and Compliance Agent. You help developers find and remediate security vulnerabilities in their codebase.\n"
                +
                "You are given a list of vulnerabilities found in the project. " +
                "If the user wants to scan, fix, or inspect a vulnerability, you should guide them and insert specific command tags in your response.\n\n"
                +
                "COMMAND TAGS:\n" +
                "- To initiate a scan: [TRIGGER_SCAN]\n" +
                "- To initiate a fix/analysis for a vulnerability ID: [TRIGGER_FIX:VULN-ID] (replace VULN-ID with the exact ID from the findings list, e.g. VULN-000001). You MUST match the user's intent to one of the active findings in the list.\n\n"
                +
                "ACTIVE FINDINGS:\n" +
                findingsContext.toString() + "\n\n" +
                "RECENT CONVERSATION:\n" + conversationContext + "\n\n" +
                "If the user asks to fix a vulnerability (e.g. \"fix SQL injection\" or \"fix VULN-123456\" or \"fix the issue in Database.java\"), check the findings list. Find the one that matches best. If you find a match, say something like: \"I am going to analyze and generate a patch for VULN-123456 (SQL Injection) in Database.java. [TRIGGER_FIX:VULN-123456]\"\n"
                +
                "Always output the exact tag [TRIGGER_FIX:VULN-XXXXXX] at the end or within your response. If there are multiple potential matches, list them and ask the user to clarify which ID they want to fix.\n"
                +
                "If the user asks to scan the project/repo, include [TRIGGER_SCAN].\n" +
                "If you cannot identify which vulnerability they want to fix or if they ask a general question, explain the vulnerabilities, ask for clarification, or discuss security best practices. Be helpful, concise, and professional.";

        try {
            org.springframework.ai.chat.messages.SystemMessage systemMsg = new org.springframework.ai.chat.messages.SystemMessage(
                    systemInstructions);
            org.springframework.ai.chat.messages.UserMessage userMsg = new org.springframework.ai.chat.messages.UserMessage(
                    userMessage);
            Prompt prompt = new Prompt(List.of(systemMsg, userMsg));

            StringBuilder fullResponse = new StringBuilder();
            return chatModel.stream(prompt)
                    .map(response -> {
                        if (response != null && response.getMetadata() != null
                                && response.getMetadata().getUsage() != null
                                && response.getMetadata().getUsage().getTotalTokens() != null
                                && response.getMetadata().getUsage().getTotalTokens() > 0) {
                            recordTokens(getChatModelName(),
                                    response.getMetadata().getUsage().getTotalTokens().intValue(), threadId, null,
                                    null);
                        }
                        if (response == null || response.getResult() == null
                                || response.getResult().getOutput() == null) {
                            return "";
                        }
                        String text = response.getResult().getOutput().getText();
                        if (text != null) {
                            fullResponse.append(text);
                            return text;
                        }
                        return "";
                    })
                    .filter(text -> !text.isEmpty())
                    .doOnComplete(() -> {
                        persistChatAnswer(threadId, fullResponse.toString().trim(), repoPath);
                    })
                    .onErrorResume(e -> {
                        logger.error("Chat streaming failed with {}", e.getClass().getSimpleName());
                        String errorMsg = "Error: Could not process the chat message through Amazon Bedrock.";
                        persistChatAnswer(threadId, errorMsg, repoPath);
                        return Flux.just(errorMsg);
                    });
        } catch (Exception e) {
            logger.error("Chat response generation failed with {}", e.getClass().getSimpleName());
            String errorMsg = "Error: Could not process the chat message through Amazon Bedrock.";
            persistChatAnswer(threadId, errorMsg, repoPath);
            return Flux.just(errorMsg);
        }
    }

    private String renderChatMessage(ChatMessage message) {
        if (message instanceof SystemMessage system)
            return "SYSTEM: " + system.text();
        if (message instanceof UserMessage user)
            return "USER: " + user.singleText();
        if (message instanceof AiMessage ai)
            return "ASSISTANT: " + Optional.ofNullable(ai.text()).orElse("");
        if (message instanceof ToolExecutionResultMessage tool)
            return "TOOL: " + tool.text();
        return "";
    }

}
