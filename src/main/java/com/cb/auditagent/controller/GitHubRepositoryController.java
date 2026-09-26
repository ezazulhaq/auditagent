package com.cb.auditagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.SourceControlProvider;

import java.util.List;

@RestController
@RequestMapping("/api/github/repositories")
public class GitHubRepositoryController {
    private final GitHubAuthService auth;
    private final SourceControlProvider sourceControl;
    private final DatabaseService database;

    public GitHubRepositoryController(GitHubAuthService auth, SourceControlProvider sourceControl,
            DatabaseService database) {
        this.auth = auth;
        this.sourceControl = sourceControl;
        this.database = database;
    }

    @GetMapping
    public List<ManagedRepository> repositories(ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        List<ManagedRepository> repositories = sourceControl.listRepositories(auth.accessToken(user.userId()));
        repositories.forEach(database::upsertManagedRepository);
        return repositories;
    }

    @GetMapping("/{repositoryId}/branches")
    public List<String> branches(@PathVariable long repositoryId, ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        ManagedRepository repository = database.getManagedRepository(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository must be selected again"));
        String userToken = auth.accessToken(user.userId());
        if (!sourceControl.userCanRead(userToken, repository)) {
            throw new SecurityException("The authenticated user cannot access this repository");
        }
        return sourceControl.listBranches(userToken, repository);
    }
}
