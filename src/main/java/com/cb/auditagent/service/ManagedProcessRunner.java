package com.cb.auditagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Runs an untrusted-repository tool with bounded diagnostics, a hard deadline,
 * and ownership of the complete descendant process tree.
 */
@Component
public class ManagedProcessRunner {
    private static final Logger logger = LoggerFactory.getLogger(ManagedProcessRunner.class);
    static final int MAX_STDERR_CHARS = 64 * 1024;
    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(5);
    private static final Duration DRAIN_JOIN_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration PROCESS_OBSERVATION_INTERVAL = Duration.ofMillis(250);

    @SuppressWarnings("unused")
    public ProcessResult run(List<String> command, Path workspace, Duration timeout,
            Duration heartbeatInterval, Consumer<Duration> heartbeat) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Managed process command is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Managed process timeout must be positive");
        }
        if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative()) {
            throw new IllegalArgumentException("Managed process heartbeat interval must be positive");
        }

        Process process = null;
        Thread stdoutDrainer = null;
        Thread stderrDrainer = null;
        Path shortTempDirectory = null;
        Map<Long, ProcessHandle> observedDescendants = new LinkedHashMap<>();
        StringBuilder stdout = new StringBuilder();
        TailBuffer stderr = new TailBuffer(MAX_STDERR_CHARS);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workspace.toFile());
            builder.redirectErrorStream(false);
            shortTempDirectory = createShortTempDirectory();
            ManagedProcessEnvironment.configure(builder, workspace, shortTempDirectory);
            process = builder.start();

            Process runningProcess = process;
            stdoutDrainer = Thread.startVirtualThread(
                    () -> drain(runningProcess.getInputStream(), stdout::append));
            stderrDrainer = Thread.startVirtualThread(
                    () -> drain(runningProcess.getErrorStream(), stderr::append));

            long startedNanos = System.nanoTime();
            long deadlineNanos = saturatingAdd(startedNanos, timeout.toNanos());
            long nextHeartbeatNanos = saturatingAdd(startedNanos, heartbeatInterval.toNanos());
            while (process.isAlive()) {
                observeDescendants(process, observedDescendants);
                long nowNanos = System.nanoTime();
                long remainingNanos = deadlineNanos - nowNanos;
                if (remainingNanos <= 0) {
                    throw new ScanTimeoutException(
                            "Semgrep exceeded the configured timeout of " + timeout.toSeconds() + " seconds.");
                }

                long untilHeartbeatNanos = Math.max(1, nextHeartbeatNanos - nowNanos);
                long waitNanos = Math.min(remainingNanos,
                        Math.min(PROCESS_OBSERVATION_INTERVAL.toNanos(), untilHeartbeatNanos));
                if (process.waitFor(waitNanos, TimeUnit.NANOSECONDS)) {
                    break;
                }
                nowNanos = System.nanoTime();
                if (nowNanos >= nextHeartbeatNanos) {
                    if (heartbeat != null) {
                        heartbeat.accept(Duration.ofNanos(Math.max(0, nowNanos - startedNanos)));
                    }
                    nextHeartbeatNanos = saturatingAdd(nowNanos, heartbeatInterval.toNanos());
                }
            }

            observeDescendants(process, observedDescendants);
            terminateObservedDescendants(observedDescendants);
            joinDrainers(stdoutDrainer, stderrDrainer);
            return new ProcessResult(process.exitValue(), stdout.toString(), stderr.toString());
        } catch (ScanExecutionException e) {
            if (process != null) {
                terminateProcessTree(process, observedDescendants);
                closeStreams(process);
            }
            joinDrainersWithoutInterrupt(stdoutDrainer, stderrDrainer);
            throw e;
        } catch (InterruptedException e) {
            if (process != null) {
                terminateProcessTree(process, observedDescendants);
                closeStreams(process);
            }
            joinDrainersWithoutInterrupt(stdoutDrainer, stderrDrainer);
            Thread.currentThread().interrupt();
            throw new ScanExecutionException("Semgrep scan was interrupted.", e);
        } catch (IOException e) {
            if (process != null) {
                terminateProcessTree(process, observedDescendants);
                closeStreams(process);
            }
            joinDrainersWithoutInterrupt(stdoutDrainer, stderrDrainer);
            throw new ScanExecutionException("Could not start or read the Semgrep process.", e);
        } catch (RuntimeException e) {
            if (process != null) {
                terminateProcessTree(process, observedDescendants);
                closeStreams(process);
            }
            joinDrainersWithoutInterrupt(stdoutDrainer, stderrDrainer);
            throw new ScanExecutionException("Semgrep progress handling failed.", e);
        } finally {
            cleanupShortTempDirectory(shortTempDirectory);
        }
    }

    private static void drain(InputStream input, Consumer<String> output) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                output.accept(new String(buffer, 0, read));
            }
        } catch (IOException ignored) {
            // Stream closure is expected when a timed-out process tree is terminated.
        }
    }

    private static void joinDrainers(Thread stdoutDrainer, Thread stderrDrainer) throws InterruptedException {
        long deadlineNanos = saturatingAdd(System.nanoTime(), DRAIN_JOIN_TIMEOUT.toNanos());
        joinUntil(stdoutDrainer, deadlineNanos);
        joinUntil(stderrDrainer, deadlineNanos);
        if ((stdoutDrainer != null && stdoutDrainer.isAlive())
                || (stderrDrainer != null && stderrDrainer.isAlive())) {
            throw new ScanExecutionException("Semgrep output streams did not close after the process exited.");
        }
    }

    private static void joinUntil(Thread thread, long deadlineNanos) throws InterruptedException {
        if (thread == null) {
            return;
        }
        while (thread.isAlive()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return;
            }
            thread.join(Duration.ofNanos(remainingNanos));
        }
    }

    private static void joinDrainersWithoutInterrupt(Thread stdoutDrainer, Thread stderrDrainer) {
        boolean interrupted = Thread.interrupted();
        try {
            joinDrainers(stdoutDrainer, stderrDrainer);
        } catch (InterruptedException e) {
            interrupted = true;
        } catch (ScanExecutionException ignored) {
            // The original process failure remains the useful error.
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void observeDescendants(Process process, Map<Long, ProcessHandle> observedDescendants) {
        try (Stream<ProcessHandle> descendants = process.descendants()) {
            descendants.forEach(handle -> observedDescendants.put(handle.pid(), handle));
        } catch (SecurityException ignored) {
            // The root handle is still terminated even if descendant inspection is
            // restricted.
        }
    }

    private static void terminateObservedDescendants(Map<Long, ProcessHandle> observedDescendants) {
        List<ProcessHandle> handles = new ArrayList<>(observedDescendants.values());
        Collections.reverse(handles);
        terminateHandles(handles);
    }

    private static void terminateProcessTree(Process process, Map<Long, ProcessHandle> observedDescendants) {
        observeDescendants(process, observedDescendants);
        List<ProcessHandle> handles = new ArrayList<>(observedDescendants.values());
        Collections.reverse(handles);
        handles.add(process.toHandle());
        terminateHandles(handles);
    }

    private static void terminateHandles(List<ProcessHandle> handles) {
        handles.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
        awaitTermination(handles, TERMINATION_GRACE);
        handles.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        awaitTermination(handles, TERMINATION_GRACE);
    }

    private static Path createShortTempDirectory() throws IOException {
        String configuredRoot = System.getProperty("java.io.tmpdir");
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IOException("Operating-system temporary directory is unavailable");
        }
        Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("Operating-system temporary directory is not a real directory");
        }
        root = root.toRealPath();
        Path created = null;
        for (int attempt = 0; attempt < 10 && created == null; attempt++) {
            Path candidate = root.resolve("aa-" + UUID.randomUUID().toString().substring(0, 8));
            try {
                created = Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Retry the extremely unlikely short-name collision.
            }
        }
        if (created == null) {
            throw new IOException("Could not allocate a unique Semgrep temporary directory");
        }
        try {
            Path directory = created.toRealPath();
            if (!directory.getParent().equals(root)
                    || !directory.getFileName().toString().startsWith("aa-")) {
                throw new IOException("Semgrep temporary directory escaped its managed root");
            }
            Files.setPosixFilePermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
            return directory;
        } catch (UnsupportedOperationException ignored) {
            // Windows inherits the current user's ACL from the user temp directory.
            return created.toRealPath();
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(created);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    private static void cleanupShortTempDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            Path root = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize().toRealPath();
            Path normalized = directory.toAbsolutePath().normalize();
            if (!normalized.getParent().equals(root)
                    || !normalized.getFileName().toString().startsWith("aa-")) {
                logger.warn("Refusing to clean an unexpected Semgrep temporary directory");
                return;
            }
            if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(normalized)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not clean the short Semgrep temporary directory: {}",
                    e.getClass().getSimpleName());
        }
    }

    private static void awaitTermination(List<ProcessHandle> handles, Duration timeout) {
        long deadlineNanos = saturatingAdd(System.nanoTime(), timeout.toNanos());
        boolean interrupted = false;
        while (handles.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadlineNanos) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                interrupted = true;
                break;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeStreams(Process process) {
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
        }
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
    }

    private static long saturatingAdd(long value, long increment) {
        try {
            return Math.addExact(value, increment);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {
    }

    private static final class TailBuffer {
        private final int limit;
        private final StringBuilder value = new StringBuilder();

        private TailBuffer(int limit) {
            this.limit = limit;
        }

        private void append(String chunk) {
            value.append(chunk);
            int overflow = value.length() - limit;
            if (overflow > 0) {
                value.delete(0, overflow);
            }
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
