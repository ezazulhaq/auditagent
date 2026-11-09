package com.cb.auditagent.client.jev;

import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class JevClientTest {

    private JevProperties jevProperties;
    private LlmProperties llmProperties;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private HttpClient mockHttpClient;
    private JevClient jevClient;

    @BeforeEach
    void setUp() {
        jevProperties = new JevProperties();
        jevProperties.setApiKey("test-jev-key");
        jevProperties.setBaseUrl("https://openrouter.ai/api/v1");
        jevProperties.setModelName("typesafe/jev-1.13");
        jevProperties.setMaxRetries(1);
        jevProperties.setTimeoutMs(1000);

        llmProperties = new LlmProperties();
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        mockHttpClient = Mockito.mock(HttpClient.class);

        jevClient = new JevClient(jevProperties, llmProperties, objectMapper, meterRegistry, mockHttpClient);
    }

    @Test
    void testResolveEndpointUrl() {
        assertEquals("https://openrouter.ai/api/v1/systemone", jevClient.resolveEndpointUrl(null));
        assertEquals("https://openrouter.ai/api/v1/systemone", jevClient.resolveEndpointUrl("https://openrouter.ai/api/v1/"));
        assertEquals("https://openrouter.ai/api/v1/systemone", jevClient.resolveEndpointUrl("https://openrouter.ai/api/v1/systemone"));
        assertEquals("https://custom.ai/systemone", jevClient.resolveEndpointUrl("https://custom.ai"));
    }

    @Test
    void testResolveApiKey() {
        assertEquals("test-jev-key", jevClient.resolveApiKey(null));
        assertEquals("explicit-key", jevClient.resolveApiKey("explicit-key"));

        jevProperties.setApiKey("");
        llmProperties.getOpenai().setApiKey("openai-fallback-key");
        assertEquals("openai-fallback-key", jevClient.resolveApiKey(null));
    }

    @Test
    void testExecuteSuccess() throws Exception {
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("""
                {
                    "id": "gen-123",
                    "model": "typesafe/jev-1.13",
                    "answers": {
                        "next_stage": {
                            "value": "DISCOVERY",
                            "confidence": 0.95
                        }
                    },
                    "usage": {
                        "prompt_tokens": 120,
                        "completion_tokens": 0,
                        "total_tokens": 120
                    }
                }
                """);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        JevRequest request = new JevRequest(
                "typesafe/jev-1.13",
                "Context info",
                List.of(JevQuestion.choice("next_stage", "Next stage?", List.of("DISCOVERY", "SECURITY")))
        );

        JevResponse response = jevClient.execute(request, "supervisor_routing");

        assertNotNull(response);
        assertEquals("DISCOVERY", response.getAnswerValue("next_stage"));
        assertEquals(0.95, response.getAnswerConfidence("next_stage"));
        assertNotNull(response.getUsage());
        assertEquals(120, response.getUsage().getTotalTokens());

        assertNotNull(meterRegistry.find("jev.requests.total").tag("status", "success").counter());
    }

    @Test
    void testExecuteFailureWithRetry() throws Exception {
        HttpResponse<String> mock500 = Mockito.mock(HttpResponse.class);
        when(mock500.statusCode()).thenReturn(500);
        when(mock500.body()).thenReturn("Internal Server Error");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mock500);

        JevRequest request = new JevRequest("typesafe/jev-1.13", "Context", List.of());

        assertThrows(IOException.class, () -> jevClient.execute(request, "test_op"));
        assertNotNull(meterRegistry.find("jev.requests.total").tag("status", "failure").counter());
    }

    @Test
    void testSerializationStructure() throws Exception {
        JevQuestion q = JevQuestion.choice("ping", "System check", List.of("PING", "PONG"));
        JevRequest req = new JevRequest("typesafe/jev-1.14", "Health check test", List.of(q));
        String json = objectMapper.writeValueAsString(req);
        assertTrue(json.contains("\"questions\":{"), "questions must be serialized as a JSON object, was: " + json);
        assertTrue(json.contains("\"ping\":{"), "question id must be key in questions object");
        assertTrue(json.contains("\"instructions\":\"System check\""), "instructions must be present");
        assertTrue(json.contains("\"criteria\":{"), "criteria must be present for choice");
        assertFalse(json.contains("\"questions\":["), "questions must NOT be an array");
    }

    @Test
    void testOpenRouterChoiceAnswerFormat() throws Exception {
        String json = """
                {
                    "model": "typesafe/jev-1.14",
                    "answers": {
                        "ping": {
                            "type": "choice",
                            "choice": "PING",
                            "probabilities": {"PING": 0.9, "PONG": 0.1},
                            "confidence": 0.8
                        }
                    },
                    "usage": {
                        "input_tokens": 150,
                        "output_tokens": 20
                    }
                }
                """;
        JevResponse response = objectMapper.readValue(json, JevResponse.class);
        assertEquals("PING", response.getAnswerValue("ping"));
        assertEquals(0.8, response.getAnswerConfidence("ping"));
        assertEquals(170, response.getUsage().getTotalTokens());
    }
}

