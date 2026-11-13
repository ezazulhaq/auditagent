package com.cb.auditagent.service;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.domain.GitHubAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GitHubAuthServiceConcurrencyTest {

    private GitHubAppConfig config;
    private DatabaseService database;
    private TokenCipher cipher;
    private GitHubApiClient api;
    private GitHubAuthService service;

    @BeforeEach
    void setUp() {
        config = mock(GitHubAppConfig.class);
        database = mock(DatabaseService.class);
        cipher = mock(TokenCipher.class);
        api = mock(GitHubApiClient.class);

        when(config.isConfigured()).thenReturn(true);
        when(cipher.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(cipher.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));

        service = new GitHubAuthService(config, database, cipher, api);
    }

    @Test
    void distinctUsersExecuteTokenRefreshConcurrentlyWithoutBlocking() throws Exception {
        String userA = "user-A";
        String userB = "user-B";

        LocalDateTime expired = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        GitHubAuthorization authA = new GitHubAuthorization(userA, "token-a", expired, "refresh-a",
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        GitHubAuthorization authB = new GitHubAuthorization(userB, "token-b", expired, "refresh-b",
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1));

        when(database.getGitHubAuthorization(userA)).thenReturn(Optional.of(authA));
        when(database.getGitHubAuthorization(userB)).thenReturn(Optional.of(authB));

        CountDownLatch userAStarted = new CountDownLatch(1);
        CountDownLatch allowUserAFinish = new CountDownLatch(1);
        CountDownLatch userBFinished = new CountDownLatch(1);

        when(api.refresh("refresh-a")).thenAnswer(inv -> {
            userAStarted.countDown();
            allowUserAFinish.await(2, TimeUnit.SECONDS);
            return new GitHubApiClient.OAuthTokens("new-token-a",
                    LocalDateTime.now(ZoneOffset.UTC).plusHours(1),
                    "refresh-a", LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        });

        when(api.refresh("refresh-b")).thenReturn(new GitHubApiClient.OAuthTokens("new-token-b",
                LocalDateTime.now(ZoneOffset.UTC).plusHours(1),
                "refresh-b", LocalDateTime.now(ZoneOffset.UTC).plusDays(1)));

        // Start User A refresh on virtual thread
        Thread.startVirtualThread(() -> {
            service.accessToken(userA);
        });

        // Ensure User A is actively inside its blocking refresh call
        assertTrue(userAStarted.await(1, TimeUnit.SECONDS), "User A should start refresh");

        // User B must execute and complete unhindered by User A's ongoing refresh
        AtomicBoolean userBCompleted = new AtomicBoolean(false);
        Thread userBThread = Thread.startVirtualThread(() -> {
            String tokenB = service.accessToken(userB);
            if ("new-token-b".equals(tokenB)) {
                userBCompleted.set(true);
            }
            userBFinished.countDown();
        });

        assertTrue(userBFinished.await(1, TimeUnit.SECONDS),
                "User B must not be blocked while User A is refreshing token");
        assertTrue(userBCompleted.get(), "User B should successfully obtain refreshed token");

        // Release User A
        allowUserAFinish.countDown();
    }

    @Test
    void concurrentCallsForSameUserSerializeAndDoubleCheckExpiry() throws Exception {
        String userA = "user-A";
        LocalDateTime expired = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        LocalDateTime fresh = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

        GitHubAuthorization expiredAuth = new GitHubAuthorization(userA, "old-token", expired, "refresh-token",
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        GitHubAuthorization updatedAuth = new GitHubAuthorization(userA, "new-token", fresh, "refresh-token",
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1));

        AtomicInteger getAuthCallCount = new AtomicInteger(0);
        when(database.getGitHubAuthorization(userA)).thenAnswer(inv -> {
            if (getAuthCallCount.incrementAndGet() == 1) {
                return Optional.of(expiredAuth);
            }
            return Optional.of(updatedAuth);
        });

        when(api.refresh("refresh-token")).thenReturn(new GitHubApiClient.OAuthTokens("new-token",
                fresh, "refresh-token", LocalDateTime.now(ZoneOffset.UTC).plusDays(1)));

        int threads = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    startLatch.await();
                    String token = service.accessToken(userA);
                    assertEquals("new-token", token);
                } catch (Exception e) {
                    fail("Unexpected error in thread: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        // api.refresh should only be called once because subsequent threads double-check the newly saved authorization
        verify(api, times(1)).refresh("refresh-token");
    }
}
