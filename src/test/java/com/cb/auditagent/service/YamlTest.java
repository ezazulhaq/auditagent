package com.cb.auditagent.service;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.cb.auditagent.config.GitHubAppConfig;

@Disabled("Manual test requiring local PostgreSQL server")
@SpringBootTest(properties = {"spring.profiles.active=local", "spring.ai.openai.api-key=dummy"})
public class YamlTest {
    @Autowired
    private GitHubAppConfig config;

    @Test
    public void test() {
        System.out.println("isConfigured: " + config.isConfigured());
        System.out.println("appId: " + config.getAppId());
    }
}
