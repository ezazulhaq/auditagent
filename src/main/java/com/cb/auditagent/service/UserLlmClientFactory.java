package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.GeminiThoughtSignatureInterceptor;
import com.cb.auditagent.config.LlmProperties;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import dev.langchain4j.http.client.okhttp.OkHttpClientBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import okhttp3.OkHttpClient.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
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

@Service
public class UserLlmClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(UserLlmClientFactory.class);

    private final UserLlmConfigService configService;
    private final LlmProperties llmProperties;
    private final AgentConfig agentConfig;

    // Fallbacks (configured via application.yaml / Spring AI auto-configuration)
    private final dev.langchain4j.model.chat.ChatModel defaultAgentModel;
    private final org.springframework.ai.chat.model.ChatModel defaultSpringChatModel;

    private final Map<String, dev.langchain4j.model.chat.ChatModel> agentModelCache = new ConcurrentHashMap<>();
    private final Map<String, org.springframework.ai.chat.model.ChatModel> springChatModelCache = new ConcurrentHashMap<>();

    @Autowired
    public UserLlmClientFactory(
            @Lazy UserLlmConfigService configService,
            LlmProperties llmProperties,
            AgentConfig agentConfig,
            @Lazy dev.langchain4j.model.chat.ChatModel defaultAgentModel,
            @Autowired(required = false) @Lazy org.springframework.ai.chat.model.ChatModel defaultSpringChatModel) {
        this.configService = configService;
        this.llmProperties = llmProperties;
        this.agentConfig = agentConfig;
        this.defaultAgentModel = defaultAgentModel;
        this.defaultSpringChatModel = defaultSpringChatModel;
    }

    public void evict(String userId) {
        if (userId == null) return;
        String prefix = userId + ":";
        agentModelCache.keySet().removeIf(k -> k.startsWith(prefix));
        springChatModelCache.keySet().removeIf(k -> k.startsWith(prefix));
        logger.debug("Evicted cached LLM models for user {}", userId);
    }

    public dev.langchain4j.model.chat.ChatModel getAgentModel(String userId) {
        UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
        if (userConfig == null) {
            return defaultAgentModel;
        }

        String modelName = userConfig.modelName();
        String cacheKey = buildCacheKey(userId, "agent", modelName, userConfig);
        return agentModelCache.computeIfAbsent(cacheKey, k -> buildLangChainModel(userConfig.baseUrl(), userConfig.apiKey(), modelName));
    }

    public dev.langchain4j.model.chat.ChatModel getSupervisorModel(String userId) {
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

    public org.springframework.ai.chat.model.ChatModel getSpringChatModel(String userId) {
        UserLlmConfigService.DecryptedLlmConfig userConfig = resolveConfig(userId);
        if (userConfig == null) {
            return defaultSpringChatModel;
        }

        String modelName = userConfig.modelName();
        String cacheKey = buildCacheKey(userId, "spring", modelName, userConfig);
        return springChatModelCache.computeIfAbsent(cacheKey, k -> buildSpringChatModel(userConfig.baseUrl(), userConfig.apiKey(), modelName));
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

    private UserLlmConfigService.DecryptedLlmConfig resolveConfig(String userId) {
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

    private dev.langchain4j.model.chat.ChatModel buildLangChainModel(String baseUrl, String apiKey, String modelName) {
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

    private org.springframework.ai.chat.model.ChatModel buildSpringChatModel(String baseUrl, String apiKey, String modelName) {
        long timeoutSeconds = agentConfig.getLlmCallTimeoutSeconds();
        int maxRetries = agentConfig.getMaxRetries();

        logger.info("Instantiating user Spring AI ChatModel (model={}, baseUrl={})", modelName, baseUrl);
        OpenAIClient syncClient = OpenAiSetup.setupSyncClient(
                baseUrl, apiKey, null, null, null, null, false, false, modelName,
                Duration.ofSeconds(timeoutSeconds), maxRetries, null, Map.of(), null, null, null
        );
        OpenAIClientAsync asyncClient = OpenAiSetup.setupAsyncClient(
                baseUrl, apiKey, null, null, null, null, false, false, modelName,
                Duration.ofSeconds(timeoutSeconds), maxRetries, null, Map.of(), null, null, null
        );

        return org.springframework.ai.openai.OpenAiChatModel.builder()
                .openAiClient(syncClient)
                .openAiClientAsync(asyncClient)
                .options(OpenAiChatOptions.builder().model(modelName).temperature(0.2).build())
                .build();
    }
}

