package com.cb.auditagent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OkHttp interceptor that preserves Gemini's thought_signature across
 * multi-turn tool-calling conversations via the OpenAI compatibility layer.
 *
 * Gemini returns thought_signature at:
 * response ->
 * choices[].message.tool_calls[].extra_content.google.thought_signature
 *
 * LangChain4j's OpenAI client strips unknown fields when deserializing into
 * AiMessage / ToolExecutionRequest, so by the time it rebuilds the next request
 * the signature is gone. This interceptor:
 * 1. Captures signatures from every response keyed by tool-call id.
 * 2. Re-injects them into the outgoing request's assistant messages that
 * carry tool_calls, using the same extra_content.google structure.
 */
public class GeminiThoughtSignatureInterceptor implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger(GeminiThoughtSignatureInterceptor.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> signatureMap = new ConcurrentHashMap<>();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // -- 1. INJECT signatures into the outgoing request --
        if (request.body() != null) {
            try {
                Buffer buffer = new Buffer();
                request.body().writeTo(buffer);
                String bodyStr = buffer.readString(StandardCharsets.UTF_8);

                JsonNode root = mapper.readTree(bodyStr);
                boolean modified = false;

                if (root.has("messages")) {
                    for (JsonNode message : root.get("messages")) {
                        if (message.has("tool_calls")) {
                            for (JsonNode toolCall : message.get("tool_calls")) {
                                if (toolCall.has("id")) {
                                    String id = toolCall.get("id").asText();
                                    String sig = signatureMap.get(id);
                                    if (sig != null) {
                                        ObjectNode tcNode = (ObjectNode) toolCall;

                                        // Build extra_content.google.thought_signature
                                        ObjectNode googleNode = mapper.createObjectNode();
                                        googleNode.put("thought_signature", sig);
                                        ObjectNode extraContent = mapper.createObjectNode();
                                        extraContent.set("google", googleNode);
                                        tcNode.set("extra_content", extraContent);

                                        modified = true;
                                        logger.debug("INJECTED thought_signature for tool-call id={}", id);
                                    } else {
                                        logger.debug("No cached signature for tool-call id={} (map size={})",
                                                id, signatureMap.size());
                                    }
                                }
                            }
                        }
                    }
                }

                if (modified) {
                    String newBody = mapper.writeValueAsString(root);
                    logger.debug("MODIFIED OUTGOING BODY (first 500 chars): {}",
                            newBody.substring(0, Math.min(500, newBody.length())));
                    RequestBody newRequestBody = RequestBody.create(
                            newBody, request.body().contentType());
                    request = request.newBuilder()
                            .method(request.method(), newRequestBody)
                            .build();
                }
            } catch (Exception e) {
                logger.error("Failed to inject thought_signature: {}", e.getMessage(), e);
            }
        }

        // -- 2. EXECUTE the request --
        Response response = chain.proceed(request);

        // -- 3. EXTRACT signatures from the response --
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
            String bodyStr = responseBody.string();
            try {
                JsonNode root = mapper.readTree(bodyStr);
                if (root.has("choices")) {
                    for (JsonNode choice : root.get("choices")) {
                        JsonNode msg = choice.path("message");
                        if (msg.has("tool_calls")) {
                            for (JsonNode toolCall : msg.get("tool_calls")) {
                                String id = toolCall.path("id").asText(null);
                                if (id == null)
                                    continue;

                                String sig = extractSignature(toolCall);
                                if (sig != null) {
                                    signatureMap.put(id, sig);
                                    logger.debug("CAPTURED thought_signature for tool-call id={} (map size={})",
                                            id, signatureMap.size());
                                } else {
                                    logger.debug("No thought_signature found in tool_call id={}. RAW: {}",
                                            id, toolCall);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to extract thought_signature: {}", e.getMessage(), e);
            }

            // Reconstruct the response body (OkHttp bodies are one-shot)
            ResponseBody newBody = ResponseBody.create(bodyStr, responseBody.contentType());
            return response.newBuilder().body(newBody).build();
        }

        return response;
    }

    /**
     * Look for thought_signature in all known locations within a tool_call node.
     */
    private String extractSignature(JsonNode toolCall) {
        // Primary: extra_content.google.thought_signature (Gemini OpenAI compat)
        String sig = toolCall.path("extra_content").path("google")
                .path("thought_signature").asText(null);
        if (sig != null)
            return sig;

        // Fallback: function.thought_signature
        sig = toolCall.path("function").path("thought_signature").asText(null);
        if (sig != null)
            return sig;

        // Fallback: top-level thought_signature
        sig = toolCall.path("thought_signature").asText(null);
        if (sig != null)
            return sig;

        // Fallback: inside the arguments JSON string
        String argsStr = toolCall.path("function").path("arguments").asText(null);
        if (argsStr != null) {
            try {
                JsonNode argsNode = mapper.readTree(argsStr);
                sig = argsNode.path("thought_signature").asText(null);
                if (sig != null)
                    return sig;
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
