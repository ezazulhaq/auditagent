package com.cb.auditagent.config;

import dev.langchain4j.http.client.okhttp.OkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import okhttp3.OkHttpClient.Builder;

/**
 * Configuration for the LangChain4j ChatModel used by the agentic
 * remediation loop. This is a separate model instance from the Spring AI
 * ChatModel used for the simple chat endpoint.
 */
@Configuration
public class LangChainModelConfig {

        private static final Logger logger = LoggerFactory.getLogger(LangChainModelConfig.class);

        @Value("${auditagent.llm.openai.base-url:https://openrouter.ai/api/v1}")
        private String baseUrl;

        @Value("${auditagent.llm.openai.api-key:}")
        private String apiKey;

        @Value("${auditagent.llm.openai.model-name:anthropic/claude-3.5-sonnet}")
        private String modelName;

        @Value("${auditagent.agent.llm-temperature:0.2}")
        private double temperature;

        @Value("${auditagent.agent.llm-max-output-tokens:4096}")
        private int maxOutputTokens;

        @Bean
        public ChatModel openAiLangChainModel() {
                logger.info("Initializing LangChain4j ChatModel with model={} url={} temperature={} maxTokens={}",
                                modelName, baseUrl, temperature, maxOutputTokens);

                Builder okBuilder = new Builder()
                                .addInterceptor(new GeminiThoughtSignatureInterceptor());
                OkHttpClientBuilder httpClientBuilder = new OkHttpClientBuilder()
                                .okHttpClientBuilder(okBuilder);

                return OpenAiChatModel.builder()
                                .baseUrl(baseUrl)
                                .apiKey(apiKey)
                                .modelName(modelName)
                                .temperature(temperature)
                                .maxTokens(maxOutputTokens)
                                .httpClientBuilder(httpClientBuilder)
                                .build();
        }
}
