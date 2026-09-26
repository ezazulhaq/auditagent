package com.cb.auditagent.domain;

import java.time.LocalDateTime;

public record GitHubAuthorization(String userId, String accessTokenEncrypted,
        LocalDateTime accessExpiresAt, String refreshTokenEncrypted,
        LocalDateTime refreshExpiresAt) {
}
