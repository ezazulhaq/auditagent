package com.cb.auditagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipArchiveExtractorTest {

    private ZipArchiveExtractor extractor;

    @TempDir
    Path tempDir;

    private Path zipFile;
    private Path extractDir;

    @BeforeEach
    void setUp() throws IOException {
        extractor = new ZipArchiveExtractor();
        zipFile = tempDir.resolve("test-project.zip");
        extractDir = tempDir.resolve("extracted");
    }

    @AfterEach
    void tearDown() {
        extractor.deleteRecursively(extractDir);
    }

    private void createZip(Path destination, String[][] entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))) {
            for (String[] entry : entries) {
                String path = entry[0];
                String content = entry[1];
                ZipEntry zipEntry = new ZipEntry(path);
                zos.putNextEntry(zipEntry);
                if (content != null) {
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
    }

    @Test
    void testExtractValidZip() throws IOException {
        createZip(zipFile, new String[][] {
            { "src/Main.java", "public class Main { public static void main(String[] args) {} }" },
            { "src/util/Helper.java", "public class Helper {}" },
            { "README.md", "# Test Project" }
        });

        ZipArchiveExtractor.ExtractionResult result = extractor.extract(zipFile, extractDir);

        assertEquals(3, result.extractedFilesCount());
        assertTrue(Files.exists(extractDir.resolve("src/Main.java")));
        assertTrue(Files.exists(extractDir.resolve("src/util/Helper.java")));
        assertTrue(Files.exists(extractDir.resolve("README.md")));
    }

    @Test
    void testExcludedDirectoriesSkipped() throws IOException {
        createZip(zipFile, new String[][] {
            { "src/index.js", "console.log('hello');" },
            { "node_modules/express/index.js", "module.exports = {};" },
            { "target/classes/App.class", "bytecode" },
            { ".git/HEAD", "ref: refs/heads/main" }
        });

        ZipArchiveExtractor.ExtractionResult result = extractor.extract(zipFile, extractDir);

        assertEquals(1, result.extractedFilesCount());
        assertTrue(Files.exists(extractDir.resolve("src/index.js")));
        assertFalse(Files.exists(extractDir.resolve("node_modules")));
        assertFalse(Files.exists(extractDir.resolve("target")));
        assertFalse(Files.exists(extractDir.resolve(".git")));
    }

    @Test
    void testZipSlipAttemptThrowsSecurityException() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry malicious = new ZipEntry("../outside.txt");
            zos.putNextEntry(malicious);
            zos.write("malicious content".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(zipFile, baos.toByteArray());

        assertThrows(SecurityException.class, () -> extractor.extract(zipFile, extractDir));
    }

    @Test
    void testNonZipFileThrowsIllegalArgumentException() throws IOException {
        Files.writeString(zipFile, "this is not a zip file content");

        assertThrows(IllegalArgumentException.class, () -> extractor.extract(zipFile, extractDir));
    }

    @Test
    void testEmptyZipThrowsIllegalArgumentException() throws IOException {
        createZip(zipFile, new String[][] {});

        assertThrows(IllegalArgumentException.class, () -> extractor.extract(zipFile, extractDir));
    }
}

