package com.cb.auditagent.service.storage;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseStorageClientTest {

    private WireMockServer wireMockServer;
    private SupabaseStorageService storageService;
    private String baseUrl;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        baseUrl = "http://localhost:" + wireMockServer.port();

        storageService = new SupabaseStorageService(
                baseUrl,
                "test-service-key-12345",
                "auditagent-artifacts",
                true,
                5,
                tempDir.toString(),
                WebClient.builder()
        );
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    void testSuccessfulBinaryUpload() {
        byte[] payload = "diff --git a/App.java b/App.java".getBytes(StandardCharsets.UTF_8);

        wireMockServer.stubFor(post(urlEqualTo("/storage/v1/object/auditagent-artifacts/runs/run-1/patch.diff"))
                .withHeader("Authorization", equalTo("Bearer test-service-key-12345"))
                .withHeader("Content-Type", containing("application/octet-stream"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"Key\":\"auditagent-artifacts/runs/run-1/patch.diff\"}")));

        String uri = storageService.uploadBinaryArtifact("runs/run-1/patch.diff", payload);

        assertEquals("storage://auditagent-artifacts/runs/run-1/patch.diff", uri);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/storage/v1/object/auditagent-artifacts/runs/run-1/patch.diff"))
                .withHeader("Authorization", equalTo("Bearer test-service-key-12345"))
                .withRequestBody(binaryEqualTo(payload)));
    }

    @Test
    void testSuccessfulTextUploadAndDownload() {
        String content = "{\"findings\": [], \"status\": \"CLEAN\"}";

        wireMockServer.stubFor(post(urlEqualTo("/storage/v1/object/auditagent-artifacts/reports/sarif.json"))
                .withHeader("Authorization", equalTo("Bearer test-service-key-12345"))
                .willReturn(aResponse().withStatus(200).withBody("{\"Key\":\"ok\"}")));

        wireMockServer.stubFor(get(urlEqualTo("/storage/v1/object/auditagent-artifacts/reports/sarif.json"))
                .withHeader("Authorization", equalTo("Bearer test-service-key-12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(content.getBytes(StandardCharsets.UTF_8))));

        String uri = storageService.uploadTextArtifact("reports/sarif.json", content);
        assertEquals("storage://auditagent-artifacts/reports/sarif.json", uri);

        String downloaded = storageService.downloadTextArtifact("reports/sarif.json");
        assertEquals(content, downloaded);
    }

    @Test
    void testFallbackToLocalOnRemoteHttp500(@TempDir Path tempDir) {
        byte[] payload = "fallback-content".getBytes(StandardCharsets.UTF_8);

        wireMockServer.stubFor(post(urlEqualTo("/storage/v1/object/auditagent-artifacts/runs/err/patch.diff"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        String uri = storageService.uploadBinaryArtifact("runs/err/patch.diff", payload);

        assertEquals("storage://auditagent-artifacts/runs/err/patch.diff", uri);

        Path fallbackFile = storageService.getLocalFallbackDir().resolve("runs/err/patch.diff");
        assertTrue(Files.exists(fallbackFile), "File should be written to local fallback directory on remote error");

        byte[] downloaded = storageService.downloadArtifact("runs/err/patch.diff");
        assertNotNull(downloaded);
        assertArrayEquals(payload, downloaded);
    }

    @Test
    void testDisabledRemoteStorageWritesOnlyToFallback(@TempDir Path tempDir) {
        SupabaseStorageService disabledService = new SupabaseStorageService(
                baseUrl,
                "test-key",
                "auditagent-artifacts",
                false, // remote storage disabled
                5,
                tempDir.toString(),
                WebClient.builder()
        );

        byte[] data = "offline-content".getBytes(StandardCharsets.UTF_8);
        String uri = disabledService.uploadBinaryArtifact("offline/data.txt", data);

        assertEquals("storage://auditagent-artifacts/offline/data.txt", uri);
        wireMockServer.verify(0, postRequestedFor(anyUrl()));

        byte[] downloaded = disabledService.downloadArtifact("offline/data.txt");
        assertNotNull(downloaded);
        assertArrayEquals(data, downloaded);
    }
}
