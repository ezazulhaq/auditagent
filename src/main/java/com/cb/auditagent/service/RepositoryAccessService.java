package com.cb.auditagent.service;

import org.springframework.stereotype.Service;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;

@Service
public class RepositoryAccessService {
    private final DatabaseService database;
    private final SourceControlProvider sourceControl;
    private final GitHubAuthService auth;

    public RepositoryAccessService(DatabaseService database, SourceControlProvider sourceControl,
            GitHubAuthService auth) {
        this.database = database;
        this.sourceControl = sourceControl;
        this.auth = auth;
    }

    public ManagedRepository requireRepositoryAccess(AuthenticatedUser user, long repositoryId) {
        ManagedRepository repository = requireRepository(repositoryId);
        if (!sourceControl.userCanRead(auth.accessToken(user.userId()), repository)) {
            throw new SecurityException("The authenticated user cannot access this repository");
        }
        return repository;
    }

    public ManagedRepository requireRepository(long id) {
        return database.getManagedRepository(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository is not managed"));
    }
}
