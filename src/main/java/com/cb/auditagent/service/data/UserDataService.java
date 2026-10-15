package com.cb.auditagent.service.data;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.GitHubAuthorization;
import com.cb.auditagent.domain.Notification;
import com.cb.auditagent.entity.AppUser;
import com.cb.auditagent.entity.OauthState;
import com.cb.auditagent.entity.UserNotification;
import com.cb.auditagent.entity.UserSession;
import com.cb.auditagent.repository.AppUserRepository;
import com.cb.auditagent.repository.GithubAuthorizationRepository;
import com.cb.auditagent.repository.OauthStateRepository;
import com.cb.auditagent.repository.UserNotificationRepository;
import com.cb.auditagent.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Domain data service for User and Authentication aggregates (AppUser, UserSession, GithubAuthorization, UserNotification, OauthState).
 */
@Service
public class UserDataService {

    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final GithubAuthorizationRepository githubAuthorizationRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final OauthStateRepository oauthStateRepository;

    public UserDataService(AppUserRepository appUserRepository,
                           UserSessionRepository userSessionRepository,
                           GithubAuthorizationRepository githubAuthorizationRepository,
                           UserNotificationRepository userNotificationRepository,
                           OauthStateRepository oauthStateRepository) {
        this.appUserRepository = appUserRepository;
        this.userSessionRepository = userSessionRepository;
        this.githubAuthorizationRepository = githubAuthorizationRepository;
        this.userNotificationRepository = userNotificationRepository;
        this.oauthStateRepository = oauthStateRepository;
    }

    private GitHubAuthorization mapToGitHubAuthorization(com.cb.auditagent.entity.GithubAuthorization e) {
        if (e == null)
            return null;
        return new GitHubAuthorization(e.getUserId(), e.getAccessTokenEncrypted(), e.getAccessExpiresAt(),
                e.getRefreshTokenEncrypted(), e.getRefreshExpiresAt());
    }

    private Notification mapToNotification(UserNotification e) {
        if (e == null)
            return null;
        Notification d = new Notification();
        d.setId(e.getId());
        d.setUserId(e.getUserId());
        d.setType(e.getType());
        d.setMessage(e.getMessage());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toInstant(ZoneOffset.UTC) : null);
        return d;
    }

    @Transactional
    public String upsertGitHubUser(long githubUserId, String login, String displayName, String avatarUrl) {
        Optional<AppUser> existingOpt = appUserRepository.findByGithubUserId(githubUserId);
        if (existingOpt.isPresent()) {
            AppUser existing = existingOpt.get();
            existing.setLogin(login);
            existing.setDisplayName(displayName);
            existing.setAvatarUrl(avatarUrl);
            existing.setUpdatedAt(LocalDateTime.now());
            appUserRepository.save(existing);
            return existing.getUserId();
        } else {
            AppUser u = new AppUser();
            String newId = UUID.randomUUID().toString();
            u.setUserId(newId);
            u.setGithubUserId(githubUserId);
            u.setLogin(login);
            u.setDisplayName(displayName);
            u.setAvatarUrl(avatarUrl);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            appUserRepository.save(u);
            return newId;
        }
    }

    @Transactional
    public void saveGitHubAuthorization(GitHubAuthorization authorization) {
        com.cb.auditagent.entity.GithubAuthorization auth = githubAuthorizationRepository
                .findById(authorization.userId()).orElse(new com.cb.auditagent.entity.GithubAuthorization());
        auth.setUserId(authorization.userId());
        auth.setAccessTokenEncrypted(authorization.accessTokenEncrypted());
        auth.setAccessExpiresAt(authorization.accessExpiresAt());
        auth.setRefreshTokenEncrypted(authorization.refreshTokenEncrypted());
        auth.setRefreshExpiresAt(authorization.refreshExpiresAt());
        auth.setUpdatedAt(LocalDateTime.now());
        githubAuthorizationRepository.save(auth);
    }

    public Optional<GitHubAuthorization> getGitHubAuthorization(String userId) {
        return githubAuthorizationRepository.findById(userId).map(this::mapToGitHubAuthorization);
    }

    @Transactional
    public void saveOAuthState(String stateHash, String verifierEncrypted, LocalDateTime expiresAt) {
        OauthState s = new OauthState();
        s.setStateHash(stateHash);
        s.setVerifierEncrypted(verifierEncrypted);
        s.setExpiresAt(expiresAt);
        s.setCreatedAt(LocalDateTime.now());
        oauthStateRepository.save(s);
    }

    public Optional<String> consumeOAuthState(String stateHash) {
        Optional<OauthState> s = oauthStateRepository
                .findByStateHashAndExpiresAtAfter(stateHash, LocalDateTime.now(ZoneOffset.UTC));
        if (s.isPresent()) {
            oauthStateRepository.delete(s.get());
            return Optional.ofNullable(s.get().getVerifierEncrypted());
        }
        return Optional.empty();
    }

    @Transactional
    public void createUserSession(String sessionHash, String userId, String csrfToken, LocalDateTime expiresAt) {
        UserSession s = new UserSession();
        s.setSessionHash(sessionHash);
        s.setUserId(userId);
        s.setCsrfToken(csrfToken);
        s.setExpiresAt(expiresAt);
        s.setLastSeenAt(LocalDateTime.now());
        s.setCreatedAt(LocalDateTime.now());
        userSessionRepository.save(s);
    }

    public Optional<AuthenticatedUser> findAuthenticatedUser(String sessionHash) {
        return userSessionRepository
                .findBySessionHashAndExpiresAtAfter(sessionHash, LocalDateTime.now(ZoneOffset.UTC))
                .flatMap(s -> appUserRepository.findById(s.getUserId()).map(u -> new AuthenticatedUser(
                        u.getUserId(), u.getGithubUserId(), u.getLogin(), u.getDisplayName(),
                        u.getAvatarUrl(), s.getCsrfToken())));
    }

    @Transactional
    public void deleteUserSession(String sessionHash) {
        userSessionRepository.deleteById(sessionHash);
    }

    @Transactional
    public void saveNotification(Notification notification) {
        UserNotification u = new UserNotification();
        u.setId(notification.getId());
        u.setUserId(notification.getUserId());
        u.setType(notification.getType());
        u.setMessage(notification.getMessage());
        u.setIsRead(false);
        u.setCreatedAt(LocalDateTime.now());
        userNotificationRepository.save(u);
    }

    public List<Notification> getNotifications(String userId) {
        return userNotificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToNotification).collect(Collectors.toList());
    }

    @Transactional
    public void markNotificationsRead(String userId) {
        userNotificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void deleteExpiredMemory(LocalDateTime now) {
        oauthStateRepository.deleteExpiredStates(now);
        userSessionRepository.deleteExpiredSessions(now);
    }
}

