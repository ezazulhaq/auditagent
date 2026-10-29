# Source inventory and glossary

## Backend production files

### Root

| File | Purpose |
|---|---|
| `AuditagentApplication.java` | Spring Boot entry point. |

### `aspect`

| File | Purpose |
|---|---|
| `AopLoggingConfig.java` | Applies class-based logging proxy to controllers/services. |
| `CustomLoggingInterceptor.java` | Logs method lifecycle without argument values. |
| `LoggingAspect.java` | Empty placeholder; no active behavior. |

### `config`

| File | Purpose |
|---|---|
| `AgentConfig.java` | Validated remediation/tool limits and Docker sandbox settings. |
| `ApiSecurityFilter.java` | API session and CSRF enforcement. |
| `CorsConfig.java` | Exact frontend-origin credentialed CORS. |
| `DatabasePreflightCleanup.java` | Startup pre-flight bean purging legacy orphaned child records before Hibernate updates schema. |
| `GitHubAppConfig.java` | GitHub endpoints, secrets, cookies, workspace settings. |
| `Jackson2Config.java` | Jackson compatibility bean. |
| `LangChainModelConfig.java` | Tool-calling OpenAI model. |
| `MemoryConfig.java` | Retention, context, retrieval, content limits, and context summarization settings. |
| `ScannerConfig.java` | Validated Semgrep timeout and heartbeat settings. |
| `WebFluxConfig.java` | WebFlux multipart and codec in-memory size configuration. |

### `controller`

| File | Purpose |
|---|---|
| `ApiExceptionHandler.java` | Safe 400/403/409 JSON errors. |
| `AuditController.java` | Scan, analyze, report, skill, and chat APIs. |
| `AuthController.java` | Session, OAuth start/callback, logout. |
| `GitHubRepositoryController.java` | Installed repositories, branches, and tracked GitHub issues. |
| `GitHubWebhookController.java` | Signed/replay-protected PR, issue, and issue comment webhook. |
| `ObservabilityController.java` | Aggregated user-level observability metrics (e.g. token usage). |
| `MemoryController.java` | Thread restore/forget and run resume/discard. |
| `NotificationController.java` | User notification retrieval, creation, and mark-read endpoints. |
| `ProjectUploadController.java` | Multipart upload scan, upload report restore, and export APIs. |
| `RunPublicationController.java` | Preview, structured decision, publication retry. |

### `dto`

| File | Fields/use |
|---|---|
| `AnalyzeRequest.java` | repository ID, branch, finding ID, thread ID. |
| `BatchAnalyzeRequest.java` | repository ID, branch, thread ID, finding ID list (`vulnIds`). |
| `ChatRequest.java` | repository ID, branch, message, thread ID. |
| `ChatResponse.java` | assistant response wrapper. |
| `MemoryThreadRequest.java` | optional repository ID and branch. |
| `RunDecisionRequest.java` | finding ID, publication digest. |
| `ScanRequest.java` | repository ID, branch, scanner, force flag, thread ID. |

### `domain`

| File | Purpose |
|---|---|
| `AgentPhase.java` | Fine-grained run checkpoint enum. |
| `AgentRunRecord.java` | Durable remediation record. |
| `AgentRunStatus.java` | Broad run lifecycle/recovery enum. |
| `ApprovalPreview.java` | Bound review payload. |
| `AuthenticatedUser.java` | Server user and CSRF identity. |
| `GitHubAuthorization.java` | Encrypted OAuth metadata. |
| `ManagedRepository.java` | Installed GitHub repository identity/permission. |
| `MemoryMessageRecord.java` | Durable ordered message. |
| `Notification.java` | User workflow and security notification domain model. |
| `PublicationCheckpoint.java` | Commit/push/PR callback checkpoint. |
| `PublishResult.java` | Successful publication result. |
| `PullRequestState.java` | GitHub PR state for reconciliation. |
| `Report.java` | Markdown/HTML/findings/metadata aggregate. |
| `RepositoryMemory.java` | Approved retrieval record plus score. |
| `RunChangeRecord.java` | Changed-file hashes and backup. |
| `RunPublicationRecord.java` | Durable publication/Git/PR state. |
| `ScanMetadata.java` | Scan name/time/count data. |
| `ScanSnapshot.java` | Repository/branch/base/report binding. |
| `Severity.java` | HIGH, MEDIUM, LOW, INFO. |
| `ReportArtifact.java` | In-memory PDF or Markdown bytes with response content type and safe filename. |
| `Skill.java` | Loaded skill metadata/instructions/path. |
| `TrackedIssueRecord.java` | Durable tracked GitHub issue record. |
| `Vulnerability.java` | Finding evidence, identity, fix, and status. |
| `VulnerabilityStatus.java` | Finding lifecycle enum. |

