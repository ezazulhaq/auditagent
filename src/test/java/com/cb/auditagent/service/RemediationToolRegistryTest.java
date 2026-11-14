package com.cb.auditagent.service;

import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemediationToolRegistryTest {

    private AgentToolService mockAgentToolService;
    private DatabaseService mockDatabaseService;
    private ObjectMapper objectMapper;
    private RemediationToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        mockAgentToolService = mock(AgentToolService.class);
        mockDatabaseService = mock(DatabaseService.class);
        objectMapper = new ObjectMapper();

        toolRegistry = new RemediationToolRegistry(
                mockAgentToolService,
                mockDatabaseService,
                objectMapper
        );
    }

    @Test
    @DisplayName("buildToolSpecifications registers all 10 core remediation tools")
    void testBuildToolSpecifications() {
        List<ToolSpecification> specs = toolRegistry.buildToolSpecifications();
        assertEquals(10, specs.size());

        List<String> names = specs.stream().map(ToolSpecification::name).toList();
        assertTrue(names.contains(RemediationToolRegistry.TOOL_READ_FILE));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_SEARCH_CODEBASE));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_APPLY_PATCH));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_COMPILE_PROJECT));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_RESCAN_FILE));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_RUN_TESTS));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_ROLLBACK_FILE));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_RUN_LINTER));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_RUN_STATIC_ANALYSIS));
        assertTrue(names.contains(RemediationToolRegistry.TOOL_SEARCH_REJECTED_MEMORIES));
    }

    @Test
    @DisplayName("executeToolCall validates missing parameters and serializes errors")
    void testExecuteToolCallMissingArguments() {
        // Missing filePath for read_file
        String result = toolRegistry.executeToolCall("read_file", "{}", "/workspace", null);
        assertEquals("ERROR: Missing required argument 'filePath'.", result);

        // Missing originalCode for apply_patch
        String patchResult = toolRegistry.executeToolCall("apply_patch", "{\"filePath\": \"A.java\"}", "/workspace", null);
        assertTrue(patchResult.startsWith("ERROR: Missing required arguments"));

        // Unknown tool
        String unknownResult = toolRegistry.executeToolCall("non_existent_tool", "{}", "/workspace", null);
        assertEquals("ERROR: Unknown tool: non_existent_tool", unknownResult);
    }

    @Test
    @DisplayName("executeToolCall dispatches valid calls to AgentToolService")
    void testExecuteToolCallDispatches() {
        when(mockAgentToolService.readFile("/workspace", "src/Main.java", 1, 10))
                .thenReturn("public static void main...");

        String readResult = toolRegistry.executeToolCall(
                "read_file",
                "{\"filePath\": \"src/Main.java\", \"startLine\": 1, \"endLine\": 10}",
                "/workspace",
                null
        );
        assertEquals("public static void main...", readResult);

        when(mockAgentToolService.compileProject("/workspace")).thenReturn("BUILD SUCCESS");
        String compileResult = toolRegistry.executeToolCall("compile_project", "{}", "/workspace", null);
        assertEquals("BUILD SUCCESS", compileResult);
    }

    @Test
    @DisplayName("executeToolCall queries database for rejected memories")
    void testExecuteToolCallSearchRejectedMemories() {
        Vulnerability vuln = new Vulnerability("VULN-001", "A.java", 5, "code", Severity.HIGH, "SQLI", "desc", "java");
        RepositoryMemory memory = new RepositoryMemory(
                "mem-1", "/workspace", "fp-1", "rule-1", "SQLI",
                "SQL_INJECTION", "java", "spring", "*.java",
                "Title", "Rejection summary", "Root cause", "Bad patch code",
                "search text", 0.9, "run-1", 0, java.time.LocalDateTime.now(), 0.9
        );
        when(mockDatabaseService.searchRejectedMemories("/workspace", vuln)).thenReturn(List.of(memory));

        String result = toolRegistry.executeToolCall("search_rejected_memories", "{}", "/workspace", vuln);
        assertTrue(result.contains("Found past rejected fixes. DO NOT propose the same fixes again"));
        assertTrue(result.contains("Rejection summary"));
        assertTrue(result.contains("Bad patch code"));
    }

    @Test
    @DisplayName("isSuccessfulToolResult accurately classifies tool return codes")
    void testIsSuccessfulToolResult() {
        assertTrue(toolRegistry.isSuccessfulToolResult("compile_project", "BUILD SUCCESS in 2s"));
        assertTrue(toolRegistry.isSuccessfulToolResult("compile_project", "BUILD SKIPPED (no pom.xml)"));
        assertFalse(toolRegistry.isSuccessfulToolResult("compile_project", "BUILD FAILURE"));

        assertTrue(toolRegistry.isSuccessfulToolResult("run_tests", "TEST SUCCESS (15 tests passed)"));
        assertTrue(toolRegistry.isSuccessfulToolResult("run_tests", "TEST SKIPPED (no test files)"));
        assertFalse(toolRegistry.isSuccessfulToolResult("run_tests", "TEST FAILURE (1 failed)"));

        assertTrue(toolRegistry.isSuccessfulToolResult("rescan_file", "Rescan complete: 0 vulnerabilities found."));
        assertFalse(toolRegistry.isSuccessfulToolResult("rescan_file", "Rescan failed: parser error"));

        assertTrue(toolRegistry.isSuccessfulToolResult("read_file", "SUCCESS"));
        assertFalse(toolRegistry.isSuccessfulToolResult("read_file", "ERROR: file not found"));
    }

    @Test
    @DisplayName("isMutatingTool identifies tools that modify files")
    void testIsMutatingTool() {
        assertTrue(toolRegistry.isMutatingTool("apply_patch"));
        assertTrue(toolRegistry.isMutatingTool("rollback_file"));

        assertFalse(toolRegistry.isMutatingTool("read_file"));
        assertFalse(toolRegistry.isMutatingTool("search_codebase"));
        assertFalse(toolRegistry.isMutatingTool("compile_project"));
        assertFalse(toolRegistry.isMutatingTool("run_tests"));
    }

    @Test
    @DisplayName("sha256 computes reproducible hashes for tool idempotency")
    void testSha256() {
        String hash1 = toolRegistry.sha256("apply_patch\n{\"filePath\":\"A.java\"}");
        String hash2 = toolRegistry.sha256("apply_patch\n{\"filePath\":\"A.java\"}");
        String hash3 = toolRegistry.sha256("apply_patch\n{\"filePath\":\"B.java\"}");

        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
        assertEquals(64, hash1.length());
    }
}
