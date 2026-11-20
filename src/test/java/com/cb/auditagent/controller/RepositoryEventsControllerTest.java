package com.cb.auditagent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.service.*;
import com.cb.auditagent.service.realtime.EventBroadcastService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class RepositoryEventsControllerTest {

    @Test
    void streamRepositoryEventsReturnsSseStreamFromEventBroadcastService() {
        GitHubAuthService auth = mock(GitHubAuthService.class);
        RepositoryAccessService repositoryAccess = mock(RepositoryAccessService.class);
        EventBroadcastService broadcastService = mock(EventBroadcastService.class);
        ServerWebExchange exchange = mock(ServerWebExchange.class);

        AuthenticatedUser user = new AuthenticatedUser("github:99", 99, "octocat", "Octo", "", "csrf");
        when(auth.requireUser(exchange)).thenReturn(user);

        long repositoryId = 12345L;
        String branch = "main";
        when(broadcastService.listenRepositoryEvents(repositoryId, branch))
                .thenReturn(Flux.just("{\"type\":\"pr_lifecycle\",\"merged\":true}"));

        AuditController controller = new AuditController(
                mock(SkillManagerService.class),
                mock(ChatService.class),
                mock(ScanOrchestratorService.class),
                mock(RemediationWorkflowService.class),
                repositoryAccess,
                auth,
                mock(DatabaseService.class),
                broadcastService
        );

        Flux<String> stream = controller.streamRepositoryEvents(repositoryId, branch, exchange);
        assertNotNull(stream);

        List<String> events = stream.collectList().block();
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("{\"type\":\"pr_lifecycle\",\"merged\":true}", events.get(0));

        verify(repositoryAccess).requireRepositoryAccess(user, repositoryId);
        verify(broadcastService).listenRepositoryEvents(repositoryId, branch);
    }
}