### `graph`

| File | Purpose |
|---|---|
| `AuditWorkflowGraph.java` | Unified end-to-end graph orchestrating scan, chat, remediation, and publication. |
| `DuckDbCheckpointSaver.java` | Native LangGraph4j state persistence (now using PostgreSQL despite the name). |
| `MultiAgentRemediationGraph.java` | 15-stage Star Topology orchestration graph with a supervisor. |
| `RemediationGraph.java` | Inner LangGraph4j state machine for agent execution. |
| `RemediationState.java` | State definition for the remediation loop. |
| `agent/*.java` | Specialized agent definitions (`AgentStage.java`, `WorkerAgentGraphFactory.java`). |
| `node/SupervisorNode.java` | Dynamic routing node for the multi-agent workflow. |
| `node/*.java` | Individual graph nodes (Init, ModelCall, ToolExecution, etc.). |
| `state/MultiAgentState.java` | Agent handoff state definition; includes `planGenerated` and `remediationPlan` for the PLANNING stage. |
| `workflow/RemediationWorkflowGraph.java` | Outer LangGraph4j state machine for the end-to-end workflow. |
| `workflow/WorkflowState.java` | State definition for the workflow orchestration. |
| `workflow/*.java` | Nodes for workspace setup, and publication. |

### `service`

| File | Purpose |
|---|---|
| `AgentToolService.java` | Secure model tools and managed processes; Docker sandbox wrapping. |
| `ConversationMemoryService.java` | Durable LangChain message history, context summarization, and aggressive tool output truncation. |
| `DatabaseService.java` | Unified persistence facade delegating to Conversation, Remediation, User, and Scan domain data services. |
| `FilePatchService.java` | Backup, rollback, diff, and legacy patch helper. |
| `FindingFingerprint.java` | Stable normalized SHA-256 finding identity. |
| `GitHubApiClient.java` | OAuth/App/installation/repository/PR HTTP APIs, issue comments (`createIssueComment`), and PR diff retrieval (`getPullRequestDiff`). |
| `GitHubAuthService.java` | OAuth, tokens, sessions, CSRF, refresh. |
| `GitHubProvider.java` | JGit clone/diff/hash/commit/push/PR/cleanup. |
| `IssueLifecycleService.java` | GitHub App issue webhook event processor, triage matching, autonomous remediation, and issue thread tracking. |
| `LlmService.java` | Chat, tool-calling remediation loop, failure reflection (`reflectOnFailure`), and PR diff learning (`summarizeDiffForGlobalPattern`). |
| `ManagedProcessEnvironment.java` | Secret scrubbing, contained runtime directories, and validated scanner-temp override. |
| `ManagedProcessRunner.java` | Concurrent stream draining, heartbeat, deadline, process-tree termination, and short per-scan temp lifecycle. |
| `MemoryRedactor.java` | Secret masks and caps. |
| `OrchestratorService.java` | Legacy local-path orchestration retained for compatibility; not current controller path. |
| `PullRequestLifecycleService.java` | Pull request webhook events, and vulnerability state transitions. |
| `PullRequestProvider.java` | Pull-request abstraction. |
| `PdfReportRenderer.java` | Paginated enterprise PDF generation through Apache PDFBox. |
| `ProjectUploadService.java` | Project upload scan orchestration, persistence, and report export. |
| `RemediationWorkflowService.java` | GitHub-native remediation workflow orchestration. |
| `ReporterService.java` | Enterprise Markdown/PDF generation and legacy HTML compatibility generation. |
| `RepositoryAccessService.java` | Centralized repository authorization check. |
| `RepositoryMemoryService.java` | Renders approved retrieval into prompt. |
| `ScannerService.java` | Language selection, Semgrep execution, finding parsing. |
| `ScanExecutionException.java` | Terminal scanner execution/output failure. |
| `ScanOrchestratorService.java` | GitHub-native scan orchestration. |
| `ScanTimeoutException.java` | Distinct scanner deadline failure. |
| `SecurityConceptCatalog.java` | Categories, aliases, framework and query metadata. |
| `SkillManagerService.java` | Dynamic skill discovery/parsing. |
| `SourceControlProvider.java` | Repository/workspace abstraction. |
| `TokenCipher.java` | AES-GCM OAuth encryption. |
| `ZipArchiveExtractor.java` | Safe archive validation, extraction, ZipSlip protection, and directory cleanup. |

