package com.cb.auditagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;

public class LlmErrorClassifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern EXCEPTION_PREFIX_PATTERN = Pattern.compile(
            "^(?:(?:[a-zA-Z0-9_$.]+(?:Exception|Error)):\s*)+");

    public record ClassifiedError(
            String errorCode,
            String userMessage,
            boolean requiresLlmConfig,
            String technicalDetails
    ) {}

    public static ClassifiedError classify(Throwable throwable) {
        if (throwable == null) {
            return new ClassifiedError("REMEDIATION_FAILED", "An unknown error occurred during remediation.", false, "");
        }

        StringBuilder fullTextBuilder = new StringBuilder();
        Set<Throwable> visited = new HashSet<>();
        Throwable current = throwable;
        UserLlmConfigurationException userLlmException = null;
        boolean hasConnectionException = false;

        while (current != null && visited.add(current)) {
            if (current instanceof UserLlmConfigurationException uce && userLlmException == null) {
                userLlmException = uce;
            }
            if (current instanceof ConnectException || current instanceof UnknownHostException
                    || current instanceof SocketTimeoutException || current instanceof SSLHandshakeException
                    || current instanceof NoRouteToHostException) {
                hasConnectionException = true;
            }
            String cls = current.getClass().getName();
            fullTextBuilder.append(cls).append(" ");
            if (current.getMessage() != null) {
                fullTextBuilder.append(current.getMessage()).append(" ");
            }
            current = current.getCause();
        }

        String fullText = fullTextBuilder.toString();
        String lowerText = fullText.toLowerCase();

        // 1. Check for UserLlmConfigurationException
        if (userLlmException != null) {
            return new ClassifiedError(
                    userLlmException.getErrorCode(),
                    userLlmException.getMessage() + ". Please configure your LLM settings in your User Profile.",
                    true,
                    stripExceptionPrefixes(throwable.getMessage())
            );
        }

        // 2. Extract provider JSON error message if present
        String providerMessage = extractJsonErrorMessage(fullText);

        // 3. Stale Base Branch
        if (fullText.contains("STALE_BASE") || lowerText.contains("the repository branch has advanced")) {
            return new ClassifiedError(
                    "STALE_BASE",
                    "The repository branch has advanced since the last scan (e.g., a PR was merged). Please run a fresh scan before analyzing further findings.",
                    false,
                    fullText
            );
        }

        // 4. Model Not Found / 404
        if (fullText.contains("ModelNotFound")
                || (lowerText.contains("404") && (lowerText.contains("model") || lowerText.contains("not found") || lowerText.contains("no provider serves") || lowerText.contains("is no longer available")))
                || lowerText.contains("no provider serves this model")
                || lowerText.contains("is no longer available on openrouter")
                || lowerText.contains("model not found")
                || (lowerText.contains("does not exist") && lowerText.contains("model"))
                || lowerText.contains("unknown model")
                || lowerText.contains("model_not_found")) {

            String detail = providerMessage != null ? providerMessage : cleanMessageSnippet(throwable);
            String userMsg = "The configured model is no longer available on your LLM provider (404 Not Found): "
                    + detail + ". Please select a different model in Profile Settings.";
            return new ClassifiedError("MODEL_NOT_FOUND", userMsg, true, fullText);
        }

        // 5. Invalid Credentials / 401 Unauthorized
        if (fullText.contains("AuthenticationException")
                || lowerText.contains("401")
                || lowerText.contains("unauthorized")
                || lowerText.contains("invalid api key")
                || lowerText.contains("incorrect api key")
                || lowerText.contains("invalid_api_key")
                || lowerText.contains("authentication failed")) {

            String detail = providerMessage != null ? providerMessage : "Invalid API key or unauthorized access";
            String userMsg = "Authentication failed (401 Unauthorized): " + detail + ". Please verify your API key in Profile Settings.";
            return new ClassifiedError("CREDENTIALS_INVALID", userMsg, true, fullText);
        }

        // 6. Quota / Credits / 402 Payment Required
        if (lowerText.contains("402")
                || lowerText.contains("payment required")
                || lowerText.contains("insufficient_quota")
                || lowerText.contains("exceeded your current quota")
                || (lowerText.contains("quota") && lowerText.contains("exceeded"))
                || (lowerText.contains("credit") && (lowerText.contains("insufficient") || lowerText.contains("exhausted") || lowerText.contains("run out") || lowerText.contains("out of")))) {

            String detail = providerMessage != null ? providerMessage : "Credits or quota exhausted";
            String userMsg = "LLM provider quota or credit limit exhausted (402 Payment Required): " + detail + ". Please top up your account or switch providers in Profile Settings.";
            return new ClassifiedError("QUOTA_EXCEEDED", userMsg, true, fullText);
        }

        // 7. Rate Limit / 429 Too Many Requests
        if (lowerText.contains("429")
                || lowerText.contains("rate limit")
                || lowerText.contains("too many requests")) {

            String detail = providerMessage != null ? providerMessage : "Rate limit reached";
            String userMsg = "LLM provider rate limit reached (429 Too Many Requests): " + detail + ". Please wait a moment before trying again.";
            return new ClassifiedError("RATE_LIMITED", userMsg, false, fullText);
        }

        // 8. Context Window / 400 Bad Request Context Length
        if (lowerText.contains("context_length_exceeded")
                || lowerText.contains("maximum context length")
                || (lowerText.contains("context length") && lowerText.contains("exceeded"))
                || lowerText.contains("prompt is too long")) {

            String detail = providerMessage != null ? providerMessage : "Prompt exceeds model context window";
            String userMsg = "Context length exceeded (400 Bad Request): " + detail + ". Try analyzing a smaller file or compacting conversation memory.";
            return new ClassifiedError("CONTEXT_LENGTH_EXCEEDED", userMsg, false, fullText);
        }

        // 9. Connection Failure
        if (hasConnectionException || lowerText.contains("connection refused") || (lowerText.contains("timed out") && lowerText.contains("connect"))) {
            return new ClassifiedError(
                    "CONNECTION_FAILED",
                    "Connection to LLM provider failed: unable to connect to the configured endpoint. Please check your network connection and base URL in Profile Settings.",
                    true,
                    fullText
            );
        }

        // 10. General Remediation Failure
        String cleanMsg = providerMessage != null ? providerMessage : cleanMessageSnippet(throwable);
        return new ClassifiedError(
                "REMEDIATION_FAILED",
                "Remediation failed: " + cleanMsg,
                false,
                fullText
        );
    }

    public static String extractJsonErrorMessage(String raw) {
        if (raw == null || !raw.contains("{") || !raw.contains("}")) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String jsonCandidate = raw.substring(start, end + 1);
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonCandidate);
            if (root.has("error")) {
                JsonNode errNode = root.get("error");
                if (errNode.isObject() && errNode.has("message") && !errNode.get("message").asText().isBlank()) {
                    return errNode.get("message").asText();
                } else if (errNode.isTextual() && !errNode.asText().isBlank()) {
                    return errNode.asText();
                }
            } else if (root.has("message") && !root.get("message").asText().isBlank()) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {
            // Not valid JSON
        }
        return null;
    }

    public static String stripExceptionPrefixes(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String result = message.trim();
        while (true) {
            var matcher = EXCEPTION_PREFIX_PATTERN.matcher(result);
            if (matcher.find()) {
                result = result.substring(matcher.end()).trim();
            } else {
                break;
            }
        }
        return result;
    }

    private static String cleanMessageSnippet(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = throwable.getMessage();
        }
        if (msg == null || msg.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        String stripped = stripExceptionPrefixes(msg);
        return stripped.isBlank() ? cause.getClass().getSimpleName() : stripped;
    }
}
