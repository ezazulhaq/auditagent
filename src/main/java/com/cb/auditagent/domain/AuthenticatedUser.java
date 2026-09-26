package com.cb.auditagent.domain;

public record AuthenticatedUser(String userId, long githubUserId, String login,
        String displayName, String avatarUrl, String csrfToken) implements java.io.Serializable {
}