### `service/data`

| File | Purpose |
|---|---|
| `ConversationDataService.java` | Domain data service managing `MemoryThread` aggregate root and `MemoryMessage` collections. |
| `RemediationDataService.java` | Domain data service managing `AgentRun` aggregate root, steps, verification results, changes, checkpoints, and repository memories. |
| `UserDataService.java` | Domain data service managing `AppUser` aggregate root, user sessions, GitHub authorizations, OAuth state, and notifications. |
| `ScanDataService.java` | Domain data service managing `ManagedRepository` aggregate root, scan snapshots, scan history stats, reports, vulnerabilities, webhooks, tracked issues, and token usage. |

## Frontend production files

| File | Purpose |
|---|---|
| `src/main.jsx` | React DOM bootstrap. |
| `src/App.jsx` | Top-level orchestration and layout. |
| `src/index.css` | Tailwind import and global helpers. |
| `src/App.css` | Unused legacy starter styles. |
| `src/types.js` | JSDoc API/state types. |
| `src/api/auditApi.js` | Credentialed API/SSE client and result normalization. |
| `src/api/sse.js` | Robust JSON SSE parser. |
| `src/components/AppHeader.jsx` | Brand, scan summary, identity, logout. |
| `src/components/NotificationDropdown.jsx` | Bell icon notification dropdown with unread badge and real-time updates. |
| `src/components/TokenUsageView.jsx` | User-level UI displaying aggregated token usage and observability metrics. |
| `src/features/auth/LoginPage.jsx` | Login/configuration screen. |
| `src/features/chat/ChatWidget.jsx` | Floating durable chat/recovery controls. |
| `src/features/chat/components/ChatHeader.jsx` | Top header for the chat widget. |
| `src/features/chat/components/RecoveryBanner.jsx` | Banner for interrupted/failed run recovery. |
| `src/features/chat/components/PrBanner.jsx` | Banner showing active PR links. |
| `src/features/chat/components/MessageBubble.jsx` | Renders individual chat messages with role-based styling. |
| `src/features/chat/components/CodeBlock.jsx` | Syntax-highlighted fenced blocks with copy and collapse support. |
| `src/features/chat/components/CollapsibleText.jsx` | Wraps long prose with a "Read more" fade. |
| `src/features/chat/components/ThinkingIndicator.jsx` | Animated dots during streaming start. |
| `src/features/chat/components/ChatInput.jsx` | Auto-growing textarea and send/stop controls. |
| `src/features/chat/components/ChatFab.jsx` | Floating action button toggle for chat. |
| `src/features/chat/components/NewMessagesPill.jsx` | Smart scroll down button for new messages. |
| `src/features/chat/useChatController.js` | Direct/model chat command flow and stream cancellation. |
| `src/features/chat/useChatState.js` | Chat view state, unread behavior, and streaming status. |
| `src/features/dashboard/DashboardView.jsx` | Scan summary/empty state. |
| `src/features/findings/FindingsView.jsx` | Responsive severity-prioritized inventory, count-aware filters, search/reset controls, and finding selection. |
| `src/features/issues/IssuesView.jsx` | Tracked GitHub issues view displaying webhook/label-triggered issues, remediation status, finding links, PR links, and search/filtering. |
| `src/features/remediation/FindingDrawer.jsx` | Evidence, diff, decisions, retry, PR state. |
| `src/features/remediation/useRemediationController.js` | Run/publication browser state. |
| `src/features/report/ReportView.jsx` | Authenticated PDF viewer plus PDF/Markdown downloads, loading, retry, and Blob URL cleanup. |
| `src/features/report/WorkspaceTabs.jsx` | Main tab navigation (Dashboard, Findings, Issues, Audit Report) with count badges. |
| `src/features/scan/ScanPanel.jsx` | Selection, progress, errors, install guidance. |
| `src/features/scan/scanState.js` | Scan reducer and stale-event rules. |
| `src/features/scan/useScanController.js` | Scan request/SSE lifecycle. |
| `src/features/scan/useScanElapsedSeconds.js` | Wall-clock scan duration calculation and display tick. |
| `src/features/session/useMemoryThread.js` | Stable thread restore/forget/race handling. |
| `src/utils/auditSelectors.js` | File labels, stable severity ordering, multi-term finding filters, counts, time, and progress stages. |

