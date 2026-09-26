package com.cb.auditagent.service;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.domain.ManagedRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GitHubApiClient {
    private final GitHubAppConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public GitHubApiClient(GitHubAppConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public OAuthTokens exchangeCode(String code, String verifier) {
        String body = form(Map.of("client_id", config.getClientId(), "client_secret", config.getClientSecret(),
                "code", code, "redirect_uri", config.getCallbackUrl(), "code_verifier", verifier));
        return parseOAuth(send("POST", config.getTokenUrl(), null, body, "application/x-www-form-urlencoded"));
    }

    public OAuthTokens refresh(String refreshToken) {
        String body = form(Map.of("client_id", config.getClientId(), "client_secret", config.getClientSecret(),
                "grant_type", "refresh_token", "refresh_token", refreshToken));
        return parseOAuth(send("POST", config.getTokenUrl(), null, body, "application/x-www-form-urlencoded"));
    }

    public UserProfile getUser(String token) {
        JsonNode user = sendJson("GET", api("/user"), token, null);
        return new UserProfile(user.path("id").asLong(), user.path("login").asText(),
                textOrNull(user, "name"), textOrNull(user, "avatar_url"));
    }

    public List<ManagedRepository> listRepositories(String userToken) {
        List<ManagedRepository> repositories = new ArrayList<>();
        JsonNode installations = sendJson("GET", api("/user/installations?per_page=100"), userToken, null)
                .path("installations");
        for (JsonNode installation : installations) {
            long installationId = installation.path("id").asLong();
            JsonNode repos = sendJson("GET", api("/user/installations/" + installationId +
                    "/repositories?per_page=100"), userToken, null).path("repositories");
            for (JsonNode repo : repos) {
                JsonNode permissions = repo.path("permissions");
                String permission = permissions.path("admin").asBoolean() ? "ADMIN"
                        : permissions.path("maintain").asBoolean() ? "MAINTAIN"
                                : permissions.path("push").asBoolean() ? "WRITE" : "READ";
                repositories.add(new ManagedRepository(repo.path("id").asLong(), installationId,
                        repo.path("owner").path("login").asText(), repo.path("name").asText(),
                        repo.path("full_name").asText(), repo.path("clone_url").asText(),
                        repo.path("default_branch").asText(), repo.path("private").asBoolean(), permission));
            }
        }
        return repositories;
    }

    public List<String> listBranches(String userToken, ManagedRepository repository) {
        JsonNode branches = sendJson("GET", api("/repos/" + repository.fullName() + "/branches?per_page=100"),
                userToken, null);
        List<String> names = new ArrayList<>();
        branches.forEach(branch -> names.add(branch.path("name").asText()));
        return names;
    }

    public String branchHead(ManagedRepository repository, String branch, String installationToken) {
        JsonNode response = sendJson("GET", api("/repos/" + repository.fullName() + "/branches/" + encode(branch)),
                installationToken, null);
        return response.path("commit").path("sha").asText();
    }

    public boolean userCanPush(String userToken, ManagedRepository repository) {
        JsonNode response = sendJson("GET", api("/repos/" + repository.fullName()), userToken, null);
        return response.path("permissions").path("push").asBoolean()
                || response.path("permissions").path("maintain").asBoolean()
                || response.path("permissions").path("admin").asBoolean();
    }

    public boolean userCanRead(String userToken, ManagedRepository repository) {
        try {
            sendJson("GET", api("/repos/" + repository.fullName()), userToken, null);
            return true;
        } catch (IllegalStateException denied) {
            return false;
        }
    }

    public String installationToken(long installationId) {
        JsonNode response = sendJson("POST", api("/app/installations/" + installationId + "/access_tokens"),
                appJwt(), "{}");
        String token = response.path("token").asText();
        if (token.isBlank())
            throw new IllegalStateException("GitHub did not issue an installation token");
        return token;
    }

    public boolean installationCanPublish(long installationId) {
        JsonNode permissions = sendJson("GET", api("/app/installations/" + installationId), appJwt(), null)
                .path("permissions");
        return "write".equalsIgnoreCase(permissions.path("contents").asText())
                && "write".equalsIgnoreCase(permissions.path("pull_requests").asText())
                && !permissions.path("metadata").asText().isBlank();
    }

    public PrInfo createPullRequest(ManagedRepository repository, String baseBranch, String branchName,
            String title, String body, String installationToken) {
        JsonNode payload = objectMapper.valueToTree(Map.of("title", title, "body", body, "head", branchName,
                "base", baseBranch, "draft", false, "maintainer_can_modify", true));
        JsonNode response = sendJson("POST", api("/repos/" + repository.fullName() + "/pulls"),
                installationToken, json(payload));
        return new PrInfo(response.path("number").asInt(), response.path("html_url").asText(),
                response.path("head").path("sha").asText(), response.path("state").asText(),
                response.path("merged").asBoolean());
    }

    public PrInfo findPullRequest(ManagedRepository repository, String baseBranch, String branchName,
            String installationToken) {
        String query = "?state=all&head=" + encode(repository.owner() + ":" + branchName) +
                "&base=" + encode(baseBranch) + "&per_page=10";
        JsonNode response = sendJson("GET", api("/repos/" + repository.fullName() + "/pulls" + query),
                installationToken, null);
        if (!response.isArray() || response.isEmpty())
            return null;
        JsonNode pr = response.get(0);
        return new PrInfo(pr.path("number").asInt(), pr.path("html_url").asText(),
                pr.path("head").path("sha").asText(), pr.path("state").asText(), pr.path("merged").asBoolean());
    }

    public PrInfo getPullRequest(ManagedRepository repository, int pullRequestNumber, String installationToken) {
        JsonNode pr = sendJson("GET", api("/repos/" + repository.fullName() + "/pulls/" + pullRequestNumber),
                installationToken, null);
        return new PrInfo(pr.path("number").asInt(), pr.path("html_url").asText(),
                pr.path("head").path("sha").asText(), pr.path("state").asText(), pr.path("merged").asBoolean());
    }

    public String getPullRequestDiff(ManagedRepository repository, int pullRequestNumber, String installationToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(api("/repos/" + repository.fullName() + "/pulls/" + pullRequestNumber)))
                .header("Authorization", "Bearer " + installationToken)
                .header("Accept", "application/vnd.github.v3.diff")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "AuditAgent")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                throw new IllegalStateException("GitHub diff request failed with status " + response.statusCode());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch pull request diff", e);
        }
    }

    private OAuthTokens parseOAuth(JsonNode response) {
        if (response.hasNonNull("error"))
            throw new IllegalStateException("GitHub authorization failed: " +
                    response.path("error_description").asText(response.path("error").asText()));
        Instant now = Instant.now();
        long accessSeconds = response.path("expires_in").asLong(28_800);
        long refreshSeconds = response.path("refresh_token_expires_in").asLong(15_897_600);
        return new OAuthTokens(response.path("access_token").asText(),
                LocalDateTime.ofInstant(now.plusSeconds(accessSeconds), ZoneOffset.UTC),
                textOrNull(response, "refresh_token"),
                LocalDateTime.ofInstant(now.plusSeconds(refreshSeconds), ZoneOffset.UTC));
    }

    private JsonNode sendJson(String method, String url, String token, String body) {
        return send(method, url, token, body, "application/json");
    }

    private JsonNode send(String method, String url, String token, String body, String contentType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "AuditAgent")
                    .header("X-GitHub-Api-Version", config.getApiVersion());
            if (token != null && !token.isBlank())
                builder.header("Authorization", "Bearer " + token);
            if (body == null)
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            else
                builder.header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("GitHub API request failed (" + response.statusCode() + "): " +
                        json.path("message").asText("request rejected"));
            }
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub API request interrupted", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state)
                throw state;
            throw new IllegalStateException("GitHub API request failed", e);
        }
    }

    private String appJwt() {
        try {
            long now = Instant.now().getEpochSecond();
            String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = base64Url(("{\"iat\":" + (now - 60) + ",\"exp\":" + (now + 540) +
                    ",\"iss\":\"" + config.getAppId() + "\"}").getBytes(StandardCharsets.UTF_8));
            String unsigned = header + "." + payload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(readPrivateKey());
            signature.update(unsigned.getBytes(StandardCharsets.UTF_8));
            return unsigned + "." + base64Url(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Could not authenticate GitHub App", e);
        }
    }

    private PrivateKey readPrivateKey() throws Exception {
        String configured = config.getPrivateKey();
        String pem = configured;
        if (!configured.contains("BEGIN")) {
            Path possiblePath = Path.of(configured);
            if (Files.isRegularFile(possiblePath))
                pem = Files.readString(possiblePath);
        }
        pem = pem.replace("\\n", "\n");
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "").replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        if (pkcs1)
            keyBytes = wrapPkcs1(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = { 0x02, 0x01, 0x00 };
        byte[] algorithm = { 0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00 };
        byte[] octet = der((byte) 0x04, pkcs1);
        byte[] content = new byte[version.length + algorithm.length + octet.length];
        System.arraycopy(version, 0, content, 0, version.length);
        System.arraycopy(algorithm, 0, content, version.length, algorithm.length);
        System.arraycopy(octet, 0, content, version.length + algorithm.length, octet.length);
        return der((byte) 0x30, content);
    }

    private byte[] der(byte tag, byte[] content) {
        int length = content.length;
        int lengthBytes = length < 128 ? 1 : length < 256 ? 2 : length < 65_536 ? 3 : 4;
        byte[] result = new byte[1 + lengthBytes + length];
        result[0] = tag;
        if (length < 128)
            result[1] = (byte) length;
        else {
            int count = lengthBytes - 1;
            result[1] = (byte) (0x80 | count);
            for (int i = count; i > 0; i--)
                result[1 + i] = (byte) (length >>> (8 * (count - i)));
        }
        System.arraycopy(content, 0, result, 1 + lengthBytes, length);
        return result;
    }

    private String api(String path) {
        return config.getApiUrl().replaceAll("/$", "") + path;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encode GitHub request", e);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank() ? node.path(field).asText() : null;
    }

    public record OAuthTokens(String accessToken, LocalDateTime accessExpiresAt,
            String refreshToken, LocalDateTime refreshExpiresAt) {
    }

    public record UserProfile(long id, String login, String name, String avatarUrl) {
    }

    public record PrInfo(int number, String url, String headSha, String state, boolean merged) {
    }
}
