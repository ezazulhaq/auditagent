package com.cb.auditagent.graph.node;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.LlmService;
import com.cb.auditagent.service.RepositoryMemoryService;

import java.util.*;

@Component
public class ToolExecutionNode implements NodeAction<RemediationState> {
    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionNode.class);

    private final AgentConfig agentConfig;
    private final ConversationMemoryService memoryService;
    private final LlmService llmService;
    private final RepositoryMemoryService repositoryMemoryService;

    public ToolExecutionNode(AgentConfig agentConfig,
            ConversationMemoryService memoryService,
            @Lazy LlmService llmService,
            RepositoryMemoryService repositoryMemoryService) {
        this.agentConfig = agentConfig;
        this.memoryService = memoryService;
        this.llmService = llmService;
        this.repositoryMemoryService = repositoryMemoryService;
    }

    @Override
    public Map<String, Object> apply(RemediationState state) throws Exception {
        String runId = state.getRunId();
        String threadId = state.getThreadId();
        int iteration = state.getIteration();
        String repoPath = state.getRepoPath();

        logger.info("Executing ToolExecutionNode for run {} at iteration {}", runId, iteration);

        List<ChatMessage> history = memoryService.getHistory(threadId, runId);
        AiMessage lastMessage = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof AiMessage ai) {
                lastMessage = ai;
                break;
            }
        }

        if (lastMessage == null || !lastMessage.hasToolExecutionRequests()) {
            return Collections.emptyMap(); // Shouldn't happen based on graph routing
        }

        List<ToolExecutionRequest> toolRequests = lastMessage.toolExecutionRequests();
        List<String> toolResults = new ArrayList<>();
        List<Map<String, Object>> trajectory = state.getTrajectory() != null ? new ArrayList<>(state.getTrajectory())
                : new ArrayList<>();

        int consecutiveSameToolCalls = state.getConsecutiveSameToolCalls();
        String lastToolName = state.getLastToolName();
        boolean patchApplied = state.isPatchApplied();
        boolean hasCalledApplyPatch = state.isHasCalledApplyPatch();
        boolean compilePassed = state.isCompilePassed();
        boolean rescanPassed = state.isRescanPassed();
        boolean testsPassed = state.isTestsPassed();
        boolean testsAttempted = state.isTestsAttempted();

        for (ToolExecutionRequest toolRequest : toolRequests) {
            String toolName = toolRequest.name();
            String toolArgs = toolRequest.arguments();

            if (toolName.equals(lastToolName)) {
                consecutiveSameToolCalls++;
            } else {
                consecutiveSameToolCalls = 0;
                lastToolName = toolName;
            }

            long toolStarted = System.currentTimeMillis();
            String idempotencyKey = llmService.sha256(toolName + "\n" + Optional.ofNullable(toolArgs).orElse("{}"));

            String toolResult = llmService.executeToolCall(toolName, toolArgs, repoPath, state.getVulnerability());
            long toolDuration = System.currentTimeMillis() - toolStarted;
            String resultStatus = llmService.isSuccessfulToolResult(toolName, toolResult) ? "SUCCESS" : "FAILURE";

            llmService.persistToolStep(runId, iteration, toolName, toolArgs, toolResult, resultStatus, toolDuration,
                    idempotencyKey);

            if ("apply_patch".equals(toolName)) {
                if (toolResult.startsWith("SUCCESS")) {
                    patchApplied = true;
                    hasCalledApplyPatch = true;
                    llmService.persistRunChange(runId, toolArgs, toolResult);
                } else {
                    String duckDbMemory = repositoryMemoryService != null
                            ? repositoryMemoryService.retrieveForPrompt(repoPath, state.getVulnerability())
                            : "";
                    String nudge = "[HARNESS WARNING]: The patch application failed. You are likely targeting the wrong lines or your syntax is invalid. Re-read the file and ensure you are using the EXACT existing lines in your patch.\n\n"
                            +
                            (duckDbMemory.contains("No approved remediation") ? ""
                                    : "For your reference, here is knowledge base context on how to fix this type of issue:\n"
                                            + duckDbMemory);
                    memoryService.addUserMessage(threadId, runId, nudge);
                }
            } else if ("rollback_file".equals(toolName) && toolResult.startsWith("SUCCESS")) {
                patchApplied = false;
                compilePassed = false;
                rescanPassed = false;
            } else if ("compile_project".equals(toolName)) {
                boolean compileSkipped = toolResult.startsWith("BUILD SKIPPED");
                compilePassed = toolResult.startsWith("BUILD SUCCESS") || compileSkipped;
                llmService.persistVerification(runId, "BUILD", compilePassed, compileSkipped, toolResult);
            } else if ("rescan_file".equals(toolName)) {
                rescanPassed = toolResult.startsWith("Rescan complete: 0 vulnerabilities");
                llmService.persistVerification(runId, "RESCAN", rescanPassed, false, toolResult);
                if (!rescanPassed) {
                    String duckDbMemory = repositoryMemoryService != null
                            ? repositoryMemoryService.retrieveForPrompt(repoPath, state.getVulnerability())
                            : "";
                    String nudge = "[HARNESS WARNING]: The rescan failed! Your previous patch did NOT fix the vulnerability. Review the rescan output carefully. DO NOT attempt the exact same fix again. You MUST learn from this mistake and propose a fundamentally different approach.\n\n"
                            +
                            (duckDbMemory.contains("No approved remediation") ? ""
                                    : "Here is knowledge base context to help you fix this:\n" + duckDbMemory);
                    memoryService.addUserMessage(threadId, runId, nudge);
                }
            } else if ("run_tests".equals(toolName)) {
                testsAttempted = true;
                boolean testsSkipped = toolResult.startsWith("TEST SKIPPED");
                testsPassed = toolResult.startsWith("TEST SUCCESS") || testsSkipped;
                llmService.persistVerification(runId, "TEST", testsPassed, testsSkipped, toolResult);
            } else if ("run_linter".equals(toolName)) {
                boolean skipped = toolResult.startsWith("LINT SKIPPED");
                boolean passed = toolResult.startsWith("LINT SUCCESS") || skipped;
                llmService.persistVerification(runId, "LINTER", passed, skipped, toolResult);
            } else if ("run_static_analysis".equals(toolName)) {
                boolean skipped = toolResult.startsWith("ANALYSIS SKIPPED");
                boolean passed = toolResult.startsWith("ANALYSIS SUCCESS") || skipped;
                llmService.persistVerification(runId, "STATIC_ANALYSIS", passed, skipped, toolResult);
            }

            String uiResult = toolResult.length() > 500
                    ? toolResult.substring(0, 500) + "... [truncated]"
                    : toolResult;

            int maxResultChars = agentConfig.getMaxToolResultChars();
            String memoryResult = toolResult.length() > maxResultChars
                    ? toolResult.substring(0, maxResultChars) +
                            "\n... [truncated to " + maxResultChars + " chars]"
                    : toolResult;
            toolResults.add(memoryResult);

            Map<String, Object> step = new HashMap<>();
            step.put("iteration", iteration);
            step.put("action", toolName);
            step.put("args", toolArgs);
            step.put("result", uiResult);
            trajectory.add(step);
        }

        memoryService.addToolResults(threadId, runId, toolRequests, toolResults);

        Map<String, Object> updates = new HashMap<>();
        updates.put("consecutiveSameToolCalls", consecutiveSameToolCalls);
        updates.put("lastToolName", lastToolName);
        updates.put("patchApplied", patchApplied);
        updates.put("hasCalledApplyPatch", hasCalledApplyPatch);
        updates.put("compilePassed", compilePassed);
        updates.put("rescanPassed", rescanPassed);
        updates.put("testsPassed", testsPassed);
        updates.put("testsAttempted", testsAttempted);
        updates.put("trajectory", trajectory);

        return updates;
    }
}
