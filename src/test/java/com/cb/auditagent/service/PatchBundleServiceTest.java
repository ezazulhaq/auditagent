package com.cb.auditagent.service;

import com.cb.auditagent.domain.PatchBundle;
import com.cb.auditagent.service.storage.SupabaseStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatchBundleServiceTest {

    @TempDir
    Path tempDir;

    private SupabaseStorageService storageService;
    private DatabaseService databaseService;
    private SourceControlProvider sourceControl;
    private ObjectMapper objectMapper;
    private PatchBundleService patchBundleService;

    private Path workspace;
    private final String runId = "run-test-123";
    private final String vulnId = "VULN-000001";

    @BeforeEach
    void setUp() throws Exception {
        storageService = mock(SupabaseStorageService.class);
        databaseService = mock(DatabaseService.class);
        sourceControl = mock(SourceControlProvider.class);
        objectMapper = new ObjectMapper();
        patchBundleService = new PatchBundleService(storageService, databaseService, sourceControl, objectMapper);

        workspace = Files.createDirectory(tempDir.resolve("workspace"));
    }

    @Test
    @DisplayName("createBundle extracts diff, changed files, hashes, and text file contents")
    void createBundle_extractsAllRequiredMetadataAndContent() throws Exception {
        Path srcDir = Files.createDirectories(workspace.resolve("src"));
        Path helloFile = srcDir.resolve("Hello.java");
        Files.writeString(helloFile, "public class Hello {}");

        when(sourceControl.changedFiles(workspace)).thenReturn(List.of("src/Hello.java"));
        when(sourceControl.workspaceDiff(workspace)).thenReturn("diff --git a/src/Hello.java...");
        when(sourceControl.changedFileHashes(workspace)).thenReturn(Map.of("src/Hello.java", "hash123"));

        PatchBundle bundle = patchBundleService.createBundle(
                runId, vulnId, 101L, "main", "sha-base-1", "auditagent/fix-1", workspace, "Fixed issue"
        );

        assertNotNull(bundle);
        assertEquals(runId, bundle.runId());
        assertEquals(vulnId, bundle.vulnerabilityId());
        assertEquals(101L, bundle.repositoryId());
        assertEquals("main", bundle.baseBranch());
        assertEquals("sha-base-1", bundle.baseSha());
        assertEquals("auditagent/fix-1", bundle.branchName());
        assertEquals("diff --git a/src/Hello.java...", bundle.unifiedDiff());
        assertEquals(List.of("src/Hello.java"), bundle.changedFiles());
        assertEquals("hash123", bundle.fileHashes().get("src/Hello.java"));
        assertEquals("public class Hello {}", bundle.fileContents().get("src/Hello.java"));
        assertEquals("Fixed issue", bundle.summary());
    }

    @Test
    @DisplayName("uploadPatchBundle uploads bundle JSON and diff, and records DB run changes")
    void uploadPatchBundle_persistsArtifactsAndDbChanges() {
        PatchBundle bundle = new PatchBundle(
                runId, vulnId, 101L, "main", "sha-base-1", "auditagent/fix-1",
                "diff content", List.of("src/Foo.java"), Map.of("src/Foo.java", "hashFoo"),
                Map.of("src/Foo.java", "content"), "summary", Instant.now().toString()
        );

        when(storageService.uploadTextArtifact(eq("runs/" + runId + "/patch-bundle.json"), anyString()))
                .thenReturn("storage://bucket/runs/" + runId + "/patch-bundle.json");

        String uri = patchBundleService.uploadPatchBundle(bundle);

        assertNotNull(uri);
        assertTrue(uri.contains(runId));
        verify(storageService).uploadTextArtifact(eq("runs/" + runId + "/patch-bundle.json"), anyString());
        verify(storageService).uploadTextArtifact(eq("runs/" + runId + "/patch.diff"), eq("diff content"));
        verify(databaseService).addRunChange(eq(runId), eq("src/Foo.java"), isNull(), eq("hashFoo"), isNull());
    }

    @Test
    @DisplayName("getPatchBundle downloads and deserializes bundle from storage")
    void getPatchBundle_downloadsAndDeserializes() throws Exception {
        PatchBundle bundle = new PatchBundle(
                runId, vulnId, 101L, "main", "sha-base-1", "auditagent/fix-1",
                "diff", List.of("src/Foo.java"), Map.of("src/Foo.java", "hashFoo"),
                Map.of("src/Foo.java", "content"), "summary", Instant.now().toString()
        );
        String json = objectMapper.writeValueAsString(bundle);

        when(storageService.downloadTextArtifact("runs/" + runId + "/patch-bundle.json")).thenReturn(json);

        Optional<PatchBundle> result = patchBundleService.getPatchBundle(runId);

        assertTrue(result.isPresent());
        assertEquals(runId, result.get().runId());
        assertEquals("src/Foo.java", result.get().changedFiles().get(0));
        assertEquals("content", result.get().fileContents().get("src/Foo.java"));
    }

    @Test
    @DisplayName("applyBundleToWorkspace applies file contents and deleted files correctly")
    void applyBundleToWorkspace_appliesContentAndDeletes() throws Exception {
        Path targetWorkspace = Files.createDirectory(tempDir.resolve("target-workspace"));
        Path oldFile = targetWorkspace.resolve("ToDelete.java");
        Files.writeString(oldFile, "obsolete");

        PatchBundle bundle = new PatchBundle(
                runId, vulnId, 101L, "main", "sha-base-1", "auditagent/fix-1",
                "diff", List.of("src/NewFile.java", "ToDelete.java"),
                Map.of("src/NewFile.java", "h1", "ToDelete.java", "h2"),
                Map.of("src/NewFile.java", "new file code", "ToDelete.java", "[DELETED]"),
                "summary", Instant.now().toString()
        );

        patchBundleService.applyBundleToWorkspace(targetWorkspace, bundle);

        assertTrue(Files.exists(targetWorkspace.resolve("src/NewFile.java")));
        assertEquals("new file code", Files.readString(targetWorkspace.resolve("src/NewFile.java")));
        assertFalse(Files.exists(oldFile));
    }

    @Test
    @DisplayName("applyBundleToWorkspace throws SecurityException on path traversal attempt")
    void applyBundleToWorkspace_throwsOnPathTraversal() {
        PatchBundle maliciousBundle = new PatchBundle(
                runId, vulnId, 101L, "main", "sha-base-1", "auditagent/fix-1",
                "diff", List.of("../outside.txt"), Map.of("../outside.txt", "h"),
                Map.of("../outside.txt", "malicious payload"), "summary", Instant.now().toString()
        );

        assertThrows(SecurityException.class, () ->
                patchBundleService.applyBundleToWorkspace(workspace, maliciousBundle)
        );
    }
}
