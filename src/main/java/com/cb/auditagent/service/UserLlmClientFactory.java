package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.GeminiThoughtSignatureInterceptor;
import com.cb.auditagent.config.LlmProperties;
import dev.langchain4j.http.client.okhttp.OkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import okhttp3.OkHttpClient.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating and caching user-scoped LangChain4j ChatModel and StreamingChatModel
 * instances based on Bring-Your-Own-Key (BYOK) settings.
 * Consolidated strictly on LangChain4j in Phase 3.
 */
@Service
public class UserLlmClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(UserLlmClientFactory.class);

    private final UserLlmConfigService configService;
    private final LlmProperties llmProperties;
    private final AgentConfig agentConfig;

    // Fallbacks (configured via application.yaml / LangChainModelConfig)
    private final ChatModel defaultAgentModel;
    private final StreamingChatModel defaultStreamingChatModel;

    // Single unified LangChain4j model cache
    private final Map<String, ChatModel> agentModelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    public UserLlmClientFactory(
            @Lazy UserLlmConfigService configService,
            LlmProperties llmProperties,
            AgentConfig agentConfig,
            @Lazy ChatModel defaultAgentModel,
            @Autowired(required = false) @Lazy StreamingChatModel defaultStreamingChatModel) {
        this.configService = configService;
        this.llmProperties = llmProperties;
        this.agentConfig = agentConfig;
        this.defaultAgentModel = defaultAgentModel;
        this.defaultStreamingChatModel = defaultStreamingChatModel;
    }

    public void evict(String userId) {
        if (userId == null) return;
        String prefix = userId + ":";
        agentModelCache.keySet().removeIf(k -> k.startsWith(prefix));
        streamingModelCache.keySet().removeIf(k -> k.startsWith(prefix));
        logger.debug("Evicted cached LLM models for user {}", userId);
    }

    public ChatModel getAgentModel(String userId) {
        UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
        if (userConfig == null) {
            return defaultAgentModel;
        }

        String modelName = userConfig.modelName();
        String cacheKey = buildCacheKey(userId, "agent", modelName, userConfig);
        return agentModelCache.computeIfAbsent(cacheKey, k -> buildLangChainModel(userConfig.baseUrl(), userConfig.apiKey(), modelName));
    }

    public ChatModel getChatModel(String userId) {
        return getAgentModel(userId);
    }

    public StreamingChatModel getStreamingChatModel(String userId) {
        UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
        if (userConfig == null) {
            return defaultStreamingChatModel;
        }

        String modelName = userConfig.modelName();
        String cacheKey = buildCacheKey(userId, "streaming", modelName, userConfig);
        return streamingModelCache.computeIfAbsent(cacheKey, k -> buildStreamingLangChainModel(userConfig.baseUrl(), userConfig.apiKey(), modelName));
    }

    public ChatModel getSupervisorModel(String userId) {
        UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
        if (userConfig == null) {
            return defaultAgentModel;
        }

        String supervisorModelName = userConfig.supervisorModelName() != null && !userConfig.supervisorModelName().isBlank()
                ? userConfig.supervisorModelName()
                : userConfig.modelName();

        String cacheKey = buildCacheKey(userId, "supervisor", supervisorModelName, userConfig);
        return agentModelCache.computeIfAbsent(cacheKey, k -> buildLangChainModel(userConfig.baseUrl(), userConfig.apiKey(), supervisorModelName));
    }

    public String getAgentModelName(String userId) {
        UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
        return userConfig != null ? userConfig.modelName() : llmProperties.getOpenai().getModelName();
    }

    public String getSupervisorModelName(String userId) {
        UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
        if (userConfig != null) {
            return userConfig.supervisorModelName() != null && !userConfig.supervisorModelName().isBlank()
                    ? userConfig.supervisorModelName()
                    : userConfig.modelName();
        }
        return llmProperties.getOpenai().getSupervisorModelName();
    }

    public String getChatModelName(String userId) {
        return getAgentModelName(userId);
    }

    public boolean isJevEnabled(String userId) {
        try {
            UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
            if (userConfig != null && userConfig.jevEnabled() != null) {
                return userConfig.jevEnabled();
            }
        } catch (Exception ignored) {}
        return true;
    }

    public String getJevModelName(String userId) {
        try {
            UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
            if (userConfig != null && userConfig.jevModelName() != null && !userConfig.jevModelName().isBlank()) {
                return userConfig.jevModelName();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String getJevBaseUrl(String userId) {
        try {
            UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
            if (userConfig != null && userConfig.jevBaseUrl() != null && !userConfig.jevBaseUrl().isBlank()) {
                return userConfig.jevBaseUrl();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String getJevApiKey(String userId) {
        try {
            UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
            if (userConfig != null) {
                if (userConfig.jevApiKey() != null && !userConfig.jevApiKey().isBlank()) {
                    return userConfig.jevApiKey();
                }
                return userConfig.apiKey();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public UserLlmConfigService.DecryptedLlmConfig resolveConfig(String userId) {
        UserLlmConfigService.DecryptedLlmConfig config = configService != null ? configService.getDecryptedConfig(userId) : null;
        if (config == null || config.apiKey() == null || config.apiKey().isBlank()) {
            if (llmProperties.isUserKeysRequired()) {
                throw new UserLlmConfigurationException("User LLM API key and model must be configured in user profile", "CREDENTIALS_MISSING");
            }
            return null;
        }
        return config;
    }

    private String buildCacheKey(String userId, String type, String modelName, UserLlmConfigService.DecryptedLlmConfig config) {
        String secret = (config.apiKey() != null ? config.apiKey() : "") + (config.baseUrl() != null ? config.baseUrl() : "");
        String hash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            hash = HexFormat.of().formatHex(md.digest(secret.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            hash = String.valueOf(secret.hashCode());
        }
        return userId + ":" + type + ":" + modelName + ":" + hash;
    }

    private ChatModel buildLangChainModel(String baseUrl, String apiKey, String modelName) {
        long timeoutSeconds = agentConfig.getLlmCallTimeoutSeconds();
        int maxRetries = agentConfig.getMaxRetries();

        Builder okBuilder = new Builder()
                .addInterceptor(new GeminiThoughtSignatureInterceptor())
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS);

        OkHttpClientBuilder httpClientBuilder = new OkHttpClientBuilder()
                .okHttpClientBuilder(okBuilder);

        logger.info("Instantiating user LangChain4j ChatModel (model={}, baseUrl={})", modelName, baseUrl);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .httpClientBuilder(httpClientBuilder)
                .build();
    }

    private StreamingChatModel buildStreamingLangChainModel(String baseUrl, String apiKey, String modelName) {
        long timeoutSeconds = agentConfig.getLlmCallTimeoutSeconds();

        Builder okBuilder = new Builder()
                .addInterceptor(new GeminiThoughtSignatureInterceptor())
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS);

        OkHttpClientBuilder httpClientBuilder = new OkHttpClientBuilder()
                .okHttpClientBuilder(okBuilder);

        logger.info("Instantiating user LangChain4j StreamingChatModel (model={}, baseUrl={})", modelName, baseUrl);
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .httpClientBuilder(httpClientBuilder)
                .build();
    }
}
