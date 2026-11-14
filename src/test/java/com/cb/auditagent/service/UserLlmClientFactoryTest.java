package com.cb.auditagent.service;

import com.cb.auditagent.config.AgentConfig;
import com.cb.auditagent.config.LlmProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserLlmClientFactoryTest {

    private UserLlmConfigService configService;
    private LlmProperties llmProperties;
    private AgentConfig agentConfig;
    private ChatModel defaultAgentModel;
    private StreamingChatModel defaultStreamingChatModel;
    private UserLlmClientFactory factory;

    @BeforeEach
    void setup() {
        configService = mock(UserLlmConfigService.class);
        llmProperties = new LlmProperties();
        llmProperties.setUserKeysRequired(true);
        agentConfig = new AgentConfig();
        defaultAgentModel = mock(ChatModel.class);
        defaultStreamingChatModel = mock(StreamingChatModel.class);

        factory = new UserLlmClientFactory(
                configService, llmProperties, agentConfig, defaultAgentModel, defaultStreamingChatModel
        );
    }

    @Test
    void testThrowsWhenUserKeysRequiredAndNoConfig() {
        llmProperties.setUserKeysRequired(true);
        when(configService.getDecryptedConfig("user-1")).thenReturn(null);

        UserLlmConfigurationException ex = assertThrows(
                UserLlmConfigurationException.class,
                () -> factory.getAgentModel("user-1")
        );
        assertEquals("CREDENTIALS_MISSING", ex.getErrorCode());
    }

    @Test
    void testFallsBackToDefaultWhenUserKeysNotRequired() {
        llmProperties.setUserKeysRequired(false);
        when(configService.getDecryptedConfig("user-1")).thenReturn(null);

        ChatModel model = factory.getAgentModel("user-1");
        assertSame(defaultAgentModel, model);

        ChatModel chatModel = factory.getChatModel("user-1");
        assertSame(defaultAgentModel, chatModel);

        StreamingChatModel streamingModel = factory.getStreamingChatModel("user-1");
        assertSame(defaultStreamingChatModel, streamingModel);
    }

    @Test
    void testBuildsAndCachesModelForUser() {
        UserLlmConfigService.DecryptedLlmConfig userConfig = new UserLlmConfigService.DecryptedLlmConfig(
                "OPENROUTER", "https://openrouter.ai/api/v1", "sk-or-v1-test", "anthropic/claude-3.5-sonnet", null
        );
        when(configService.getDecryptedConfig("user-1")).thenReturn(userConfig);

        ChatModel model1 = factory.getAgentModel("user-1");
        assertNotNull(model1);

        ChatModel model2 = factory.getAgentModel("user-1");
        assertSame(model1, model2); // Cached

        // After eviction, a new model instance is created
        factory.evict("user-1");
        ChatModel model3 = factory.getAgentModel("user-1");
        assertNotNull(model3);
        assertNotSame(model1, model3);
    }

    @Test
    void testJevUserSettings() {
        UserLlmConfigService.DecryptedLlmConfig userConfig = new UserLlmConfigService.DecryptedLlmConfig(
                "OPENROUTER", "https://openrouter.ai/api/v1", "sk-primary-key", "anthropic/claude-3.5-sonnet",
                null, true, "typesafe/jev-1.14", "https://custom.jev.ai/api/v1", "sk-custom-jev-key"
        );
        when(configService.getDecryptedConfig("user-1")).thenReturn(userConfig);

        assertTrue(factory.isJevEnabled("user-1"));
        assertEquals("typesafe/jev-1.14", factory.getJevModelName("user-1"));
        assertEquals("https://custom.jev.ai/api/v1", factory.getJevBaseUrl("user-1"));
        assertEquals("sk-custom-jev-key", factory.getJevApiKey("user-1"));
    }
}
