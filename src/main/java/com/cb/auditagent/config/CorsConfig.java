package com.cb.auditagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {
    @Bean
    CorsWebFilter corsWebFilter(GitHubAppConfig config) {
        CorsConfiguration cors = new CorsConfiguration();
        String frontendUrl = config.getFrontendUrl();
        if (frontendUrl != null && frontendUrl.endsWith("/")) {
            frontendUrl = frontendUrl.substring(0, frontendUrl.length() - 1);
        }
        String codespaceName = System.getenv("CODESPACE_NAME");
        String codespaceDomain = System.getenv("GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN");
        String codespaceUrl = (codespaceName != null && !codespaceName.isEmpty() && codespaceDomain != null)
                ? "https://" + codespaceName + "-8173." + codespaceDomain
                : "";

        List<String> allowedOrigins = new java.util.ArrayList<>(Arrays.asList(
                frontendUrl,
                "http://localhost:3000",
                "http://localhost:5173",
                "http://localhost:8173",
                "https://localhost:8173"));

        if (!codespaceUrl.isEmpty()) {
            allowedOrigins.add(codespaceUrl);
            allowedOrigins.add(codespaceUrl.replace("8173", "5173"));
        }

        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedOriginPatterns(List.of("https://*.app.github.dev"));
        System.out.println(
                "CorsConfig initialized. AllowedOrigins: " + allowedOrigins + ", Patterns: https://*.app.github.dev");
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return new CorsWebFilter(source);
    }
}
