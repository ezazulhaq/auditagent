package com.cb.auditagent.controller;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.PullRequestLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class GitHubWebhookController {
    private final GitHubAppConfig config;
    private final DatabaseService database;
    private final PullRequestLifecycleService pullRequestLifecycle;
    private final ObjectMapper objectMapper;

    public GitHubWebhookController(GitHubAppConfig config, DatabaseService database,
            PullRequestLifecycleService pullRequestLifecycle, ObjectMapper objectMapper) {
        this.config = config;
        this.database = database;
        this.pullRequestLifecycle = pullRequestLifecycle;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/github")
    public ResponseEntity<?> receive(@RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestBody String rawBody) throws Exception {
        verifySignature(signature, rawBody);
        if (!database.recordWebhookDelivery(deliveryId, eventType)) {
            return ResponseEntity.ok(Map.of("accepted", true, "duplicate", true));
        }
        try {
            if (!"pull_request".equals(eventType)) {
                return ResponseEntity.ok(Map.of("accepted", true, "ignored", true));
            }
            JsonNode payload = objectMapper.readTree(rawBody);
            String action = payload.path("action").asText();
            if ("closed".equals(action)) {
                long repositoryId = payload.path("repository").path("id").asLong();
                int pullRequestNumber = payload.path("number").asInt();
                boolean merged = payload.path("pull_request").path("merged").asBoolean();
                pullRequestLifecycle.handlePullRequestEvent(repositoryId, pullRequestNumber, merged, true);
            }
            return ResponseEntity.ok(Map.of("accepted", true));
        } catch (Exception e) {
            database.releaseWebhookDelivery(deliveryId);
            throw e;
        }
    }

    private void verifySignature(String supplied, String body) throws Exception {
        String secret = config.getWebhookSecret();
        if (secret == null || secret.isBlank())
            throw new SecurityException("GitHub webhook secret is not configured");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("GitHub webhook signature is invalid");
        }
    }
}
