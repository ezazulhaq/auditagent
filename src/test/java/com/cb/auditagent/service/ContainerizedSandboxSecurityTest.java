package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContainerizedSandboxSecurityTest {

    @TempDir
    Path tempDir;

    private AgentConfig agentConfig;
    private ContainerizedProcessRunner runner;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        agentConfig = new AgentConfig();
        agentConfig.setSandboxEnabled(true);
        agentConfig.setSandboxImage("auditagent-sandbox:2.7.0");
        agentConfig.setSandboxMemoryLimit("2g");
        agentConfig.setSandboxCpuLimit("2.0");

        runner = new ContainerizedProcessRunner(agentConfig);
        workspace = Files.createDirectory(tempDir.resolve("sandbox-workspace"));
    }

    @Test
    @DisplayName("buildContainerCommand applies strict resource limits and security flags")
    void buildContainerCommand_appliesResourceLimitsAndSecurityFlags() {
        List<String> command = List.of("mvn", "test", "-Dtest=UserTest");
        List<String> containerCmd = runner.buildContainerCommand(command, workspace);

        assertNotNull(containerCmd);
        assertEquals("docker", containerCmd.get(0));
        assertEquals("run", containerCmd.get(1));
        assertTrue(containerCmd.contains("--rm"), "Must include --rm for ephemeral container lifecycle");
        assertTrue(containerCmd.contains("--network=none"), "Must isolate container from external network");
        assertTrue(containerCmd.contains("--memory=2g"), "Must enforce memory resource limit");
        assertTrue(containerCmd.contains("--cpus=2.0"), "Must enforce CPU resource limit");
        assertTrue(containerCmd.contains("--security-opt=no-new-privileges"), "Must prevent privilege escalation");
        assertTrue(containerCmd.contains("--cap-drop=CAP_NET_RAW"), "Must drop CAP_NET_RAW capability");
        assertTrue(containerCmd.contains("--cap-drop=CAP_SYS_ADMIN"), "Must drop CAP_SYS_ADMIN capability");
        assertTrue(containerCmd.contains("--tmpfs"), "Must mount writable tmpfs");
        assertTrue(containerCmd.contains("/tmp:rw,noexec,size=512m"), "tmpfs must be noexec and size-capped");

        // Verify image and target command
        assertTrue(containerCmd.contains("auditagent-sandbox:2.7.0"));
        int imageIndex = containerCmd.indexOf("auditagent-sandbox:2.7.0");
        assertTrue(imageIndex > 0);
        assertEquals("mvn", containerCmd.get(imageIndex + 1));
        assertEquals("test", containerCmd.get(imageIndex + 2));
        assertEquals("-Dtest=UserTest", containerCmd.get(imageIndex + 3));
    }

    @Test
    @DisplayName("buildContainerCommand does not leak any host sensitive environment variables")
    void buildContainerCommand_doesNotLeakSensitiveEnvironmentVariables() {
        List<String> command = List.of("mvn", "compile");
        List<String> containerCmd = runner.buildContainerCommand(command, workspace);

        for (String arg : containerCmd) {
            for (String sensitiveKey : ContainerizedProcessRunner.SENSITIVE_ENV_VARS) {
                assertFalse(arg.contains(sensitiveKey),
                        "Container argument [" + arg + "] leaked sensitive variable: " + sensitiveKey);
            }
        }
    }

    @Test
    @DisplayName("buildContainerCommand correctly bounds workspace volume mount and working directory")
    void buildContainerCommand_boundsWorkspaceMountAndWorkingDir() throws Exception {
        Path realWorkspace = workspace.toRealPath();
        List<String> containerCmd = runner.buildContainerCommand(List.of("gradle", "check"), workspace);

        int vIndex = containerCmd.indexOf("-v");
        assertTrue(vIndex >= 0, "Command must contain -v mount flag");
        String mountArg = containerCmd.get(vIndex + 1);
        assertEquals(realWorkspace + ":" + realWorkspace + ":rw", mountArg);

        int wIndex = containerCmd.indexOf("-w");
        assertTrue(wIndex >= 0, "Command must contain -w working directory flag");
        assertEquals(realWorkspace.toString(), containerCmd.get(wIndex + 1));
    }

    @Test
    @DisplayName("buildContainerCommand reflects customized memory and CPU limits from AgentConfig")
    void buildContainerCommand_reflectsCustomResourceLimits() {
        agentConfig.setSandboxMemoryLimit("4g");
        agentConfig.setSandboxCpuLimit("3.5");

        List<String> containerCmd = runner.buildContainerCommand(List.of("pytest"), workspace);

        assertTrue(containerCmd.contains("--memory=4g"));
        assertTrue(containerCmd.contains("--cpus=3.5"));
    }

    @Test
    @DisplayName("buildContainerCommand rejects invalid or empty commands")
    void buildContainerCommand_validatesInputs() {
        assertThrows(IllegalArgumentException.class, () ->
                runner.buildContainerCommand(null, workspace)
        );
        assertThrows(IllegalArgumentException.class, () ->
                runner.buildContainerCommand(List.of(), workspace)
        );
        assertThrows(IllegalArgumentException.class, () ->
                runner.buildContainerCommand(List.of("ls"), null)
        );
    }
}
