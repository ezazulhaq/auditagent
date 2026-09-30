package com.cb.auditagent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.Notification;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final DatabaseService databaseService;
    private final GitHubAuthService auth;

    public NotificationController(DatabaseService databaseService, GitHubAuthService auth) {
        this.databaseService = databaseService;
        this.auth = auth;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        return ResponseEntity.ok(databaseService.getNotifications(user.userId()));
    }

    @PostMapping
    public ResponseEntity<Void> createNotification(ServerWebExchange exchange,
            @RequestBody Map<String, String> payload) {
        AuthenticatedUser user = auth.requireUser(exchange);
        String type = payload.getOrDefault("type", "info");
        String message = payload.get("message");
        if (message != null && !message.trim().isEmpty()) {
            Notification notification = new Notification(user.userId(), type, message);
            databaseService.saveNotification(notification);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        databaseService.markNotificationsRead(user.userId());
        return ResponseEntity.ok().build();
    }
}
