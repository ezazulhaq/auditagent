package com.cb.auditagent.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.service.AgentToolService;
import com.cb.auditagent.service.FilePatchService;
import com.cb.auditagent.service.ManagedProcessEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolServiceSecurityTest {
    @TempDir
    Path tempDir;

    @Test
    void stripsServerSecretsAndContainsProcessRuntime() throws Exception {
        Path repository = Files.createDirectory(tempDir.resolve("process-repo"));
        ProcessBuilder processBuilder = new ProcessBuilder("noop");
        processBuilder.environment().put("AWS_SECRET_ACCESS_KEY", "aws-secret");
        processBuilder.environment().put("GITHUB_APP_PRIVATE_KEY", "github-secret");
        processBuilder.environment().put("INTERNAL_API_TOKEN", "api-secret");
        processBuilder.environment().put("JAVA_TOOL_OPTIONS", "-javaagent:outside.jar");

        ManagedProcessEnvironment.configure(processBuilder, repository);

        assertFalse(processBuilder.environment().containsKey("AWS_SECRET_ACCESS_KEY"));
        assertFalse(processBuilder.environment().containsKey("GITHUB_APP_PRIVATE_KEY"));
        assertFalse(processBuilder.environment().containsKey("INTERNAL_API_TOKEN"));
        assertFalse(processBuilder.environment().containsKey("JAVA_TOOL_OPTIONS"));
        Path home = Path.of(processBuilder.environment().get("HOME"));
        Path temporary = Path.of(processBuilder.environment().get("TEMP"));
        assertTrue(home.startsWith(repository.toRealPath()));
        assertTrue(temporary.startsWith(repository.toRealPath()));
        assertTrue(Files.isDirectory(home));
        assertTrue(Files.isDirectory(temporary));
    }

    @Test
    void rejectsLexicalPathTraversal() throws Exception {
        Path repository = Files.createDirectory(tempDir.resolve("repo"));
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "secret");
        AgentConfig config = new AgentConfig();
        AgentToolService tools = new AgentToolService(config, new FilePatchService(config),
                new com.cb.auditagent.config.ScannerConfig(), null);

        String result = tools.readFile(repository.toString(), "../outside.txt", 0, 0);

        assertTrue(result.contains("Security violation"));
        assertEquals("secret", Files.readString(outside));
    }

    @Test
    void rejectsSymbolicLinkFileEscapesWhenSupported() throws Exception {
        Path repository = Files.createDirectory(tempDir.resolve("symlink-repo"));
        Path outside = tempDir.resolve("symlink-outside.txt");
        Files.writeString(outside, "secret");
        Path link = repository.resolve("Link.java");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (Exception unsupported) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }
        AgentConfig config = new AgentConfig();
        AgentToolService tools = new AgentToolService(config, new FilePatchService(config),
                new com.cb.auditagent.config.ScannerConfig(), null);

        String result = tools.applyPatch(repository.toString(), "Link.java", "secret", "changed");

        assertTrue(result.contains("symbolic-link"));
        assertEquals("secret", Files.readString(outside));
    }

    @Test
    void rejectsBackupDirectorySymlinkEscapesWhenSupported() throws Exception {
        Path repository = Files.createDirectory(tempDir.resolve("backup-repo"));
        Path source = repository.resolve("Example.java");
        Files.writeString(source, "unsafe");
        Path outside = Files.createDirectory(tempDir.resolve("backup-outside"));
        try {
            Files.createSymbolicLink(repository.resolve(".auditagent"), outside);
        } catch (Exception unsupported) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }
        AgentConfig config = new AgentConfig();
        AgentToolService tools = new AgentToolService(config, new FilePatchService(config),
                new com.cb.auditagent.config.ScannerConfig(), null);

        String result = tools.applyPatch(repository.toString(), "Example.java", "unsafe", "safe");

        assertTrue(result.startsWith("ERROR"));
        assertEquals("unsafe", Files.readString(source));
        try (var files = Files.list(outside)) {
            assertEquals(0, files.count());
        }
    }
}
