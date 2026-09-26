package com.cb.auditagent.graph.workflow;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ManagedRepository;
import com.cb.auditagent.service.DatabaseService;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.SourceControlProvider;

import java.util.HashMap;
import java.util.Map;

@Component
public class ValidateAccessNode implements NodeAction<WorkflowState> {

    private final DatabaseService database;
    private final GitHubAuthService auth;
    private final SourceControlProvider sourceControl;

    public ValidateAccessNode(DatabaseService database, GitHubAuthService auth, SourceControlProvider sourceControl) {
        this.database = database;
        this.auth = auth;
        this.sourceControl = sourceControl;
    }

    @Override
    public Map<String, Object> apply(WorkflowState state) throws Exception {
        AuthenticatedUser user = state.getAuthenticatedUser();
        long repositoryId = state.getRepositoryId();

        ManagedRepository repository = database.getManagedRepository(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        if (!sourceControl.userCanRead(auth.accessToken(user.userId()), repository)) {
            throw new SecurityException("The authenticated user cannot access this repository");
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("workflowMessage", "Access validated for repository " + repository.name());
        return updates;
    }
}
