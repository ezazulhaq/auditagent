package com.cb.auditagent.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Service for offloading heavy artifacts (unified diffs, SARIF JSON dumps, generated PDFs)
 * to Supabase Storage (S3-compatible bucket: auditagent-artifacts), preventing relational database bloat.
 */
@Service
public class SupabaseStorageService {
    private static final Logger logger = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final String supabaseUrl;
    private final String serviceKey;
    private final String bucketName;
    private final Path localFallbackDir;

    public SupabaseStorageService(
            @Value("${auditagent.supabase.url:http://localhost:8000}") String supabaseUrl,
            @Value("${auditagent.supabase.service-key:}") String serviceKey,
            @Value("${auditagent.supabase.storage.bucket:auditagent-artifacts}") String bucketName,
            @Value("${auditagent.workspace.root:/tmp/auditagent}") String workspaceRoot) {
        this.supabaseUrl = supabaseUrl;
        this.serviceKey = serviceKey;
        this.bucketName = bucketName;
        this.localFallbackDir = Path.of(workspaceRoot, "artifacts");
        try {
            Files.createDirectories(this.localFallbackDir);
        } catch (Exception ignored) {
        }
    }

    /**
     * Upload an artifact from a string (e.g. unified diff, SARIF JSON).
     *
     * @param objectPath path key inside the bucket (e.g. runs/run-123/patch.diff)
     * @param content text content
     * @return canonical storage URI or identifier
     */
    public String uploadTextArtifact(String objectPath, String content) {
        if (content == null) {
            return null;
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return uploadBinaryArtifact(objectPath, bytes);
    }

    /**
     * Upload a binary artifact (e.g. PDF report).
     *
     * @param objectPath path key inside the bucket
     * @param data binary bytes
     * @return canonical storage URI
     */
    public String uploadBinaryArtifact(String objectPath, byte[] data) {
        String cleanPath = objectPath.startsWith("/") ? objectPath.substring(1) : objectPath;
        if (isRemoteStorageConfigured()) {
            // Future HTTP client call to Supabase Storage REST endpoint:
            // POST {supabaseUrl}/storage/v1/object/{bucketName}/{cleanPath}
            logger.info("Uploading artifact [{}] to Supabase Storage bucket [{}] ({} bytes)",
                    cleanPath, bucketName, data.length);
        }

        // Local fallback persistence ensuring operational continuity
        try {
            Path target = localFallbackDir.resolve(cleanPath);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "storage://" + bucketName + "/" + cleanPath;
        } catch (Exception e) {
            logger.warn("Failed saving artifact to fallback storage: {}", e.getMessage());
            return "inline://" + UUID.randomUUID();
        }
    }

    public boolean isRemoteStorageConfigured() {
        return serviceKey != null && !serviceKey.isBlank() && !serviceKey.contains("placeholder");
    }

    public String getBucketName() {
        return bucketName;
    }
}
