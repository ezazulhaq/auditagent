package com.cb.auditagent.graph.node;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.Report;
import com.cb.auditagent.domain.Skill;
import com.cb.auditagent.domain.Vulnerability;
import com.cb.auditagent.domain.VulnerabilityStatus;
import com.cb.auditagent.graph.RemediationState;
import com.cb.auditagent.service.ConversationMemoryService;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.RepositoryMemoryService;
import com.cb.auditagent.service.SkillManagerService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class InitNode implements NodeAction<RemediationState> {
    private static final Logger logger = LoggerFactory.getLogger(InitNode.class);

    private final AgentConfig agentConfig;
    private final ConversationMemoryService memoryService;
    private final RepositoryMemoryService repositoryMemoryService;
    private final SkillManagerService skillManager;
    private final DatabaseService databaseService;

    public InitNode(AgentConfig agentConfig,
            ConversationMemoryService memoryService,
            RepositoryMemoryService repositoryMemoryService,
            SkillManagerService skillManager,
            DatabaseService databaseService) {
        this.agentConfig = agentConfig;
        this.memoryService = memoryService;
        this.repositoryMemoryService = repositoryMemoryService;
        this.skillManager = skillManager;
        this.databaseService = databaseService;
    }

    @Override
    public Map<String, Object> apply(RemediationState state) throws Exception {
        String runId = state.getRunId();
        logger.info("Executing InitNode for run {}", runId);

        Vulnerability primaryVuln = state.getVulnerability();
        String repoPath = state.getRepoPath();
        String threadId = state.getThreadId();
        String repositoryMemoryKey = state.getRepositoryMemoryKey();

        // Find all vulnerabilities in the same file to fix together
        List<Vulnerability> targetVulns = List.of(primaryVuln);
        if (repositoryMemoryKey != null) {
            Optional<Report> reportOpt = databaseService.getReport(repositoryMemoryKey);
            if (reportOpt.isPresent()) {
                List<Vulnerability> fileVulns = reportOpt.get().getFindings().stream()
                        .filter(v -> v.getFilePath().equals(primaryVuln.getFilePath()))
                        .filter(v -> v.getStatus() == VulnerabilityStatus.DETECTED
                                || v.getStatus() == VulnerabilityStatus.PATCH_FAILED)
                        .collect(Collectors.toList());
                if (!fileVulns.isEmpty()) {
                    // Ensure the primary vuln is first
                    fileVulns.removeIf(v -> v.getId().equals(primaryVuln.getId()));
                    fileVulns.add(0, primaryVuln);
                    targetVulns = fileVulns;
                }
            }
        }

        // Build system prompt
        String systemPrompt = buildAgenticSystemPrompt(targetVulns, repoPath);
        if (repositoryMemoryService != null && repositoryMemoryKey != null) {
            systemPrompt += "\n\n## Approved Repository Memory\n" +
                    repositoryMemoryService.retrieveForPrompt(repositoryMemoryKey, primaryVuln);
        }

        memoryService.initConversation(threadId, runId, systemPrompt);

        // Build task prompt
        String taskPrompt = buildTaskPrompt(targetVulns);
        memoryService.addUserMessage(threadId, runId, taskPrompt);

        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("iteration", 0);
        updates.put("maxIterations", agentConfig.getMaxIterations());
        updates.put("nextAction", "call_model");
        updates.put("consecutiveEmptyResponses", 0);
        updates.put("consecutiveSameToolCalls", 0);
        updates.put("patchApplied", false);
        updates.put("hasCalledApplyPatch", false);
        updates.put("compilePassed", false);
        updates.put("rescanPassed", false);
        updates.put("testsPassed", false);
        updates.put("testsAttempted", false);

        return updates;
    }

    private String buildAgenticSystemPrompt(List<Vulnerability> vulns, String repoPath) {
        Skill skill = skillManager.getSkill("patch-engineer");
        String basePrompt = Optional.ofNullable(skill)
                .map(Skill::getInstructions)
                .filter(inst -> !inst.trim().isEmpty())
                .orElse("You are a Security Remediation Agent. You have tools to read files, " +
                        "search the codebase, apply patches, compile the project, rescan for vulnerabilities, " +
                        "and run tests. Your goal is to fix the given vulnerability and verify the fix.");

        int maxIter = agentConfig.getMaxIterations();
        int urgentThreshold = maxIter * 2 / 3;

        String vulnIds = vulns.stream().map(Vulnerability::getId).collect(Collectors.joining(", "));

        return basePrompt + "\n\n## Context\n" +
                "- Repository path: " + repoPath + "\n" +
                "- Target vulnerability IDs: " + vulnIds + "\n" +
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

    private String buildTaskPrompt(List<Vulnerability> vulns) {
        StringBuilder sb = new StringBuilder();
        if (vulns.size() == 1) {
            sb.append("Please fix this security vulnerability:\n\n");
        } else {
            sb.append("Please fix ALL of the following security vulnerabilities in this file:\n\n");
        }

        for (Vulnerability vuln : vulns) {
            String language = Optional.ofNullable(vuln.getLanguage()).orElse("Unknown");
            sb.append(String.format(
                    "**Vulnerability ID:** %s\n" +
                            "**File Path:** %s\n" +
                            "**Line Number:** %d\n" +
                            "**Language:** %s\n" +
                            "**Type:** %s\n" +
                            "**Severity:** %s\n" +
                            "**Description:** %s\n\n" +
                            "**Vulnerable Code Snippet:**\n```%s\n%s\n```\n\n---\n\n",
                    vuln.getId(),
                    vuln.getFilePath(),
                    vuln.getLineNumber(),
                    language,
                    vuln.getVulnType(),
                    vuln.getSeverity() != null ? vuln.getSeverity().name() : "UNKNOWN",
                    vuln.getDescription(),
                    language.toLowerCase(),
                    vuln.getCodeSnippet()));
        }

        sb.append("Start by reading the full file to understand the context, then fix the vulnerabilities, ")
                .append("and verify your fix compiles and the security scanner no longer flags ANY of these assigned issues.");

        return sb.toString();
    }
}
