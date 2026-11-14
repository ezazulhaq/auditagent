package com.cb.auditagent.service;

import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.Vulnerability;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry and execution dispatcher for remediation tools used by LangChain4j agents.
 * Extracted from LlmService during Phase 3 decomposition.
 */
@Service
public class RemediationToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RemediationToolRegistry.class);

    public static final String TOOL_READ_FILE = "read_file";
    public static final String TOOL_SEARCH_CODEBASE = "search_codebase";
    public static final String TOOL_APPLY_PATCH = "apply_patch";
    public static final String TOOL_COMPILE_PROJECT = "compile_project";
    public static final String TOOL_RESCAN_FILE = "rescan_file";
    public static final String TOOL_RUN_TESTS = "run_tests";
    public static final String TOOL_ROLLBACK_FILE = "rollback_file";
    public static final String TOOL_RUN_LINTER = "run_linter";
    public static final String TOOL_RUN_STATIC_ANALYSIS = "run_static_analysis";
    public static final String TOOL_SEARCH_REJECTED_MEMORIES = "search_rejected_memories";

    private static final Set<String> MUTATING_TOOLS = Set.of(
            TOOL_APPLY_PATCH,
            TOOL_ROLLBACK_FILE
    );

    private final AgentToolService agentToolService;
    private final DatabaseService databaseService;
    private final ObjectMapper objectMapper;

    public RemediationToolRegistry(
            AgentToolService agentToolService,
            @Autowired(required = false) DatabaseService databaseService,
            ObjectMapper objectMapper) {
        this.agentToolService = agentToolService;
        this.databaseService = databaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds standard LangChain4j ToolSpecifications for agentic workflows.
     */
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

    /**
     * Dispatches a tool invocation and returns the formatted execution result.
     */
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
        return MUTATING_TOOLS.contains(toolName);
    }

    public boolean isValidToolName(String toolName) {
        if (toolName == null) return false;
        return switch (toolName) {
            case TOOL_READ_FILE, TOOL_SEARCH_CODEBASE, TOOL_APPLY_PATCH,
                 TOOL_COMPILE_PROJECT, TOOL_RESCAN_FILE, TOOL_RUN_TESTS,
                 TOOL_ROLLBACK_FILE, TOOL_RUN_LINTER, TOOL_RUN_STATIC_ANALYSIS,
                 TOOL_SEARCH_REJECTED_MEMORIES -> true;
            default -> false;
        };
    }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not create idempotency key", e);
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
}
