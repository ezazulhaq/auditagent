package com.cb.auditagent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.Notification;
import com.cb.auditagent.service.DatabaseService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final DatabaseService databaseService;

    public NotificationController(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(@RequestAttribute("user") AuthenticatedUser user) {
        return ResponseEntity.ok(databaseService.getNotifications(user.userId()));
    }

    @PostMapping
    public ResponseEntity<Void> createNotification(@RequestAttribute("user") AuthenticatedUser user,
            @RequestBody Map<String, String> payload) {
        String type = payload.getOrDefault("type", "info");
        String message = payload.get("message");
        if (message != null && !message.trim().isEmpty()) {
            Notification notification = new Notification(user.userId(), type, message);
            databaseService.saveNotification(notification);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(@RequestAttribute("user") AuthenticatedUser user) {
        databaseService.markNotificationsRead(user.userId());
        return ResponseEntity.ok().build();
    }
}
