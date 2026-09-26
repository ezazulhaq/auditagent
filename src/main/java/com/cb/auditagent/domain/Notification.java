package com.cb.auditagent.domain;

import java.time.Instant;
import java.util.UUID;

public class Notification {
    private String id;
    private String userId;
    private String type;
    private String message;
    private boolean read;
    private Instant createdAt;

    public Notification() {
    }

    public Notification(String userId, String type, String message) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.read = false;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
