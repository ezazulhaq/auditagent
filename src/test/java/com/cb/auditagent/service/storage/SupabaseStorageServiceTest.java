package com.cb.auditagent.service.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SupabaseStorageServiceTest {

    @Test
    void shouldUploadTextArtifactToFallbackStorage(@TempDir Path tempDir) {
        SupabaseStorageService storageService = new SupabaseStorageService(
                "http://localhost:8000", "", "auditagent-artifacts", tempDir.toString()
        );

        String resultUri = storageService.uploadTextArtifact("runs/run-1/patch.diff", "--- a/File.java\n+++ b/File.java\n");

        assertNotNull(resultUri);
        assertTrue(resultUri.startsWith("storage://auditagent-artifacts/runs/run-1/patch.diff"));
    }

    @Test
    void shouldUploadBinaryArtifact(@TempDir Path tempDir) {
        SupabaseStorageService storageService = new SupabaseStorageService(
                "http://localhost:8000", "", "auditagent-artifacts", tempDir.toString()
        );

        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
        String resultUri = storageService.uploadBinaryArtifact("reports/report-1.pdf", pdfBytes);

        assertNotNull(resultUri);
        assertTrue(resultUri.contains("report-1.pdf"));
    }
}