## Resources and build files

| Path | Purpose |
|---|---|
| `src/main/resources/application.yaml` | Common runtime settings and the default `local` Spring profile selection. |
| `src/main/resources/application-example.yaml` | Tracked credential-free template to copy to an ignored profile-specific configuration file. |
| `src/main/resources/application-{local,dev,prod}.yaml` | Ignored environment overlays; local convenience only and never a committed secret source. |
| `src/main/resources/META-INF/additional-spring-configuration-metadata.json` | IDE metadata for scanner/agent/database/memory settings. |
| `src/main/resources/static/` | Compiled frontend served by Spring. |
| .auditagent/skills/stage-*/SKILL.md | Active dynamic prompts for the 16 code review stages. |
| .auditagent/skills/patch-engineer/SKILL.md | Legacy remediation model playbook. |
| .auditagent/skills/db-manager/SKILL.md | Discoverable historical database/reporting playbook; its named tools are not registered in the current runtime. |
| .auditagent/skills/github-pr-manager/SKILL.md | Discoverable historical PR playbook; current publication instead uses automated publication and managed services. |
| `.agents/skills/doc-navigator/SKILL.md` | Developer assistant skill for navigating and searching `@doc`. |
| `.agents/skills/git-feature-workflow/SKILL.md` | Developer assistant skill for branch isolation and grouped commits. |
| `.agents/skills/systematic-debugging/SKILL.md` | Developer assistant skill for structured root-cause debugging. |
| `.agents/skills/frontend-design/SKILL.md` | Developer assistant skill for distinctive UI design. |
| `.agents/rules/git-commit-policy.md` | Developer workspace rule: user approval required prior to git commit. |
| `.agents/rules/pr-merge-policy.md` | Developer workspace rule: Pull Requests must strictly target and merge into dev only. |
| `pom.xml` | Java dependencies, versions, build plugin. |
| `frontend/package.json` | Frontend dependencies and scripts. |
| `frontend/vite.config.js` | plugins, backend proxy, production output. |
| `frontend/vitest.config.js` | frontend test environment. |
| `.gitignore` | Excludes build/runtime artifacts and populated profile-specific configuration files. |
| `docker-compose.yml` | Container Compose specification for AuditAgent application, frontend dev server, and sandbox builder connecting to external PostgreSQL. |
| `Dockerfile` | Multi-stage production container packaging Java 21, Semgrep CLI, Maven, Gradle, Python 3, and Git. |
| `Dockerfile.sandbox` | Sandbox container image definition (`auditagent-sandbox:latest`) for isolated tool execution. |
| `frontend/Dockerfile.dev` | Frontend Vite dev server container with HMR for containerized frontend development. |
| `.dockerignore` | Excludes build artifacts, databases, dependencies, and version control files from container builds. |
| `.env.example` | Template with full environment variable definitions for Docker Compose deployments. |
| `run-dev.sh` | parallel development startup/logs. |
| `run.sh` | frontend build then backend startup. |

| `.githooks/pre-commit`                          | Blocks relevant staged changes when no `doc/*.md` update is staged. |
| `scripts/install-git-hooks.ps1`                 | Enables versioned hooks for PowerShell/Windows users. |
| `scripts/install-git-hooks.sh`                  | Enables versioned hooks and executable bit on Unix-like systems. |
| `.devcontainer/devcontainer.json`               | Temurin Java 21, Node.js 24 & Python 3.12 development container. |
| `release_notes/*.md`                            | Versioned release notes tracking product, tooling, and infrastructure updates (v2.0.0, v2.0.1, v2.1.0, v2.1.1, v2.2.0, v2.2.1). |

## Test file map

### Backend

`AuditagentApplicationTests`, `ApiSecurityFilterTest`, `AuditControllerReportExportTest`, `GitHubWebhookControllerTest`, `GitHubRepositoryControllerTest`, `IssueLifecycleServiceTest`, `AgentToolServiceSecurityTest`, `ConversationMemoryServiceTest`, `DatabaseMemoryServiceTest`, `GitHubAuthServiceTest`, `GitHubProviderIntegrationTest`, `LlmServiceAgentLoopTest`, `ManagedProcessRunnerTest`, `ProjectUploadControllerTest`, `ProjectUploadServiceTest`, `RemediationWorkflowPreparationTest`, `ScanOrchestratorReportExportTest`, `ScanOrchestratorServiceTest`, `ReporterServiceTest`, `ScannerServiceTest`, `TokenCipherTest`, and `ZipArchiveExtractorTest`.

