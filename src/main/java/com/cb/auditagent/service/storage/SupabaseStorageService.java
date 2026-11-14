package com.cb.auditagent.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
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
    private final boolean remoteEnabled;
    private final int timeoutSeconds;
    private final Path localFallbackDir;
    private final WebClient webClient;

    @Autowired
    public SupabaseStorageService(
            @Value("${auditagent.supabase.url:http://localhost:8000}") String supabaseUrl,
            @Value("${auditagent.supabase.service-key:}") String serviceKey,
            @Value("${auditagent.supabase.storage.bucket:auditagent-artifacts}") String bucketName,
            @Value("${auditagent.supabase.storage.enabled:true}") boolean remoteEnabled,
            @Value("${auditagent.supabase.storage.timeout-seconds:30}") int timeoutSeconds,
            @Value("${auditagent.workspace.root:/tmp/auditagent}") String workspaceRoot,
            @Autowired(required = false) WebClient.Builder webClientBuilder) {
        this.supabaseUrl = supabaseUrl;
        this.serviceKey = serviceKey;
        this.bucketName = bucketName;
        this.remoteEnabled = remoteEnabled;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.localFallbackDir = Path.of(workspaceRoot, "artifacts");

        try {
            Files.createDirectories(this.localFallbackDir);
        } catch (Exception ignored) {
        }

        String baseUrl = supabaseUrl != null && !supabaseUrl.isBlank() ? supabaseUrl : "http://localhost:8000";
        if (webClientBuilder != null) {
            this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        } else {
            this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        }
    }

    public SupabaseStorageService(
            String supabaseUrl,
            String serviceKey,
            String bucketName,
            String workspaceRoot) {
        this(supabaseUrl, serviceKey, bucketName, true, 30, workspaceRoot, null);
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
     * Upload a binary artifact (e.g. PDF report, unified diff).
     *
     * @param objectPath path key inside the bucket
     * @param data binary bytes
     * @return canonical storage URI
     */
    public String uploadBinaryArtifact(String objectPath, byte[] data) {
        if (data == null) {
            return null;
        }
        String cleanPath = objectPath.startsWith("/") ? objectPath.substring(1) : objectPath;
        if (cleanPath.startsWith("storage://" + bucketName + "/")) {
            cleanPath = cleanPath.substring(("storage://" + bucketName + "/").length());
        }

        if (isRemoteStorageConfigured()) {
            try {
                logger.info("Uploading artifact [{}] to Supabase Storage bucket [{}] ({} bytes)",
                        cleanPath, bucketName, data.length);
                webClient.post()
                        .uri("/storage/v1/object/" + bucketName + "/" + cleanPath)
                        .header("apikey", serviceKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .bodyValue(data)
                        .retrieve()
                        .toBodilessEntity()
                        .block(Duration.ofSeconds(timeoutSeconds));

                return "storage://" + bucketName + "/" + cleanPath;
            } catch (Exception e) {
                logger.warn("Failed uploading artifact [{}] to Supabase Storage: {}, falling back to local storage",
                        cleanPath, e.getMessage());
            }
        }

        return saveToLocalFallback(cleanPath, data);
    }

    /**
     * Download a binary artifact from Supabase storage, with local disk fallback.
     *
     * @param objectPath path key inside the bucket or storage:// URI
     * @return artifact bytes or null if not found
     */
    public byte[] downloadArtifact(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return null;
        }
        String cleanPath = objectPath.startsWith("/") ? objectPath.substring(1) : objectPath;
        if (cleanPath.startsWith("storage://" + bucketName + "/")) {
            cleanPath = cleanPath.substring(("storage://" + bucketName + "/").length());
        }

        if (isRemoteStorageConfigured()) {
            try {
                byte[] data = webClient.get()
                        .uri("/storage/v1/object/" + bucketName + "/" + cleanPath)
                        .header("apikey", serviceKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block(Duration.ofSeconds(timeoutSeconds));
                if (data != null) {
                    return data;
                }
            } catch (Exception e) {
                logger.warn("Failed downloading artifact [{}] from Supabase Storage: {}, attempting local fallback",
                        cleanPath, e.getMessage());
            }
        }

        try {
            Path target = localFallbackDir.resolve(cleanPath);
            if (Files.exists(target)) {
                return Files.readAllBytes(target);
            }
        } catch (Exception e) {
            logger.warn("Failed reading artifact from local fallback: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Download text artifact content from Supabase storage or local disk fallback.
     *
     * @param objectPath path key inside the bucket or storage:// URI
     * @return artifact string content or null if not found
     */
    public String downloadTextArtifact(String objectPath) {
        byte[] bytes = downloadArtifact(objectPath);
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    private String saveToLocalFallback(String cleanPath, byte[] data) {
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
        return remoteEnabled
                && serviceKey != null
                && !serviceKey.isBlank()
                && !serviceKey.contains("placeholder")
                && supabaseUrl != null
                && !supabaseUrl.isBlank();
    }

    public String getBucketName() {
        return bucketName;
    }

    public Path getLocalFallbackDir() {
        return localFallbackDir;
    }
}
