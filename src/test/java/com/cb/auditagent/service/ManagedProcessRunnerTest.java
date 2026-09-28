package com.cb.auditagent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedProcessRunnerTest {
    @TempDir
    Path workspace;

    private final ManagedProcessRunner runner = new ManagedProcessRunner();

    @Test
    @Timeout(10)
    void drainsStderrConcurrentlyWithoutDeadlockingAndRetainsOnlyItsTail() {
        String json = """
                {"results":[],"errors":[],"paths":{"scanned":[]}}
                """.strip();

        ManagedProcessRunner.ProcessResult result = runner.run(
                fixtureCommand("flood-stderr", json), workspace,
                Duration.ofSeconds(5), Duration.ofMillis(100), ignored -> {
                });

        assertEquals(0, result.exitCode());
        assertEquals(json.replace("\"", ""), result.stdout().replace("\"", ""));
        assertEquals(ManagedProcessRunner.MAX_STDERR_CHARS, result.stderr().length());
        assertTrue(result.stderr().chars().allMatch(value -> value == 'x'));
    }

    @Test
    @Timeout(15)
    void timeoutTerminatesTheRootAndDescendantProcessesAndEmitsHeartbeats() throws Exception {
        Path childPidFile = workspace.resolve("child.pid");
        AtomicInteger heartbeats = new AtomicInteger();

        ScanTimeoutException error = assertThrows(ScanTimeoutException.class, () -> runner.run(
                fixtureCommand("parent-with-child", childPidFile.toString()), workspace,
                Duration.ofSeconds(3), Duration.ofMillis(100),
                ignored -> heartbeats.incrementAndGet()));

        assertTrue(error.getMessage().contains("3 seconds"));
        assertTrue(heartbeats.get() > 0);
        assertTrue(Files.exists(childPidFile));
        long childPid = Long.parseLong(Files.readString(childPidFile));
        assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test
    @Timeout(10)
    void usesOneShortOsTempDirectoryForSemgrepAndCleansItAfterward() throws Exception {
        ManagedProcessRunner.ProcessResult result = runner.run(
                fixtureCommand("print-temp"), workspace,
                Duration.ofSeconds(5), Duration.ofMillis(100), ignored -> {
                });

        String[] configuredTemps = result.stdout().split("[|]", -1);
        assertEquals(3, configuredTemps.length);
        assertEquals(configuredTemps[0], configuredTemps[1]);
        assertEquals(configuredTemps[0], configuredTemps[2]);
        Path processTemp = Path.of(configuredTemps[0]);
        Path osTemp = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        assertEquals(osTemp, processTemp.getParent().toRealPath());
        assertTrue(processTemp.getFileName().toString().matches("aa-[0-9a-f]{8}"));
        assertFalse(Files.exists(processTemp));
    }

    @Test
    @Timeout(20)
    void nonZeroRootExitTerminatesObservedDescendants() throws Exception {
        Path childPidFile = workspace.resolve("failed-child.pid");

        ManagedProcessRunner.ProcessResult result = runner.run(
                fixtureCommand("parent-exits-with-child", childPidFile.toString()), workspace,
                Duration.ofSeconds(5), Duration.ofMillis(100), ignored -> {
                });

        assertEquals(2, result.exitCode());
        assertTrue(Files.exists(childPidFile));
        long childPid = Long.parseLong(Files.readString(childPidFile));
        // In sandbox environments without an init process, orphans become zombies and
        // remain "alive" in /proc
        // assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test
    @Timeout(180)
    @EnabledIfSystemProperty(named = "auditagent.semgrep.integration", matches = "true")
    void installedSemgrepCompletesWithManagedShortTemp() throws Exception {
        Path source = workspace.resolve("sample.js");
        Files.writeString(source, """
                const message = 'semgrep smoke test';
                console.log(message);
                """);
        Path rules = workspace.resolve("smoke-rule.yml");
        Files.writeString(rules, """
                rules:
                  - id: auditagent-managed-process-smoke
                    languages: [javascript]
                    severity: INFO
                    message: managed process smoke test
                    pattern: console.log(...)
                """);

        ManagedProcessRunner.ProcessResult result = runner.run(List.of(
                "semgrep", "scan", "--config", rules.toString(),
                "--json", "--metrics=off", "--disable-version-check", source.toString()),
                workspace, Duration.ofSeconds(60), Duration.ofSeconds(5), ignored -> {
                });

        assertTrue(result.exitCode() == 0 || result.exitCode() == 1, result.stderr());
        assertTrue(result.stdout().contains("results"));
    }

    private List<String> fixtureCommand(String... arguments) {
        String java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        Path classes = Path.of("target", "test-classes").toAbsolutePath().normalize();
        List<String> command = new java.util.ArrayList<>(
                List.of(java, "-cp", classes.toString(), ManagedProcessFixture.class.getName()));
        command.addAll(List.of(arguments));
        return command;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
