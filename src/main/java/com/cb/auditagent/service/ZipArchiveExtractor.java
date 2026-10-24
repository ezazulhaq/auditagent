package com.cb.auditagent.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ZipArchiveExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ZipArchiveExtractor.class);

    public static final long MAX_ARCHIVE_SIZE_BYTES = 50 * 1024 * 1024L; // 50MB
    public static final long MAX_UNCOMPRESSED_SIZE_BYTES = 250 * 1024 * 1024L; // 250MB
    public static final int MAX_ENTRIES_COUNT = 10_000;

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", ".git", "vendor", "target", "dist", "build",
            "__pycache__", ".idea", ".vscode", ".gradle", ".next", ".nuxt");

    public record ExtractionResult(int extractedFilesCount, long totalBytesExtracted) {}

    public void validateZipHeader(Path zipFilePath) throws IOException {
        try (InputStream is = Files.newInputStream(zipFilePath)) {
            byte[] header = new byte[4];
            int read = is.read(header);
            if (read < 4 || header[0] != 0x50 || header[1] != 0x4B) {
                throw new IllegalArgumentException("The uploaded file is not a valid ZIP archive");
            }
        }
    }

    public ExtractionResult extract(Path zipFilePath, Path destinationDir) throws IOException {
        long archiveSize = Files.size(zipFilePath);
        if (archiveSize > MAX_ARCHIVE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Archive size (" + (archiveSize / (1024 * 1024)) + "MB) exceeds the maximum allowed limit of 50MB");
        }

        validateZipHeader(zipFilePath);

        Path normalizedDest = destinationDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedDest);

        int fileCount = 0;
        long totalUncompressedBytes = 0;

        try (InputStream fis = Files.newInputStream(zipFilePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                Path targetPath = normalizedDest.resolve(entryName).normalize();
                if (!targetPath.startsWith(normalizedDest)) {
                    throw new SecurityException("Archive entry attempted directory traversal (ZipSlip): " + entryName);
                }

                // Check exclusion for any directory in the relative path
                Path relativePath = normalizedDest.relativize(targetPath);
                boolean isExcluded = false;
                for (Path segment : relativePath) {
                    if (EXCLUDED_DIRS.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                        isExcluded = true;
                        break;
                    }
                }

                if (isExcluded) {
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    if (targetPath.getParent() != null) {
                        Files.createDirectories(targetPath.getParent());
                    }

                    try (OutputStream fos = Files.newOutputStream(targetPath)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            totalUncompressedBytes += len;
                            if (totalUncompressedBytes > MAX_UNCOMPRESSED_SIZE_BYTES) {
                                throw new SecurityException("Archive exceeded maximum uncompressed size of 250MB");
                            }
                            fos.write(buffer, 0, len);
                        }
                    }

                    fileCount++;
                    if (fileCount > MAX_ENTRIES_COUNT) {
                        throw new SecurityException("Archive contains too many entries (exceeds limit of 10000 files)");
                    }
                }
                zis.closeEntry();
            }
        }

        if (fileCount == 0) {
            throw new IllegalArgumentException("The ZIP archive contains no scannable source code files");
        }

        logger.info("Successfully extracted {} files ({} bytes) into {}", fileCount, totalUncompressedBytes, destinationDir);
        return new ExtractionResult(fileCount, totalUncompressedBytes);
    }

    public void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to safely clean up directory {}: {}", root, e.getMessage());
        }
    }
}

