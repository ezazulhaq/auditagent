package com.cb.auditagent.service;

import com.cb.auditagent.domain.PatchBundle;
import com.cb.auditagent.service.storage.SupabaseStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for serializing, uploading, and retrieving stateless patch bundles,
 * enabling worker pods to discard local clones immediately after verification
 * and allowing ingress/web pods to reconstruct workspaces on-demand at publication time.
 */
@Service
public class PatchBundleService {
    private static final Logger logger = LoggerFactory.getLogger(PatchBundleService.class);

    private final SupabaseStorageService storageService;
    private final DatabaseService databaseService;
    private final SourceControlProvider sourceControl;
    private final ObjectMapper objectMapper;

    public PatchBundleService(
            SupabaseStorageService storageService,
            DatabaseService databaseService,
            SourceControlProvider sourceControl,
            ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.databaseService = databaseService;
        this.sourceControl = sourceControl;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes workspace changes into a stateless PatchBundle record.
     */
    public PatchBundle createBundle(
            String runId,
            String vulnerabilityId,
            long repositoryId,
            String baseBranch,
            String baseSha,
            String branchName,
            Path workspace,
            String summary) {
        List<String> changedFiles = sourceControl.changedFiles(workspace);
        String unifiedDiff = sourceControl.workspaceDiff(workspace);
        Map<String, String> hashes = sourceControl.changedFileHashes(workspace);

        Map<String, String> contents = new LinkedHashMap<>();
        for (String file : changedFiles) {
            try {
                Path filePath = workspace.resolve(file).normalize();
                if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    contents.put(file, Files.readString(filePath, StandardCharsets.UTF_8));
                } else {
                    contents.put(file, "[DELETED]");
                }
            } catch (Exception e) {
                logger.warn("Could not read file content for patch bundle {}: {}", file, e.getMessage());
            }
        }

        return new PatchBundle(
                runId,
                vulnerabilityId,
                repositoryId,
                baseBranch,
                baseSha,
                branchName,
                unifiedDiff,
                changedFiles,
                hashes,
                contents,
                summary,
                Instant.now().toString());
    }

    /**
     * Persists a patch bundle into object storage and records changed files in the relational database.
     */
    public String uploadPatchBundle(PatchBundle bundle) {
        if (bundle == null || bundle.runId() == null) {
            throw new IllegalArgumentException("Cannot upload null patch bundle or bundle without runId");
        }
        try {
            String json = objectMapper.writeValueAsString(bundle);
            String bundlePath = "runs/" + bundle.runId() + "/patch-bundle.json";
            String storageUri = storageService.uploadTextArtifact(bundlePath, json);

            // Also upload unified diff artifact for debugging and transparency
            if (bundle.unifiedDiff() != null && !bundle.unifiedDiff().isBlank()) {
                storageService.uploadTextArtifact("runs/" + bundle.runId() + "/patch.diff", bundle.unifiedDiff());
            }

            // Persist file changes into run_changes table for audit trail
            if (bundle.changedFiles() != null && bundle.fileHashes() != null) {
                for (String file : bundle.changedFiles()) {
                    String afterHash = bundle.fileHashes().getOrDefault(file, "UNKNOWN");
                    databaseService.addRunChange(bundle.runId(), file, null, afterHash, null);
                }
            }

            logger.info("Successfully persisted patch bundle for run [{}] to [{}]", bundle.runId(), storageUri);
            return storageUri;
        } catch (Exception e) {
            logger.error("Failed to upload patch bundle for run {}: {}", bundle.runId(), e.getMessage(), e);
            throw new IllegalStateException("Could not persist patch bundle", e);
        }
    }

    /**
     * Helper to create and immediately upload a patch bundle from a verified workspace.
     */
    public PatchBundle createAndUploadBundle(
            String runId,
            String vulnerabilityId,
            long repositoryId,
            String baseBranch,
            String baseSha,
            String branchName,
            Path workspace,
            String summary) {
        PatchBundle bundle = createBundle(runId, vulnerabilityId, repositoryId, baseBranch, baseSha, branchName, workspace, summary);
        uploadPatchBundle(bundle);
        return bundle;
    }

    /**
     * Retrieves and deserializes a patch bundle for the given run ID from object storage.
     */
    public Optional<PatchBundle> getPatchBundle(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        try {
            String bundlePath = "runs/" + runId + "/patch-bundle.json";
            String json = storageService.downloadTextArtifact(bundlePath);
            if (json == null || json.isBlank()) {
                logger.debug("No patch bundle JSON found in storage for run [{}]", runId);
                return Optional.empty();
            }
            PatchBundle bundle = objectMapper.readValue(json, PatchBundle.class);
            return Optional.ofNullable(bundle);
        } catch (Exception e) {
            logger.warn("Failed to retrieve or deserialize patch bundle for run {}: {}", runId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Applies a downloaded patch bundle onto a freshly cloned workspace at the matching base SHA.
     */
    public void applyBundleToWorkspace(Path workspace, PatchBundle bundle) {
        if (workspace == null || bundle == null) {
            throw new IllegalArgumentException("Workspace and bundle must not be null");
        }
        try {
            Path canonicalWorkspace = workspace.toAbsolutePath().normalize().toRealPath();

            if (bundle.fileContents() != null && !bundle.fileContents().isEmpty()) {
                for (Map.Entry<String, String> entry : bundle.fileContents().entrySet()) {
                    String relPath = entry.getKey();
                    String content = entry.getValue();

                    Path target = canonicalWorkspace.resolve(relPath).normalize();
                    if (!target.startsWith(canonicalWorkspace)) {
                        throw new SecurityException("Target file path escapes workspace: " + relPath);
                    }

                    if ("[DELETED]".equals(content)) {
                        Files.deleteIfExists(target);
                    } else {
                        if (target.getParent() != null) {
                            Files.createDirectories(target.getParent());
                        }
                        Files.writeString(target, content, StandardCharsets.UTF_8);
                    }
                }
            } else if (bundle.unifiedDiff() != null && !bundle.unifiedDiff().isBlank()) {
                sourceControl.applyPatch(workspace, bundle.unifiedDiff());
            } else {
                logger.warn("Patch bundle for run {} contains neither fileContents nor unifiedDiff", bundle.runId());
            }
        } catch (IOException e) {
            logger.error("Failed to apply patch bundle for run {} to workspace {}: {}", bundle.runId(), workspace, e.getMessage(), e);
            throw new IllegalStateException("Failed to reconstruct workspace from patch bundle", e);
        }
    }
}
