package com.cb.auditagent.controller;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.Notification;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationControllerTest {
    private DatabaseService databaseService;
    private GitHubAuthService authService;
    private NotificationController controller;
    private AuthenticatedUser sampleUser;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        databaseService = mock(DatabaseService.class);
        authService = mock(GitHubAuthService.class);
        controller = new NotificationController(databaseService, authService);

        sampleUser = new AuthenticatedUser("user-1", 123L, "octocat", "Octocat", null, "octocat");
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/notifications").build());
    }

    @Test
    void getNotificationsReturnsList() {
        when(authService.requireUser(exchange)).thenReturn(sampleUser);
        Notification notif = new Notification(sampleUser.userId(), "info", "Scan finished");
        when(databaseService.getNotifications(sampleUser.userId())).thenReturn(List.of(notif));

        ResponseEntity<List<Notification>> response = controller.getNotifications(exchange);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Notification> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.size());
        assertEquals("Scan finished", body.get(0).getMessage());
    }

    @Test
    void createNotificationSaves() {
        when(authService.requireUser(exchange)).thenReturn(sampleUser);
        Map<String, String> payload = Map.of("type", "success", "message", "Remediation verified");

        ResponseEntity<Void> response = controller.createNotification(exchange, payload);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(databaseService, times(1)).saveNotification(any(Notification.class));
    }

    @Test
    void markReadCallsDatabaseService() {
        when(authService.requireUser(exchange)).thenReturn(sampleUser);

        ResponseEntity<Void> response = controller.markRead(exchange);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(databaseService, times(1)).markNotificationsRead(sampleUser.userId());
    }

    @Test
    void markNotificationReadCallsDatabaseService() {
        when(authService.requireUser(exchange)).thenReturn(sampleUser);

        ResponseEntity<Void> response = controller.markNotificationRead(exchange, "notif-99");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(databaseService, times(1)).markNotificationRead("notif-99", sampleUser.userId());
    }

    @Test
    void clearNotificationsCallsDatabaseService() {
        when(authService.requireUser(exchange)).thenReturn(sampleUser);

        ResponseEntity<Void> response = controller.clearNotifications(exchange);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(databaseService, times(1)).clearNotifications(sampleUser.userId());
    }
}
