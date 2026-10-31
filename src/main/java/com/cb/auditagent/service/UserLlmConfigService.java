package com.cb.auditagent.service;

import com.cb.auditagent.config.LlmProperties;
import com.cb.auditagent.dto.TestConnectionRequest;
import com.cb.auditagent.dto.TestConnectionResponse;
import com.cb.auditagent.dto.UserLlmConfigDto;
import com.cb.auditagent.dto.UserLlmConfigRequest;
import com.cb.auditagent.entity.UserLlmConfig;
import com.cb.auditagent.repository.UserLlmConfigRepository;
import dev.langchain4j.http.client.okhttp.OkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import okhttp3.OkHttpClient.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class UserLlmConfigService {

    private static final Logger logger = LoggerFactory.getLogger(UserLlmConfigService.class);
    public static final String DEFAULT_OPENROUTER_URL = "https://openrouter.ai/api/v1";

    private final UserLlmConfigRepository repository;
    private final TokenCipher tokenCipher;
    private final LlmProperties llmProperties;
    private final UserLlmClientFactory clientFactory;

    public record DecryptedLlmConfig(
            String provider,
            String baseUrl,
            String apiKey,
            String modelName,
            String supervisorModelName
    ) {}

    public UserLlmConfigService(
            UserLlmConfigRepository repository,
            TokenCipher tokenCipher,
            LlmProperties llmProperties,
            @Lazy UserLlmClientFactory clientFactory) {
        this.repository = repository;
        this.tokenCipher = tokenCipher;
        this.llmProperties = llmProperties;
        this.clientFactory = clientFactory;
    }

    public boolean isConfigured(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return repository.existsById(userId);
    }

    public UserLlmConfigDto getConfig(String userId) {
        boolean userKeysRequired = llmProperties.isUserKeysRequired();
        if (userId == null || userId.isBlank()) {
            return new UserLlmConfigDto(false, userKeysRequired, "OPENROUTER", DEFAULT_OPENROUTER_URL,
                    llmProperties.getOpenai().getModelName(), llmProperties.getOpenai().getSupervisorModelName(), null);
        }

        Optional<UserLlmConfig> opt = repository.findById(userId);
        if (opt.isEmpty()) {
            return new UserLlmConfigDto(false, userKeysRequired, "OPENROUTER", DEFAULT_OPENROUTER_URL,
                    llmProperties.getOpenai().getModelName(), llmProperties.getOpenai().getSupervisorModelName(), null);
        }

        UserLlmConfig entity = opt.get();
        String maskedKey = null;
        try {
            String raw = tokenCipher.decrypt(entity.getApiKeyEncrypted());
            maskedKey = maskKey(raw);
        } catch (Exception e) {
            logger.warn("Could not decrypt API key for display masking for user {}: {}", userId, e.getMessage());
            maskedKey = "••••••••";
        }

        return new UserLlmConfigDto(
                true,
                userKeysRequired,
                entity.getProvider(),
                entity.getBaseUrl(),
                entity.getModelName(),
                entity.getSupervisorModelName(),
                maskedKey
        );
    }

    @Transactional
    public UserLlmConfigDto saveConfig(String userId, UserLlmConfigRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String provider = request.provider() != null && !request.provider().isBlank()
                ? request.provider().trim().toUpperCase()
                : "OPENROUTER";

        String baseUrl = request.baseUrl();
        if ("OPENROUTER".equals(provider) && (baseUrl == null || baseUrl.isBlank())) {
            baseUrl = DEFAULT_OPENROUTER_URL;
        } else if (baseUrl != null) {
            baseUrl = baseUrl.trim();
        }

        String modelName = request.modelName() != null && !request.modelName().isBlank()
                ? request.modelName().trim()
                : llmProperties.getOpenai().getModelName();

        String supervisorModelName = request.supervisorModelName() != null && !request.supervisorModelName().isBlank()
                ? request.supervisorModelName().trim()
                : null;

        Optional<UserLlmConfig> existingOpt = repository.findById(userId);
        String encryptedKey;

        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            encryptedKey = tokenCipher.encrypt(request.apiKey().trim());
        } else if (existingOpt.isPresent()) {
            encryptedKey = existingOpt.get().getApiKeyEncrypted();
        } else {
            throw new IllegalArgumentException("API Key is required");
        }

        LocalDateTime now = LocalDateTime.now();
        UserLlmConfig entity = existingOpt.orElseGet(() -> UserLlmConfig.builder()
                .userId(userId)
                .createdAt(now)
                .build());

        entity.setProvider(provider);
        entity.setBaseUrl(baseUrl);
        entity.setApiKeyEncrypted(encryptedKey);
        entity.setModelName(modelName);
        entity.setSupervisorModelName(supervisorModelName);
        entity.setUpdatedAt(now);

        repository.save(entity);

        // Evict cached ChatModel clients for this user
        clientFactory.evict(userId);

        logger.info("Saved user LLM configuration for user {} (provider={}, model={})", userId, provider, modelName);
        return getConfig(userId);
    }

    public DecryptedLlmConfig getDecryptedConfig(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        Optional<UserLlmConfig> opt = repository.findById(userId);
        if (opt.isEmpty()) {
            return null;
        }
        UserLlmConfig entity = opt.get();
        String apiKey = tokenCipher.decrypt(entity.getApiKeyEncrypted());
        return new DecryptedLlmConfig(
                entity.getProvider(),
                entity.getBaseUrl(),
                apiKey,
                entity.getModelName(),
                entity.getSupervisorModelName()
        );
    }

    public TestConnectionResponse testConfig(String userId, TestConnectionRequest request) {
        if (request == null) {
            return TestConnectionResponse.fail("Request body is required", "INVALID_REQUEST");
        }

        String provider = request.provider() != null && !request.provider().isBlank()
                ? request.provider().trim().toUpperCase()
                : "OPENROUTER";

        String baseUrl = request.baseUrl();
        if ("OPENROUTER".equals(provider) && (baseUrl == null || baseUrl.isBlank())) {
            baseUrl = DEFAULT_OPENROUTER_URL;
        } else if (baseUrl != null) {
            baseUrl = baseUrl.trim();
        }

        String apiKey = request.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            // Try to use existing saved key if user is logged in
            if (userId != null) {
                DecryptedLlmConfig existing = getDecryptedConfig(userId);
                if (existing != null) {
                    apiKey = existing.apiKey();
                }
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            return TestConnectionResponse.fail("API Key is required to test connection", "CREDENTIALS_MISSING");
        }

        String modelName = request.modelName() != null && !request.modelName().isBlank()
                ? request.modelName().trim()
                : "z-ai/glm-5.3-flash";

        try {
            logger.info("Testing connection to {} with model {}", baseUrl, modelName);
            Builder okBuilder = new Builder()
                    .readTimeout(15, TimeUnit.SECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(15, TimeUnit.SECONDS);

            OkHttpClientBuilder httpClientBuilder = new OkHttpClientBuilder()
                    .okHttpClientBuilder(okBuilder);

            ChatModel testModel = OpenAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .maxTokens(5)
                    .timeout(Duration.ofSeconds(15))
                    .maxRetries(1)
                    .httpClientBuilder(httpClientBuilder)
                    .build();

            testModel.chat("ping");
            return TestConnectionResponse.ok("Connection successful! Model " + modelName + " responded.");
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            logger.warn("Test connection failed for {}: {}", modelName, message);
            if (message.contains("401") || message.toLowerCase().contains("unauthorized") || message.toLowerCase().contains("invalid api key")) {
                return TestConnectionResponse.fail("Authentication failed: Invalid API Key.", "CREDENTIALS_INVALID");
            }
            if (message.contains("402") || message.toLowerCase().contains("credit") || message.toLowerCase().contains("quota")) {
                return TestConnectionResponse.fail("Insufficient credits or quota exceeded on provider account.", "QUOTA_EXCEEDED");
            }
            if (message.contains("404") || message.toLowerCase().contains("not found")) {
                return TestConnectionResponse.fail("Model not found: " + modelName, "MODEL_NOT_FOUND");
            }
            return TestConnectionResponse.fail("Connection test failed: " + message, "CONNECTION_FAILED");
        }
    }

    public static String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        int len = key.length();
        if (len <= 8) {
            return "••••••••";
        }
        String prefix = key.startsWith("sk-or-v1-") ? "sk-or-v1-" : (key.startsWith("sk-") ? "sk-" : "");
        String suffix = key.substring(len - 4);
        return prefix + "••••••••" + suffix;
    }
}

