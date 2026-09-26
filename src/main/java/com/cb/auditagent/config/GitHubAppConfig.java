package com.cb.auditagent.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auditagent.github")
public class GitHubAppConfig {
    private String appId = "";
    private String clientId = "";
    private String clientSecret = "";
    private String privateKey = "";
    private String webhookSecret = "";
    private String tokenEncryptionKey = "";
    private String callbackUrl = "http://localhost:8173/api/auth/github/callback";
    private String frontendUrl = "http://localhost:5173";
    private String installationUrl = "";
    private String apiUrl = "https://api.github.com";
    private String authorizeUrl = "https://github.com/login/oauth/authorize";
    private String tokenUrl = "https://github.com/login/oauth/access_token";
    private String workspaceRoot = System.getProperty("java.io.tmpdir") + "/auditagent-workspaces";
    private boolean secureCookies;
    @Min(1)
    private int sessionHours = 24;
    private String apiVersion = "2026-03-10";

    public boolean isConfigured() {
        return notBlank(appId) && notBlank(clientId) && notBlank(clientSecret)
                && notBlank(privateKey) && notBlank(webhookSecret) && notBlank(tokenEncryptionKey);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getTokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public void setTokenEncryptionKey(String tokenEncryptionKey) {
        this.tokenEncryptionKey = tokenEncryptionKey;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String getInstallationUrl() {
        return installationUrl;
    }

    public void setInstallationUrl(String installationUrl) {
        this.installationUrl = installationUrl;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public boolean isSecureCookies() {
        return secureCookies;
    }

    public boolean useSecureCookies() {
        return secureCookies || (callbackUrl != null && callbackUrl.startsWith("https://"))
                || (frontendUrl != null && frontendUrl.startsWith("https://"));
    }

    public void setSecureCookies(boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    public int getSessionHours() {
        return sessionHours;
    }

    public void setSessionHours(int sessionHours) {
        this.sessionHours = sessionHours;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
}
