package com.cb.auditagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.domain.Vulnerability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FilePatchService {
    private static final Logger logger = LoggerFactory.getLogger(FilePatchService.class);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AgentConfig agentConfig;

    public FilePatchService(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    // ── Backup ────────────────────────────────────────────────────────────

    /**
     * Creates a timestamped backup of the given file before a patch is applied.
     *
     * @param repoPath absolute path to the repository root
     * @param filePath relative path of the file inside the repository
     * @return the absolute path of the backup file, or {@code null} if the backup
     *         failed
     */
    public String createBackup(String repoPath, String filePath) {
        Path sourceFile = resolveSecurePath(repoPath, filePath);
        if (!Files.exists(sourceFile, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
            logger.warn("Cannot create backup — source file does not exist: {}", sourceFile);
            return null;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        Path backupFile = resolveSecurePath(repoPath,
                Paths.get(agentConfig.getBackupDir(), timestamp, filePath).toString());

        try {
            createDirectoriesSecure(Paths.get(repoPath).toAbsolutePath().normalize().toRealPath(),
                    backupFile.getParent());
            Files.copy(sourceFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Backup created: {}", backupFile);
            return backupFile.toString();
        } catch (IOException e) {
            logger.error("Failed to create backup for {}: {}", filePath, e.getMessage(), e);
            return null;
        }
    }

    // ── Rollback ──────────────────────────────────────────────────────────

    /**
     * Restores a file from its most recent backup.
     *
     * @param repoPath absolute path to the repository root
     * @param filePath relative path of the file inside the repository
     * @return {@code true} if the file was successfully restored
     */
    public boolean rollback(String repoPath, String filePath) {
        Path latestBackup = findLatestBackup(repoPath, filePath);
        if (latestBackup == null) {
            logger.warn("No backup found for {} — rollback aborted.", filePath);
            return false;
        }

        Path targetFile = Paths.get(repoPath, filePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetFile.getParent());
            Files.copy(latestBackup, targetFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Rolled back {} from backup {}", filePath, latestBackup);
            return true;
        } catch (IOException e) {
            logger.error("Rollback failed for {}: {}", filePath, e.getMessage(), e);
            return false;
        }
    }

    /** Restore the exact backup captured for a durable agent run. */
    public boolean restoreBackup(String repoPath, String filePath, String backupPath) {
        if (backupPath == null || backupPath.isBlank())
            return false;
        try {
            Path repoRoot = Paths.get(repoPath).toAbsolutePath().normalize();
            Path backupRoot = repoRoot.resolve(agentConfig.getBackupDir()).normalize();
            Path source = Paths.get(backupPath).toAbsolutePath().normalize();
            Path target = repoRoot.resolve(filePath).normalize();
            if (!source.startsWith(backupRoot) || !target.startsWith(repoRoot) || !Files.isRegularFile(source)) {
                logger.warn("Rejected invalid durable rollback path for {}", filePath);
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Restored {} from durable run backup {}", filePath, source);
            return true;
        } catch (Exception e) {
            logger.error("Durable rollback failed for {}", filePath, e);
            return false;
        }
    }

    // ── Diff ──────────────────────────────────────────────────────────────

    /**
     * Produces a human-readable unified diff between the current file and its
     * latest backup.
     *
     * @param repoPath absolute path to the repository root
     * @param filePath relative path of the file inside the repository
     * @return the unified diff string, or a descriptive message if no backup exists
     */
    public String getFileDiff(String repoPath, String filePath) {
        Path latestBackup = findLatestBackup(repoPath, filePath);
        if (latestBackup == null) {
            String msg = "No backup exists for " + filePath + " — cannot generate diff.";
            logger.info(msg);
            return msg;
        }

        Path currentFile = Paths.get(repoPath, filePath).toAbsolutePath().normalize();
        if (!Files.exists(currentFile)) {
            String msg = "Current file does not exist: " + currentFile;
            logger.warn(msg);
            return msg;
        }

        try {
            List<String> backupLines = Files.readAllLines(latestBackup, StandardCharsets.UTF_8);
            List<String> currentLines = Files.readAllLines(currentFile, StandardCharsets.UTF_8);
            return buildUnifiedDiff(latestBackup.toString(), currentFile.toString(),
                    backupLines, currentLines);
        } catch (IOException e) {
            logger.error("Failed to generate diff for {}: {}", filePath, e.getMessage(), e);
            return "Error generating diff: " + e.getMessage();
        }
    }

    // ── Patch (existing — now with auto-backup) ──────────────────────────

    public boolean applyPatch(String repoPath, Vulnerability vuln) {
        String proposedFix = Optional.ofNullable(vuln.getProposedFix()).map(String::trim).orElse("");
        if (proposedFix.isEmpty()) {
            logger.warn("Cannot apply patch for {}: proposed fix is empty.", vuln.getId());
            return false;
        }

        Path targetFile = Paths.get(repoPath, vuln.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(targetFile) || !Files.isRegularFile(targetFile)) {
            logger.error("Target file does not exist: {}", targetFile);
            return false;
        }

        // Auto-backup before applying any changes
        String backupPath = createBackup(repoPath, vuln.getFilePath());
        if (backupPath == null) {
            logger.warn("Backup failed for {} — proceeding with patch anyway.", vuln.getFilePath());
        }

        try {
            String originalContent = Files.readString(targetFile, StandardCharsets.UTF_8);
            String codeSnippet = Optional.ofNullable(vuln.getCodeSnippet()).map(String::trim).orElse("");
            if (codeSnippet.isEmpty()) {
                logger.warn("Cannot apply patch for {}: code snippet is empty.", vuln.getId());
                return false;
            }

            // Try 1: Exact text block replace
            if (originalContent.contains(codeSnippet)) {
                String updatedContent = originalContent.replace(codeSnippet, proposedFix);
                Files.writeString(targetFile, updatedContent, StandardCharsets.UTF_8);
                logger.info("Successfully applied patch to {} using exact text matching.", targetFile.getFileName());
                return true;
            }

            // Try 2: Line-number based replacement if exact match failed (e.g. whitespace
            // differences)
            List<String> fileLines = Files.readAllLines(targetFile, StandardCharsets.UTF_8);
            int startLine = vuln.getLineNumber(); // 1-indexed
            int startIdx = startLine - 1;

            if (startIdx >= 0 && startIdx < fileLines.size()) {
                String[] snippetLines = codeSnippet.split("\\r?\\n");
                int snippetLineCount = snippetLines.length;
                int endIdx = Math.min(startIdx + snippetLineCount, fileLines.size());

                List<String> updatedLines = new ArrayList<>(fileLines.subList(0, startIdx));
                // Add the proposed fix lines
                updatedLines.addAll(Arrays.asList(proposedFix.split("\\r?\\n")));
                // Add the remaining lines of the original file
                if (endIdx < fileLines.size()) {
                    updatedLines.addAll(fileLines.subList(endIdx, fileLines.size()));
                }

                Files.write(targetFile, updatedLines, StandardCharsets.UTF_8);
                logger.info("Successfully applied patch to {} at line {} using line-number based replacement.",
                        targetFile.getFileName(), startLine);
                return true;
            }

            logger.error("Failed to match code snippet or line number in {}", targetFile.getFileName());
            return false;
        } catch (Exception e) {
            logger.error("Exception applying patch to file {}", targetFile, e);
            return false;
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Locates the latest backup for the given file by scanning timestamped
     * backup directories in reverse-lexicographic order.
     */
    private Path findLatestBackup(String repoPath, String filePath) {
        Path backupsRoot = Paths.get(repoPath, agentConfig.getBackupDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(backupsRoot)) {
            logger.debug("Backups root does not exist: {}", backupsRoot);
            return null;
        }

        try (Stream<Path> dirs = Files.list(backupsRoot)) {
            List<Path> timestampDirs = dirs
                    .filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

            for (Path tsDir : timestampDirs) {
                Path candidate = tsDir.resolve(filePath).normalize();
                if (Files.isRegularFile(candidate)) {
                    logger.debug("Found latest backup for {}: {}", filePath, candidate);
                    return candidate;
                }
            }
        } catch (IOException e) {
            logger.error("Error scanning backup directories: {}", e.getMessage(), e);
        }

        logger.debug("No backup found for {}", filePath);
        return null;
    }

    private Path resolveSecurePath(String repoPath, String relativePath) {
        try {
            Path root = Paths.get(repoPath).toAbsolutePath().normalize().toRealPath();
            Path resolved = root.resolve(relativePath).normalize();
            if (!resolved.startsWith(root))
                throw new IllegalArgumentException("Path escapes repository");
            Path current = root;
            for (Path component : root.relativize(resolved)) {
                current = current.resolve(component);
                if (Files.isSymbolicLink(current)) {
                    throw new IllegalArgumentException("Symbolic-link paths are not permitted");
                }
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                        && !current.toRealPath().startsWith(root)) {
                    throw new IllegalArgumentException("Resolved path escapes repository");
                }
            }
            return resolved;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid repository path", e);
        }
    }

    private void createDirectoriesSecure(Path root, Path directory) throws IOException {
        Path current = root;
        for (Path component : root.relativize(directory)) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unsafe backup directory");
                }
                if (!current.toRealPath().startsWith(root))
                    throw new IOException("Backup directory escapes repository");
            } else {
                Files.createDirectory(current);
            }
        }
    }

    /**
     * Builds a simple unified-diff-style output comparing two line lists.
     */
    private String buildUnifiedDiff(String oldLabel, String newLabel,
            List<String> oldLines, List<String> newLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(oldLabel).append(" (backup)\n");
        sb.append("+++ ").append(newLabel).append(" (current)\n");

        int maxLen = Math.max(oldLines.size(), newLines.size());
        int i = 0;
        while (i < maxLen) {
            // Find next differing region
            int matchStart = i;
            while (i < oldLines.size() && i < newLines.size()
                    && oldLines.get(i).equals(newLines.get(i))) {
                i++;
            }

            if (i >= maxLen) {
                break; // remainder was identical
            }

            // Emit context (up to 3 lines before the change)
            int contextStart = Math.max(matchStart, i - 3);
            sb.append(String.format("@@ -%d,%d +%d,%d @@%n",
                    contextStart + 1, Math.min(oldLines.size(), maxLen) - contextStart,
                    contextStart + 1, Math.min(newLines.size(), maxLen) - contextStart));

            for (int c = contextStart; c < i; c++) {
                sb.append(" ").append(oldLines.get(c)).append("\n");
            }

            // Collect removed / added lines
            int oldEnd = i;
            int newEnd = i;
            while (oldEnd < oldLines.size() && (newEnd >= newLines.size()
                    || !oldLines.get(oldEnd).equals(newEnd < newLines.size() ? newLines.get(newEnd) : null))) {
                oldEnd++;
            }
            while (newEnd < newLines.size() && (oldEnd >= oldLines.size()
                    || !newLines.get(newEnd).equals(oldEnd < oldLines.size() ? oldLines.get(oldEnd) : null))) {
                newEnd++;
            }

            for (int r = i; r < oldEnd; r++) {
                sb.append("-").append(oldLines.get(r)).append("\n");
            }
            for (int a = i; a < newEnd; a++) {
                sb.append("+").append(newLines.get(a)).append("\n");
            }

            i = Math.max(oldEnd, newEnd);
        }

        if (sb.indexOf("@@") == -1) {
            return "Files are identical — no differences found.";
        }
        return sb.toString();
    }
}