### Frontend

Tests cover App, API, SSE, scan state/panel/wall-clock timing, thread restore, remediation controller/drawer, findings, tracked issues, and report.

## Rule inventory

The repository currently contains 666 Semgrep YAML rule files.

| Top-level rule directory | Count | Selected by scanner for |
|---|---:|---|
| `rules/html` | 6 | `.html`, `.htm` |
| `rules/java` | 125 | `.java`, `.kt` |
| `rules/javascript` | 173 | `.js`, `.jsx` |
| `rules/python` | 337 | `.py` |
| `rules/typescript` | 25 | `.ts`, `.tsx` |

Rules are further organized by language/framework and categories such as security, audit, correctness, and best practice. When no supported language is detected, the full `rules` directory is passed to Semgrep.

To refresh counts:

```powershell
Get-ChildItem rules -Directory | ForEach-Object {
  [PSCustomObject]@{
    Language = $_.Name
    Rules = (Get-ChildItem $_.FullName -Recurse -Filter *.yaml).Count
  }
}
```

## Glossary

**Agent run** — one durable attempt to remediate one finding.

**Publication digest** — SHA-256 binding of run/finding/base/branch/file hashes/verification evidence.

**Base SHA** — exact Git commit scanned and used to prepare a remediation.

**BM25** — lexical ranking algorithm used by DuckDB FTS.

**CSRF** — attack that causes a signed-in browser to send an unwanted mutation; prevented with a session-bound header token.

**Finding** — one Semgrep result represented by `Vulnerability`.

**Finding fingerprint** — stable SHA-256 of normalized rule ID, path, and vulnerable code.

**FTS** — full-text search. Here it is DuckDB lexical search, not embeddings.

**`getPullRequestDiff`** — `GitHubApiClient` method retrieving PR diffs via `application/vnd.github.v3.diff` for self-training.

**Global remediation pattern** — cross-repository positive remediation pattern or negative anti-pattern keyed by Semgrep rule ID in `global_remediation_patterns`.

**Installation token** — short-lived GitHub App credential used for bot repository operations.

**Managed repository** — server-resolved GitHub repository from an installation.

**Managed workspace** — isolated clone below the configured workspace root.

**Memory thread** — stable conversation keyed to user and repository/branch.

**OAuth user token** — GitHub credential used for repository discovery and user permission checks.

**Positive repository memory** — merged, approved remediation pattern eligible for future prompt retrieval.

**PR_OPEN** — remediation was published, but merge is not yet confirmed.

**Recovery checkpoint** — persisted phase/status/iteration/Git hashes enabling safe retry or resume.

**`reflectOnFailure`** — `LlmService` method asynchronously generating anti-pattern warnings from failed agent trajectories when hitting maximum iterations.

**Report key** — `github:<repositoryId>:<branch>`, used to scope report, finding, and repository memory.

**`saveGlobalPatternTx`** — `DatabaseService` transactional method persisting, accumulating, and capping positive and negative patterns in `global_remediation_patterns`.

**SSE** — server-sent events, used to stream JSON progress from POST workflows.

**`summarizeDiffForGlobalPattern`** — `LlmService` method asynchronously extracting abstract positive remediation patterns from merged PR diffs.

**Automated publication** — explicit API decision with exact run/finding/digest; not chat text.

**Tool turn** — model tool request plus its corresponding result(s), kept together in memory.

## Status quick reference

### Finding

| Status | Plain meaning |
|---|---|
| `DETECTED` | Available for remediation. |
| `ANALYZING` | Agent run is working. |
| `PATCH_FAILED` | Agent did not produce a verified fix. |
| `AWAITING_APPROVAL` | Verified diff autonomously published. |
| `PR_OPEN` | Published and awaiting merge/close. |
| `FIXED` | GitHub confirmed merge. |
| `IGNORED` | Finding explicitly identified as a false positive or manually skipped; unverified run failures transition to `PATCH_FAILED` instead. |

### Recoverable run

| Status | Action |
|---|---|
| `INTERRUPTED` | Resume unapproved work if hashes match; retry if already approved. |
| `AWAITING_APPROVAL` | Autonomous publication triggered. |
| `PUBLISH_FAILED` | Retry publication. |
| `PR_OPEN` | Track/reconcile GitHub; do not republish. |
| `CONFLICTED` | Inspect and generally start fresh; no automatic overwrite. |
