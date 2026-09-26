package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.ScannerConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service implementing the tools that the LLM agent can invoke during its
 * reasoning loop. Each public method represents a callable tool and returns
 * a String result describing what happened. Errors are returned as descriptive
 * strings rather than thrown exceptions so the LLM can interpret them.
 */
@Service
public class AgentToolService {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolService.class);

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", ".auditagent");

    private static final int MAX_SEARCH_RESULTS = 30;
    private static final int MAX_OUTPUT_CHARS = 2000;

    private final AgentConfig agentConfig;
    private final FilePatchService filePatchService;
    private final ScannerConfig scannerConfig;
    private final ScannerService scannerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentToolService(
            AgentConfig agentConfig,
            FilePatchService filePatchService,
            ScannerConfig scannerConfig,
            ScannerService scannerService) {
        this.agentConfig = agentConfig;
        this.filePatchService = filePatchService;
        this.scannerConfig = scannerConfig;
        this.scannerService = scannerService;
    }

    // -----------------------------------------------------------------------
    // Tool 1: readFile
    // -----------------------------------------------------------------------

    /**
     * Reads file content between startLine and endLine (1-indexed, inclusive).
     * If both are 0 the entire file is returned, capped at maxFileReadLines.
     */
    public String readFile(String repoPath, String filePath, int startLine, int endLine) {
        try {
            String pathError = validateFilePath(repoPath, filePath);
            if (pathError != null)
                return pathError;

            Path resolved = resolveSecurePath(repoPath, filePath);
            if (!Files.exists(resolved)) {
                return "ERROR: File not found: " + filePath;
            }
            if (!Files.isRegularFile(resolved)) {
                return "ERROR: Path is not a regular file: " + filePath;
            }

            List<String> allLines = Files.readAllLines(resolved, StandardCharsets.UTF_8);
            int totalLines = allLines.size();

            int start;
            int end;

            if (startLine == 0 && endLine == 0) {
                // Read entire file, capped
                start = 1;
                end = Math.min(totalLines, agentConfig.getMaxFileReadLines());
            } else {
                start = Math.max(1, startLine);
                end = Math.min(totalLines, endLine);
            }

            if (start > totalLines) {
                return "ERROR: startLine " + start + " exceeds file length of " + totalLines + " lines.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("File: ").append(filePath)
                    .append(" (lines ").append(start).append("-").append(end)
                    .append(" of ").append(totalLines).append(")\n");

            for (int i = start; i <= end; i++) {
                sb.append(i).append(": ").append(allLines.get(i - 1)).append("\n");
            }

            logger.info("readFile: read {} lines from {}", (end - start + 1), filePath);
            return sb.toString();

        } catch (Exception e) {
            logger.error("Error in readFile for {}", filePath, e);
            return "ERROR reading file: " + e.getMessage();
        }
    }

    // -----------------------------------------------------------------------
    // Tool 2: searchCodebase
    // -----------------------------------------------------------------------

    /**
     * Searches the codebase for the given query string, optionally filtered by
     * a file glob pattern (e.g. "*.java"). Returns matching file paths and line
     * numbers, capped at 30 results.
     */
    public String searchCodebase(String repoPath, String query, String fileGlob) {
        try {
            Path repoRoot = secureRepositoryRoot(repoPath);

            if (query == null || query.isBlank()) {
                return "ERROR: Search query cannot be empty.";
            }

            Pattern globPattern = null;
            if (fileGlob != null && !fileGlob.isBlank()) {
                // Convert simple glob to regex: *.java -> .*\.java
                String regex = fileGlob
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("?", ".");
                globPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            }

            List<String> results = new ArrayList<>();
            Pattern finalGlobPattern = globPattern;

            try (Stream<Path> walk = Files.walk(repoRoot)) {
                walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(p -> !isExcludedPath(repoRoot, p))
                        .filter(p -> finalGlobPattern == null
                                || finalGlobPattern.matcher(p.getFileName().toString()).matches())
                        .forEach(p -> {
                            if (results.size() >= MAX_SEARCH_RESULTS)
                                return;
                            try (Stream<String> lineStream = Files.lines(p, StandardCharsets.UTF_8)) {
                                AtomicInteger lineNum = new AtomicInteger(0);
                                lineStream.forEach(line -> {
                                    int ln = lineNum.incrementAndGet();
                                    if (results.size() < MAX_SEARCH_RESULTS && line.contains(query)) {
                                        String relPath = repoRoot.relativize(p).toString().replace("\\", "/");
                                        results.add(relPath + ":" + ln + ": " + line.trim());
                                    }
                                });
                            } catch (Exception e) {
                                logger.trace("Skipping unreadable file {}: {}", p, e.getMessage());
                            }
                        });
            }

            if (results.isEmpty()) {
                return "No matches found for query: \"" + query + "\"" +
                        (fileGlob != null ? " with glob: " + fileGlob : "");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" match(es) for \"").append(query).append("\":\n");
            for (String result : results) {
                sb.append("  ").append(result).append("\n");
            }
            if (results.size() >= MAX_SEARCH_RESULTS) {
                sb.append("  ... (results capped at ").append(MAX_SEARCH_RESULTS).append(")\n");
            }

            logger.info("searchCodebase: found {} matches for a {} character query", results.size(), query.length());
            return sb.toString();

        } catch (Exception e) {
            logger.error("searchCodebase failed with {}", e.getClass().getSimpleName());
            return "ERROR searching codebase: " + e.getMessage();
        }
    }

    // -----------------------------------------------------------------------
    // Tool 3: applyPatch
    // -----------------------------------------------------------------------

    /**
     * Reads the target file, finds originalCode, and replaces it with
     * replacementCode. Creates a backup before modification.
     */
    public String applyPatch(String repoPath, String filePath, String originalCode, String replacementCode) {
        try {
            String pathError = validateFilePath(repoPath, filePath);
            if (pathError != null)
                return pathError;

            Path resolved = resolveSecurePath(repoPath, filePath);
            if (!Files.exists(resolved)) {
                return "ERROR: File not found: " + filePath;
            }

            String content = Files.readString(resolved, StandardCharsets.UTF_8);

            if (!content.contains(originalCode)) {
                return "ERROR: Could not find the original code block in " + filePath +
                        ". The code may have been modified or the snippet may not match exactly.";
            }

            // Check for multiple occurrences
            int firstIdx = content.indexOf(originalCode);
            int secondIdx = content.indexOf(originalCode, firstIdx + 1);
            if (secondIdx != -1) {
                return "ERROR: Multiple occurrences of the original code block found in " + filePath +
                        ". Please provide a more specific code snippet to ensure a unique match.";
            }

            String backupPath = filePatchService.createBackup(repoPath, filePath);
            if (backupPath == null) {
                return "ERROR: Backup creation failed; patch was not applied.";
            }
            String beforeHash = sha256(content);

            String updatedContent = content.replace(originalCode, replacementCode);
            if (updatedContent.equals(content)) {
                return "ERROR: Patch application resulted in no changes. The replacement code is identical to the original code.";
            }
            Files.writeString(resolved, updatedContent, StandardCharsets.UTF_8);
            String afterHash = sha256(updatedContent);

            logger.info("applyPatch: successfully patched {}", filePath);
            return "SUCCESS: Patch applied to " + filePath +
                    ". Replaced " + originalCode.lines().count() + " line(s) of original code with " +
                    replacementCode.lines().count() + " line(s) of new code.\n" +
                    "BACKUP_PATH: " + backupPath + "\n" +
                    "BEFORE_HASH: " + beforeHash + "\n" +
                    "AFTER_HASH: " + afterHash;

        } catch (Exception e) {
            logger.error("Error in applyPatch for {}", filePath, e);
            return "ERROR applying patch: " + e.getMessage();
        }
    }

    // -----------------------------------------------------------------------
    // Tool 4: compileProject
    // -----------------------------------------------------------------------

    /**
     * Detects the build tool and runs a compile command. Returns build output
     * truncated to 2000 chars.
     */
    public String compileProject(String repoPath) {
        try {
            Path repoRoot = secureRepositoryRoot(repoPath);

            List<String> cmd;
            if (Files.exists(repoRoot.resolve("pom.xml"))) {
                cmd = Arrays.asList("mvn",
                        "-Dmaven.repo.local=" + ManagedProcessEnvironment.mavenRepository(repoRoot),
                        "compile", "-q");
            } else if (Files.exists(repoRoot.resolve("build.gradle"))) {
                cmd = Arrays.asList("gradle", "compileJava");
            } else {
                return "BUILD SKIPPED: No supported build file found (pom.xml or build.gradle).";
            }

            logger.info("compileProject: running {} in {}", String.join(" ", cmd), repoPath);
            ProcessResult processResult = runProcess(cmd, repoRoot.toFile(), agentConfig.getCompileTimeoutSeconds());

            // Fix #10: Use exit code as primary signal, not fragile string matching
            if (processResult.exitCode != 0 || processResult.output.contains("BUILD FAILURE")) {
                return "BUILD FAILURE (exit code " + processResult.exitCode + "):\n"
                        + truncate(processResult.output, MAX_OUTPUT_CHARS);
            }
            return "BUILD SUCCESS:\n" + truncate(processResult.output, MAX_OUTPUT_CHARS);

        } catch (Exception e) {
            logger.error("Error in compileProject for {}", repoPath, e);
            return "BUILD FAILURE: " + e.getMessage();
        }
    }

    public String runLinter(String repoPath) {
        try {
            Path repoRoot = secureRepositoryRoot(repoPath);
            List<String> detectedLangs = scannerService.detectLanguages(repoRoot.toFile());
            List<String> configs = scannerService.getRuleConfigs(detectedLangs);

            List<String> cmd = new ArrayList<>(Arrays.asList("semgrep", "scan"));
            for (String config : configs) {
                cmd.add("--config");
                cmd.add(config);
            }
            cmd.addAll(Arrays.asList(
                    "--quiet",
                    "--metrics=off",
                    "--disable-version-check",
                    "--no-git-ignore",
                    "--timeout", String.valueOf(scannerConfig.getRuleTimeoutSeconds()),
                    "--timeout-threshold", String.valueOf(scannerConfig.getRuleTimeoutSeconds())));

            String[] excludePatterns = {
                    "node_modules", ".venv", "venv", "__pycache__", ".git",
                    "dist", "build", "target", "bin", "obj", "out",
                    ".idea", ".vscode", ".gradle", ".m2"
            };
            for (String pattern : excludePatterns) {
                cmd.add("--exclude");
                cmd.add(pattern);
            }
            cmd.add(repoRoot.toString());

            logger.info("runLinter (Semgrep): running {} in {}", String.join(" ", cmd), repoPath);
            ProcessResult processResult = runProcess(cmd, repoRoot.toFile(), agentConfig.getCompileTimeoutSeconds());

            if (processResult.exitCode != 0 && processResult.exitCode != 1) {
                return "LINT FAILURE (exit code " + processResult.exitCode + "):\n"
                        + truncate(processResult.output, MAX_OUTPUT_CHARS);
            }
            if (processResult.output.isBlank()) {
                return "LINT SUCCESS: No lint or syntax issues found.";
            }
            return "LINT RESULTS:\n" + truncate(processResult.output, MAX_OUTPUT_CHARS);
        } catch (Exception e) {
            logger.error("Error in runLinter for {}", repoPath, e);
            return "LINT FAILURE: " + e.getMessage();
        }
    }

    public String runStaticAnalysis(String repoPath) {
        try {
            Path repoRoot = secureRepositoryRoot(repoPath);
            List<String> detectedLangs = scannerService.detectLanguages(repoRoot.toFile());
            List<String> configs = scannerService.getRuleConfigs(detectedLangs);

            List<String> cmd = new ArrayList<>(Arrays.asList("semgrep", "scan"));
            for (String config : configs) {
                cmd.add("--config");
                cmd.add(config);
            }
            cmd.addAll(Arrays.asList(
                    "--quiet",
                    "--metrics=off",
                    "--disable-version-check",
                    "--no-git-ignore",
                    "--timeout", String.valueOf(scannerConfig.getRuleTimeoutSeconds()),
                    "--timeout-threshold", String.valueOf(scannerConfig.getRuleTimeoutSeconds())));

            String[] excludePatterns = {
                    "node_modules", ".venv", "venv", "__pycache__", ".git",
                    "dist", "build", "target", "bin", "obj", "out",
                    ".idea", ".vscode", ".gradle", ".m2"
            };
            for (String pattern : excludePatterns) {
                cmd.add("--exclude");
                cmd.add(pattern);
            }
            cmd.add(repoRoot.toString());

            logger.info("runStaticAnalysis (Semgrep): running {} in {}", String.join(" ", cmd), repoPath);
            ProcessResult processResult = runProcess(cmd, repoRoot.toFile(), agentConfig.getCompileTimeoutSeconds());

            if (processResult.exitCode != 0 && processResult.exitCode != 1) {
                return "ANALYSIS FAILURE (exit code " + processResult.exitCode + "):\n"
                        + truncate(processResult.output, MAX_OUTPUT_CHARS);
            }
            if (processResult.output.isBlank()) {
                return "ANALYSIS SUCCESS: No security or code quality issues found.";
            }
            return "ANALYSIS RESULTS:\n" + truncate(processResult.output, MAX_OUTPUT_CHARS);
        } catch (Exception e) {
            logger.error("Error in runStaticAnalysis for {}", repoPath, e);
            return "ANALYSIS FAILURE: " + e.getMessage();
        }
    }
    // -----------------------------------------------------------------------
    // Tool 5: rescanFile
    // -----------------------------------------------------------------------

    /**
     * Runs semgrep on a single file and returns a summary of remaining findings.
     */
    public String rescanFile(String repoPath, String filePath) {
        try {
            String pathError = validateFilePath(repoPath, filePath);
            if (pathError != null)
                return pathError;

            Path resolved = resolveSecurePath(repoPath, filePath);
            if (!Files.exists(resolved)) {
                return "ERROR: File not found: " + filePath;
            }

            String lang = scannerService.detectLanguageForFile(resolved.getFileName().toString());
            List<String> configs = scannerService.getRuleConfigs(
                    Optional.ofNullable(lang)
                            .map(List::of)
                            .orElseGet(Collections::emptyList));

            List<String> cmd = new ArrayList<>(Arrays.asList("semgrep", "scan"));
            for (String config : configs) {
                cmd.add("--config");
                cmd.add(config);
            }
            cmd.addAll(Arrays.asList(
                    "--json",
                    "-q",
                    "--metrics=off",
                    "--disable-version-check",
                    "--timeout", String.valueOf(scannerConfig.getRuleTimeoutSeconds()),
                    "--timeout-threshold", String.valueOf(scannerConfig.getRuleTimeoutSeconds()),
                    resolved.toString()));

            logger.info("rescanFile: running semgrep on {}", filePath);
            Path repoRoot = secureRepositoryRoot(repoPath);
            ProcessResult processResult = runProcess(cmd, repoRoot.toFile(), agentConfig.getCompileTimeoutSeconds());
            String output = processResult.output;
            if (processResult.exitCode != 0 && processResult.exitCode != 1) {
                return "ERROR: Semgrep rescan failed with exit code " + processResult.exitCode + ":\n" +
                        truncate(output, MAX_OUTPUT_CHARS);
            }

            // Parse the JSON output to count findings
            int findingCount = 0;
            StringBuilder details = new StringBuilder();
            try {
                JsonNode root = objectMapper.readTree(output);
                if (root.has("results") && root.get("results").isArray()) {
                    findingCount = root.get("results").size();
                    for (JsonNode item : root.get("results")) {
                        int line = item.path("start").path("line").asInt();
                        String checkId = item.path("check_id").asText();
                        String message = item.path("extra").path("message").asText().replace("\n", " ");
                        details.append(String.format("- Line %d [%s]: %s\n", line, checkId, message));
                    }
                }
            } catch (Exception parseEx) {
                logger.warn("Could not parse semgrep JSON output for {}", filePath, parseEx);
                return "Rescan complete but could not parse results for " + filePath +
                        ". Raw output:\n" + truncate(output, MAX_OUTPUT_CHARS);
            }

            logger.info("rescanFile: {} findings in {}", findingCount, filePath);
            String summary = "Rescan complete: " + findingCount + " vulnerabilities found in " + filePath;
            if (findingCount > 0) {
                summary += "\nDetails:\n" + details.toString();
                summary += "\nNOTE TO AGENT: There are still vulnerabilities in this file. You MUST fix ALL vulnerabilities in this file before verification can pass. Do not stop until findingCount is 0.";
            } else {
                summary += "\nNOTE TO AGENT: All vulnerabilities in this file have been resolved. Your patch was SUCCESSFUL.";
            }
            return summary;

        } catch (Exception e) {
            logger.error("Error in rescanFile for {}", filePath, e);
            return "ERROR rescanning file: " + e.getMessage();
        }
    }

    // -----------------------------------------------------------------------
    // Tool 6: runTests
    // -----------------------------------------------------------------------

    /**
     * Runs project tests. If testGlob is provided, runs only matching tests;
     * otherwise runs all tests.
     */
    public String runTests(String repoPath, String testGlob) {
        try {
            Path repoRoot = secureRepositoryRoot(repoPath);

            List<String> cmd;
            if (Files.exists(repoRoot.resolve("pom.xml")) && testGlob != null && !testGlob.isBlank()) {
                cmd = Arrays.asList("mvn", "test", "-pl", ".", "-Dtest=" + testGlob);
            } else if (Files.exists(repoRoot.resolve("pom.xml"))) {
                cmd = Arrays.asList("mvn",
                        "-Dmaven.repo.local=" + ManagedProcessEnvironment.mavenRepository(repoRoot),
                        "test", "-q");
            } else if (Files.exists(repoRoot.resolve("build.gradle"))) {
                cmd = Arrays.asList("gradle", "test");
            } else {
                return "TEST SKIPPED: No supported test runner was detected (Maven or Gradle).";
            }

            logger.info("runTests: running {} in {}", String.join(" ", cmd), repoPath);
            ProcessResult processResult = runProcess(cmd, repoRoot.toFile(), agentConfig.getTestTimeoutSeconds());
            String output = processResult.output;

            if (processResult.exitCode != 0) {
                return "TEST FAILURE (exit code " + processResult.exitCode + "):\n"
                        + truncate(output, MAX_OUTPUT_CHARS);
            }

            // Try to extract test summary from Maven output
            String summary = extractTestSummary(output);
            if (summary != null) {
                return "TEST SUCCESS: " + summary;
            }

            // Fallback: use exit code as primary signal
            return "TEST SUCCESS:\n" + truncate(output, MAX_OUTPUT_CHARS);

        } catch (Exception e) {
            logger.error("Error in runTests for {}", repoPath, e);
            return "TEST FAILURE: " + e.getMessage();
        }
    }

    // -----------------------------------------------------------------------
    // Tool 7: rollbackFile
    // -----------------------------------------------------------------------

    /**
     * Restores a file from its backup, undoing any patches that were applied.
     */
    public String rollbackFile(String repoPath, String filePath) {
        try {
            String pathError = validateFilePath(repoPath, filePath);
            if (pathError != null)
                return pathError;

            boolean success = filePatchService.rollback(repoPath, filePath);
            if (success) {
                logger.info("rollbackFile: restored {} from backup", filePath);
                return "SUCCESS: File " + filePath + " has been restored from backup.";
            } else {
                return "ERROR: No backup found for " + filePath +
                        ". Cannot rollback without a prior backup.";
            }

        } catch (Exception e) {
            logger.error("Error in rollbackFile for {}", filePath, e);
            return "ERROR rolling back file: " + e.getMessage();
        }
    }

    // =======================================================================
    // Private helper methods
    // =======================================================================

    /**
     * Validates that the given filePath does not escape the repoPath via
     * path traversal (e.g. "../../etc/passwd").
     */
    @SuppressWarnings("unused")
    private String validateFilePathLegacy(String repoPath, String filePath) {
        if (repoPath == null || repoPath.isBlank()) {
            return "ERROR: Repository path is null or empty.";
        }
        if (filePath == null || filePath.isBlank()) {
            return "ERROR: File path is null or empty.";
        }
        try {
            Path repoRoot = Path.of(repoPath).toAbsolutePath().normalize();
            Path resolved = repoRoot.resolve(filePath).normalize();
            if (!resolved.startsWith(repoRoot)) {
                return "ERROR: Security violation — file path escapes the repository directory: " + filePath;
            }
        } catch (Exception e) {
            return "ERROR: Invalid file path: " + e.getMessage();
        }
        return null; // valid
    }

    /**
     * Resolves and normalizes a file path securely within the repo root.
     */
    @SuppressWarnings("unused")
    private Path resolveLexicalPath(String repoPath, String filePath) {
        return Path.of(repoPath).toAbsolutePath().normalize()
                .resolve(filePath).normalize();
    }

    private String validateFilePath(String repoPath, String filePath) {
        if (repoPath == null || repoPath.isBlank())
            return "ERROR: Repository path is null or empty.";
        if (filePath == null || filePath.isBlank())
            return "ERROR: File path is null or empty.";
        try {
            resolveSecurePath(repoPath, filePath);
            return null;
        } catch (Exception e) {
            return "ERROR: Security violation - " + e.getMessage();
        }
    }

    private Path resolveSecurePath(String repoPath, String filePath) {
        try {
            Path repoRoot = secureRepositoryRoot(repoPath);
            Path resolved = repoRoot.resolve(filePath).normalize();
            if (!resolved.startsWith(repoRoot)) {
                throw new IllegalArgumentException("file path escapes the repository directory: " + filePath);
            }
            Path current = repoRoot;
            for (Path component : repoRoot.relativize(resolved)) {
                current = current.resolve(component);
                if (Files.isSymbolicLink(current)) {
                    throw new IllegalArgumentException("symbolic-link paths are not permitted: " + filePath);
                }
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                        && !current.toRealPath().startsWith(repoRoot)) {
                    throw new IllegalArgumentException("resolved path escapes the repository directory: " + filePath);
                }
            }
            return resolved;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid repository file path", e);
        }
    }

    private Path secureRepositoryRoot(String repoPath) {
        try {
            Path root = Path.of(repoPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw new IllegalArgumentException("repository path is not a real directory");
            }
            return root.toRealPath();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("repository path is unavailable", e);
        }
    }

    /**
     * Checks whether a path falls within any excluded directory.
     */
    private boolean isExcludedPath(Path repoRoot, Path filePath) {
        Path relative = repoRoot.relativize(filePath);
        for (Path component : relative) {
            if (EXCLUDED_DIRS.contains(component.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Result of an external process execution.
     */
    private static class ProcessResult {
        final String output;
        final int exitCode;

        ProcessResult(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }
    }

    /**
     * Runs an external process with timeout handling. Captures both stdout and
     * stderr and returns them combined along with the exit code.
     * When sandbox is enabled, wraps the command inside a Docker container.
     */
    private ProcessResult runProcess(List<String> command, File workingDir, int timeoutSeconds) {
        try {
            List<String> effectiveCommand;

            if (agentConfig.isSandboxEnabled()) {
                // Wrap command in docker run for sandboxed execution
                effectiveCommand = buildDockerCommand(command, workingDir);
                logger.info("Sandbox enabled: wrapping command in Docker container [{}]",
                        agentConfig.getSandboxImage());
            } else {
                effectiveCommand = command;
            }

            ProcessBuilder pb = new ProcessBuilder(effectiveCommand);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);

            if (!agentConfig.isSandboxEnabled()) {
                // Only apply host environment sandboxing when NOT using Docker
                ManagedProcessEnvironment.configure(pb, workingDir.toPath());
            }

            Process process = pb.start();

            // Read output asynchronously to avoid blocking the timeout
            StringBuilder output = new StringBuilder();
            Thread outputReader = Thread.startVirtualThread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception ignored) {
                }
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputReader.interrupt();
                output.append("\n[TIMEOUT] Process killed after ").append(timeoutSeconds).append(" seconds.");
                logger.warn("Process timed out after {} seconds: {}", timeoutSeconds, String.join(" ", command));
            }
            outputReader.join(2000); // Wait briefly for reader to drain

            int exitCode = finished ? process.exitValue() : -1;
            logger.info("Process exited with code {}: {}", exitCode, String.join(" ", command));
            return new ProcessResult(output.toString(), exitCode);

        } catch (Exception e) {
            logger.error("Error running process: {}", String.join(" ", command), e);
            return new ProcessResult("ERROR: Failed to execute command: " + e.getMessage(), -1);
        }
    }

    /**
     * Builds a docker run command that wraps the original command inside a
     * sandboxed container with volume-mounted workspace and resource limits.
     */
    private List<String> buildDockerCommand(List<String> originalCommand, File workingDir) {
        List<String> dockerCmd = new ArrayList<>();
        dockerCmd.add("docker");
        dockerCmd.add("run");
        dockerCmd.add("--rm"); // Remove container after execution
        dockerCmd.add("--network=none"); // No network access for security
        dockerCmd.add("--memory=" + agentConfig.getSandboxMemoryLimit());
        dockerCmd.add("--cpus=" + agentConfig.getSandboxCpuLimit());
        dockerCmd.add("--read-only"); // Read-only root filesystem
        dockerCmd.add("--tmpfs");
        dockerCmd.add("/tmp:rw,noexec,size=512m"); // Writable /tmp for build tools
        dockerCmd.add("-v");
        dockerCmd.add(workingDir.getAbsolutePath() + ":" + workingDir.getAbsolutePath());
        dockerCmd.add("-w");
        dockerCmd.add(workingDir.getAbsolutePath());
        dockerCmd.add(agentConfig.getSandboxImage());
        dockerCmd.addAll(originalCommand); // Append the original command
        return dockerCmd;
    }

    /**
     * Truncates a string to the specified maximum length, appending an
     * indicator if truncation occurred.
     */
    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "\n... [output truncated at " + maxLength + " chars]";
    }

    public String hashFile(String repoPath, String filePath) {
        try {
            String pathError = validateFilePath(repoPath, filePath);
            if (pathError != null)
                return null;
            Path path = resolveSecurePath(repoPath, filePath);
            return Files.isRegularFile(path) ? sha256(Files.readString(path, StandardCharsets.UTF_8)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash file content", e);
        }
    }

    /**
     * Attempts to extract a Maven Surefire-style test summary from the raw
     * build output (e.g. "Tests run: 5, Failures: 1, Errors: 0, Skipped: 0").
     */
    private String extractTestSummary(String output) {
        if (output == null)
            return null;

        for (String line : output.split("\n")) {
            if (line.contains("Tests run:") && line.contains("Failures:")) {
                String status = output.toLowerCase().contains("build failure") ? "TEST FAILURE" : "TEST SUCCESS";
                return status + ": " + line.trim() + "\n\n" + truncate(output, MAX_OUTPUT_CHARS);
            }
        }
        return null;
    }
}
