package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Containerized execution runner that wraps untrusted repository commands (e.g. mvn test, gradle test)
 * inside ephemeral, resource-bounded Docker containers with dropped Linux capabilities and restricted network.
 * Satisfies Phase 5 Workspace & Sandbox Decoupling.
 */
@Component
@Primary
public class ContainerizedProcessRunner extends ManagedProcessRunner {
    private static final Logger logger = LoggerFactory.getLogger(ContainerizedProcessRunner.class);

    // Host sensitive environment variable keys that must NEVER be passed into untrusted sandbox containers
    public static final Set<String> SENSITIVE_ENV_VARS = Set.of(
            "SUPABASE_SERVICE_KEY",
            "SUPABASE_URL",
            "OPENAI_API_KEY",
            "GITHUB_APP_PRIVATE_KEY",
            "GITHUB_TOKEN",
            "AWS_SECRET_ACCESS_KEY",
            "INTERNAL_API_TOKEN",
            "DB_PASSWORD",
            "RABBITMQ_PASSWORD"
    );

    private final AgentConfig agentConfig;

    public ContainerizedProcessRunner(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    @Override
    public ProcessResult run(List<String> command, Path workspace, Duration timeout,
            Duration heartbeatInterval, Consumer<Duration> heartbeat) {
        if (!agentConfig.isSandboxEnabled()) {
            return super.run(command, workspace, timeout, heartbeatInterval, heartbeat);
        }

        List<String> containerCmd = buildContainerCommand(command, workspace);
        logger.info("Executing command in container sandbox [{}]: {}",
                agentConfig.getSandboxImage(), String.join(" ", command));

        return super.run(containerCmd, workspace, timeout, heartbeatInterval, heartbeat);
    }

    /**
     * Builds a docker run command with strict security controls:
     * - Ephemeral container (--rm)
     * - Network isolation (--network=none)
     * - Strict memory limit (--memory=...)
     * - Strict CPU limit (--cpus=...)
     * - Dropped Linux capabilities (--cap-drop=CAP_NET_RAW, --cap-drop=CAP_SYS_ADMIN)
     * - Privilege escalation prevention (--security-opt=no-new-privileges)
     * - Workspace volume mount bounded to canonical path (-v workspace:workspace:rw)
     * - Working directory set to workspace (-w workspace)
     * - Writable temporary storage (--tmpfs /tmp:rw,noexec,size=512m)
     * - Zero host sensitive environment variable leakage
     */
    public List<String> buildContainerCommand(List<String> originalCommand, Path workspace) {
        if (originalCommand == null || originalCommand.isEmpty()) {
            throw new IllegalArgumentException("Original command cannot be empty");
        }
        if (workspace == null) {
            throw new IllegalArgumentException("Workspace path cannot be null");
        }

        Path canonicalWorkspace;
        try {
            canonicalWorkspace = workspace.toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            canonicalWorkspace = workspace.toAbsolutePath().normalize();
        }

        List<String> dockerCmd = new ArrayList<>();
        dockerCmd.add("docker");
        dockerCmd.add("run");
        dockerCmd.add("--rm");
        dockerCmd.add("--network=none");

        // Resource limits
        String memLimit = agentConfig.getSandboxMemoryLimit() != null && !agentConfig.getSandboxMemoryLimit().isBlank()
                ? agentConfig.getSandboxMemoryLimit()
                : "2g";
        dockerCmd.add("--memory=" + memLimit);

        String cpuLimit = agentConfig.getSandboxCpuLimit() != null && !agentConfig.getSandboxCpuLimit().isBlank()
                ? agentConfig.getSandboxCpuLimit()
                : "2.0";
        dockerCmd.add("--cpus=" + cpuLimit);

        // Security options and dropped capabilities
        dockerCmd.add("--security-opt=no-new-privileges");
        dockerCmd.add("--cap-drop=CAP_NET_RAW");
        dockerCmd.add("--cap-drop=CAP_SYS_ADMIN");

        // Temporary storage for build tooling
        dockerCmd.add("--tmpfs");
        dockerCmd.add("/tmp:rw,noexec,size=512m");

        // Workspace volume mount and working directory
        dockerCmd.add("-v");
        dockerCmd.add(canonicalWorkspace.toString() + ":" + canonicalWorkspace.toString() + ":rw");
        dockerCmd.add("-w");
        dockerCmd.add(canonicalWorkspace.toString());

        // Safe environment variables only (no host secrets)
        dockerCmd.add("-e");
        dockerCmd.add("LANG=C.UTF-8");
        dockerCmd.add("-e");
        dockerCmd.add("LC_ALL=C.UTF-8");

        // Sandbox image
        String image = agentConfig.getSandboxImage() != null && !agentConfig.getSandboxImage().isBlank()
                ? agentConfig.getSandboxImage()
                : "auditagent-sandbox:2.7.0";
        dockerCmd.add(image);

        // Appended original user command
        dockerCmd.addAll(originalCommand);

        return Collections.unmodifiableList(dockerCmd);
    }
}
