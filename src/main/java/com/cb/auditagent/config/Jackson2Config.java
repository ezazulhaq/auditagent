package com.cb.auditagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AuditAgent and the current Spring AI release still use Jackson 2 types,
 * while Spring Boot 4 auto-configures Jackson 3 by default.
 */
@Configuration
public class Jackson2Config {
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
