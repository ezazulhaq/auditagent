package com.cb.auditagent.service.data;

import com.cb.auditagent.domain.Notification;
import com.cb.auditagent.entity.UserNotification;
import com.cb.auditagent.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserDataServiceTest {

    private AppUserRepository appUserRepository;
    private UserSessionRepository userSessionRepository;
    private GithubAuthorizationRepository githubAuthorizationRepository;
    private UserNotificationRepository userNotificationRepository;
    private OauthStateRepository oauthStateRepository;

    private UserDataService userDataService;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        userSessionRepository = mock(UserSessionRepository.class);
        githubAuthorizationRepository = mock(GithubAuthorizationRepository.class);
        userNotificationRepository = mock(UserNotificationRepository.class);
        oauthStateRepository = mock(OauthStateRepository.class);

        userDataService = new UserDataService(
                appUserRepository,
                userSessionRepository,
                githubAuthorizationRepository,
                userNotificationRepository,
                oauthStateRepository
        );
    }

    @Test
    void saveNotificationPersistsValidNotification() {
        Notification notification = new Notification("user-1", "success", "Scan completed");
        when(userNotificationRepository.existsByUserIdAndTypeAndMessageAndCreatedAtAfter(
                eq("user-1"), eq("success"), eq("Scan completed"), any(LocalDateTime.class)))
                .thenReturn(false);

        userDataService.saveNotification(notification);

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationRepository).save(captor.capture());
        UserNotification saved = captor.getValue();
        assertEquals("user-1", saved.getUserId());
        assertEquals("success", saved.getType());
        assertEquals("Scan completed", saved.getMessage());
        assertFalse(saved.getIsRead());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void saveNotificationIgnoresDuplicateWithinWindow() {
        Notification notification = new Notification("user-1", "error", "Scan failed");
        when(userNotificationRepository.existsByUserIdAndTypeAndMessageAndCreatedAtAfter(
                eq("user-1"), eq("error"), eq("Scan failed"), any(LocalDateTime.class)))
                .thenReturn(true);

        userDataService.saveNotification(notification);

        verify(userNotificationRepository, never()).save(any());
    }

    @Test
    void saveNotificationIgnoresNullOrBlank() {
        userDataService.saveNotification(null);
        userDataService.saveNotification(new Notification(null, "info", "test"));
        userDataService.saveNotification(new Notification("user-1", "info", "   "));

        verify(userNotificationRepository, never()).save(any());
    }

    @Test
    void getNotificationsMapsEntitiesToDomain() {
        UserNotification entity = new UserNotification();
        entity.setId("notif-1");
        entity.setUserId("user-1");
        entity.setType("info");
        entity.setMessage("Hello");
        entity.setIsRead(false);
        entity.setCreatedAt(LocalDateTime.now());

        when(userNotificationRepository.findTop100ByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(entity));

        List<Notification> list = userDataService.getNotifications("user-1");
        assertEquals(1, list.size());
        assertEquals("notif-1", list.get(0).getId());
        assertEquals("user-1", list.get(0).getUserId());
        assertEquals("Hello", list.get(0).getMessage());
    }
}
