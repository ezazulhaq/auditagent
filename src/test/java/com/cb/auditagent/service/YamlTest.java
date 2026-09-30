package com.cb.auditagent.service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.cb.auditagent.config.GitHubAppConfig;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.profiles.active=local"})
public class YamlTest {
    @Autowired
    private GitHubAppConfig config;

    @Test
    public void test() {
        System.out.println("isConfigured: " + config.isConfigured());
        System.out.println("appId: " + config.getAppId());
    }
}
