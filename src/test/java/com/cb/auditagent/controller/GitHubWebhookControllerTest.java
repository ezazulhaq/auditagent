package com.cb.auditagent.controller;

import com.cb.auditagent.config.GitHubAppConfig;
import com.cb.auditagent.controller.GitHubWebhookController;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.PullRequestLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubWebhookControllerTest {
        private static final String SECRET = "webhook-test-secret";
        private static final String BODY = """
                        {"action":"closed","repository":{"id":42},"number":7,"pull_request":{"merged":true}}
                        """.trim();

        @Test
        void validatesSignatureAndProcessesDeliveryOnlyOnce() throws Exception {
                GitHubAppConfig config = new GitHubAppConfig();
                config.setWebhookSecret(SECRET);
                DatabaseService database = mock(DatabaseService.class);
                PullRequestLifecycleService remediation = mock(PullRequestLifecycleService.class);
                when(database.recordWebhookDelivery("delivery-1", "pull_request")).thenReturn(true, false);
                GitHubWebhookController controller = new GitHubWebhookController(
                                config, database, remediation, new ObjectMapper());

                assertEquals(200, controller.receive(signature(BODY), "delivery-1", "pull_request", BODY)
                                .getStatusCode().value());
                assertEquals(200, controller.receive(signature(BODY), "delivery-1", "pull_request", BODY)
                                .getStatusCode().value());

                verify(remediation).handlePullRequestEvent(42, 7, true, true);
        }

        @Test
        void rejectsInvalidSignatureBeforeRecordingDelivery() {
                GitHubAppConfig config = new GitHubAppConfig();
                config.setWebhookSecret(SECRET);
                DatabaseService database = mock(DatabaseService.class);
                PullRequestLifecycleService remediation = mock(PullRequestLifecycleService.class);
                GitHubWebhookController controller = new GitHubWebhookController(
                                config, database, remediation, new ObjectMapper());

                assertThrows(SecurityException.class,
                                () -> controller.receive("sha256=invalid", "delivery-2", "pull_request", BODY));
                verify(database, never()).recordWebhookDelivery("delivery-2", "pull_request");
        }

        @Test
        void failedProcessingReleasesDeliveryForGitHubRetry() throws Exception {
                GitHubAppConfig config = new GitHubAppConfig();
                config.setWebhookSecret(SECRET);
                DatabaseService database = mock(DatabaseService.class);
                PullRequestLifecycleService remediation = mock(PullRequestLifecycleService.class);
                when(database.recordWebhookDelivery("delivery-3", "pull_request")).thenReturn(true);
                doThrow(new IllegalStateException("temporary database failure"))
                                .when(remediation).handlePullRequestEvent(42, 7, true, true);
                GitHubWebhookController controller = new GitHubWebhookController(
                                config, database, remediation, new ObjectMapper());

                assertThrows(IllegalStateException.class,
                                () -> controller.receive(signature(BODY), "delivery-3", "pull_request", BODY));
                verify(database).releaseWebhookDelivery("delivery-3");
        }

        private String signature(String body) throws Exception {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        }
}
