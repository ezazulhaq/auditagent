package com.cb.auditagent.service;

import com.cb.auditagent.domain.Vulnerability;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * Interface for AI conversational assistance, vulnerability triage, and command dispatching.
 * Follows Interface Segregation Principle (ISP) and Dependency Inversion Principle (DIP).
 */
public interface ChatService {

    String chat(String userMessage, List<Vulnerability> findings);

    String chat(String threadId, String repoPath, String userMessage, List<Vulnerability> findings);

    Flux<String> chatStream(String threadId, String repoPath, String userMessage, List<Vulnerability> findings);

    Flux<String> chatStream(String userId, String threadId, String repoPath, String userMessage, List<Vulnerability> findings);

    Optional<String> matchFindingForIssue(String issueTitle, String issueBody, List<Vulnerability> findings);
}
