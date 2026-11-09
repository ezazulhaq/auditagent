package com.cb.auditagent.service;

import com.cb.auditagent.client.jev.JevClient;
import com.cb.auditagent.client.jev.JevResponse;
import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.config.JevProperties;
import com.cb.auditagent.config.LlmProperties;
import com.cb.auditagent.dto.TestConnectionRequest;
import com.cb.auditagent.dto.TestConnectionResponse;
import com.cb.auditagent.dto.TestJevConnectionRequest;
import com.cb.auditagent.dto.UserLlmConfigDto;
import com.cb.auditagent.dto.UserLlmConfigRequest;
import com.cb.auditagent.entity.UserLlmConfig;
import com.cb.auditagent.repository.UserLlmConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserLlmConfigServiceTest {

    private UserLlmConfigRepository repository;
    private TokenCipher tokenCipher;
    private LlmProperties llmProperties;
    private JevProperties jevProperties;
    private UserLlmClientFactory clientFactory;
    private JevClient jevClient;
    private UserLlmConfigService service;

    @BeforeEach
    void setup() {
        repository = mock(UserLlmConfigRepository.class);
        GitHubAppConfig appConfig = new GitHubAppConfig();
        appConfig.setTokenEncryptionKey("test-encryption-key-for-unit-testing-32-bytes");
        tokenCipher = new TokenCipher(appConfig);
        llmProperties = new LlmProperties();
        llmProperties.setUserKeysRequired(true);
        jevProperties = new JevProperties();
        clientFactory = mock(UserLlmClientFactory.class);
        jevClient = mock(JevClient.class);
        service = new UserLlmConfigService(repository, tokenCipher, llmProperties, jevProperties, clientFactory, jevClient);
    }

    @Test
    void testGetConfigUnconfigured() {
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        UserLlmConfigDto dto = service.getConfig("user-1");
        assertNotNull(dto);
        assertFalse(dto.configured());
        assertTrue(dto.userKeysRequired());
        assertEquals("OPENROUTER", dto.provider());
        assertNull(dto.maskedApiKey());
    }

    @Test
    void testSaveAndGetConfig() {
        String rawKey = "sk-or-v1-abcdef1234567890abcdef";
        String encKey = tokenCipher.encrypt(rawKey);

        UserLlmConfig entity = UserLlmConfig.builder()
                .userId("user-1")
                .provider("OPENROUTER")
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKeyEncrypted(encKey)
                .modelName("anthropic/claude-3.5-sonnet")
                .build();

        when(repository.findById("user-1")).thenReturn(Optional.of(entity));

        UserLlmConfigDto dto = service.getConfig("user-1");
        assertTrue(dto.configured());
        assertEquals("anthropic/claude-3.5-sonnet", dto.modelName());
        assertNotNull(dto.maskedApiKey());
        assertTrue(dto.maskedApiKey().startsWith("sk-or-v1-"));
        assertTrue(dto.maskedApiKey().endsWith("cdef"));
    }

    @Test
    void testSaveConfigEncryptsAndEvictsCache() {
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        UserLlmConfigRequest request = new UserLlmConfigRequest(
                "OPENROUTER", null, "sk-or-v1-secret-key-1234", "openai/gpt-4o", null
        );

        service.saveConfig("user-1", request);

        ArgumentCaptor<UserLlmConfig> captor = ArgumentCaptor.forClass(UserLlmConfig.class);
        verify(repository).save(captor.capture());
        UserLlmConfig saved = captor.getValue();

        assertEquals("user-1", saved.getUserId());
        assertEquals("OPENROUTER", saved.getProvider());
        assertEquals("https://openrouter.ai/api/v1", saved.getBaseUrl());
        assertEquals("openai/gpt-4o", saved.getModelName());
        assertNotEquals("sk-or-v1-secret-key-1234", saved.getApiKeyEncrypted());
        assertEquals("sk-or-v1-secret-key-1234", tokenCipher.decrypt(saved.getApiKeyEncrypted()));

        verify(clientFactory).evict("user-1");
    }

    @Test
    void testSaveConfigPreservesKeyIfOmittedOnUpdate() {
        String existingEncKey = tokenCipher.encrypt("sk-existing-key-1234");
        UserLlmConfig existing = UserLlmConfig.builder()
                .userId("user-1")
                .provider("OPENAI_COMPATIBLE")
                .baseUrl("https://api.openai.com/v1")
                .apiKeyEncrypted(existingEncKey)
                .modelName("gpt-4o")
                .build();

        when(repository.findById("user-1")).thenReturn(Optional.of(existing));

        // Update model only, omit apiKey
        UserLlmConfigRequest request = new UserLlmConfigRequest(
                "OPENAI_COMPATIBLE", "https://api.openai.com/v1", null, "gpt-4o-mini", null
        );

        service.saveConfig("user-1", request);

        verify(repository).save(existing);
        assertEquals("gpt-4o-mini", existing.getModelName());
        assertEquals(existingEncKey, existing.getApiKeyEncrypted());
        verify(clientFactory).evict("user-1");
    }

    @Test
    void testMaskKey() {
        assertEquals("sk-or-v1-••••••••3a8f", UserLlmConfigService.maskKey("sk-or-v1-abcdef1234563a8f"));
        assertEquals("sk-••••••••9999", UserLlmConfigService.maskKey("sk-123456789999"));
        assertEquals("••••••••", UserLlmConfigService.maskKey("123456"));
        assertNull(UserLlmConfigService.maskKey(null));
        assertNull(UserLlmConfigService.maskKey("   "));
    }

    @Test
    void testTestConnectionFailsGracefullyWithoutHttpClientConflict() {
        TestConnectionRequest request = new TestConnectionRequest(
                "OPENROUTER", "https://openrouter.ai/api/v1", "invalid-key", "anthropic/claude-3.5-sonnet"
        );
        TestConnectionResponse response = service.testConfig("user-1", request);
        assertNotNull(response);
        assertFalse(response.success());
        assertFalse(response.message().contains("multiple HTTP clients have been found"));
    }

    @Test
    void testSaveAndGetConfigWithSystemOne() {
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        UserLlmConfigRequest request = new UserLlmConfigRequest(
                "OPENROUTER", "https://openrouter.ai/api/v1", "sk-or-v1-main-key", "anthropic/claude-3.5-sonnet",
                null, true, "typesafe/jev-1.14", "https://openrouter.ai/api/v1", "sk-jev-custom-key-9999"
        );

        service.saveConfig("user-1", request);

        ArgumentCaptor<UserLlmConfig> captor = ArgumentCaptor.forClass(UserLlmConfig.class);
        verify(repository).save(captor.capture());
        UserLlmConfig saved = captor.getValue();

        assertTrue(saved.getJevEnabled());
        assertEquals("typesafe/jev-1.14", saved.getJevModelName());
        assertEquals("https://openrouter.ai/api/v1", saved.getJevBaseUrl());
        assertEquals("sk-jev-custom-key-9999", tokenCipher.decrypt(saved.getJevApiKeyEncrypted()));
    }

    @Test
    void testTestJevConnectionSuccess() throws Exception {
        JevResponse mockResponse = mock(JevResponse.class);
        when(mockResponse.getAnswerValue("ping")).thenReturn("PING");
        when(mockResponse.getAnswerConfidence("ping")).thenReturn(0.99);
        when(jevClient.execute(any(), any(), any(), any())).thenReturn(mockResponse);

        TestJevConnectionRequest request = new TestJevConnectionRequest(
                "https://openrouter.ai/api/v1", "sk-test-jev", "typesafe/jev-1.14"
        );

        TestConnectionResponse response = service.testJevConfig("user-1", request);
        assertNotNull(response);
        assertTrue(response.success());
        assertTrue(response.message().contains("SystemOne connection successful"));
    }
}

