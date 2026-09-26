# Backend guide

## Application entry and framework

`AuditagentApplication` starts Spring Boot. The backend uses Java 21, Spring Boot 4.0.6, WebFlux, validation, Actuator, Spring AI 2.0.0-RC1, LangChain4j 1.0.0-beta4, DuckDB JDBC 1.1.3, JGit 7.6, and aligned AWS SDK modules.

## Controllers

### `AuthController`

- Returns authentication/configuration state and the CSRF token.
- Starts OAuth and creates the ten-minute browser-state cookie.
- Completes OAuth, creates the session cookie, expires the state cookie, and redirects to the frontend.
- Logs out by deleting the session and expiring the cookie.

### `GitHubRepositoryController`

- Lists repositories from the user's installations and upserts their server-side metadata.
- Loads branches only after retrieving stored repository metadata and revalidating user read access.

### `AuditController`

- `/scan`: requires repository ID and nonblank branch; local paths are explicitly disabled.
- `/analyze`: requires repository ID and branch and starts managed remediation.
- `/reports`: restores the latest repository/branch report.
- `/skills`: lists dynamically discovered local skills.
- `/chat`: requires thread ownership and repository access, then loads findings from DuckDB.

### `MemoryController`

- Creates/restores the stable user/repository/branch thread.
- Returns user-visible general messages, active recoverable run, publication, active finding, and FTS health.
- Reconciles an open PR with GitHub during restore.
- Forgets a thread after discarding unpublished recoverable work.
- Forgets repository memory by report key.
- Resumes or discards runs.

### `RunPublicationController`

- Builds the PR preview and autonomously proceeds to creation.
- Accepts the only structured decisions.
- Retries an approved publication failure.

### `GitHubWebhookController`

- Validates `X-Hub-Signature-256` with HMAC-SHA256.
- Deduplicates `X-GitHub-Delivery`.
- Ignores non-pull-request events after recording them.
- Processes only `pull_request` action `closed`.
- Releases the delivery claim if processing fails, allowing GitHub to retry.

### `ObservabilityController`

- Provides endpoints for tracking AI utilization.

- Backed by user-bound queries in `DatabaseService`.

### `ApiExceptionHandler`

- `SecurityException` → 403.
- `IllegalArgumentException` → 400.
- `IllegalStateException` → 409.
- Messages are secret-redacted and capped at 500 characters.
- Filter-level unauthenticated requests return 401 directly.

## Configuration components

### `ApiSecurityFilter`

Runs near highest precedence. It authenticates protected `/api/**` requests and attaches `AuthenticatedUser` to exchange attributes. Non-GET/HEAD requests require the session's CSRF token.

### `CorsConfig`

Allows configured frontend origin, credentials, `GET`, `POST`, `DELETE`, `OPTIONS`, `Content-Type`, and `X-CSRF-Token`.

### `GitHubAppConfig`

Holds GitHub credentials, endpoints, workspace root, API version, cookie behavior, and session duration. "Configured" requires App ID, client ID/secret, private key, webhook secret, and token-encryption key.

### `AgentConfig`, `ScannerConfig`, and `MemoryConfig`

Typed and validated settings for model/tool limits, Semgrep timeout and heartbeat, backup path, retention, context budget, retrieval, and content caps.

### `LangChainModelConfig`

Builds the tool-calling OpenAI-compatible model with configured model name, base URL, temperature, and output tokens. Uses standard OpenAI API key configuration.

### `Jackson2Config`

Provides a Jackson 2 mapper needed by current integrations when Spring Boot 4's default JSON generation differs.

## Core services

### `RepositoryAccessService`

Centralizes access control logic. It verifies whether an authenticated user has the necessary permissions (e.g., `READ` or `WRITE` access) to perform actions on a specific repository.

### `ScanOrchestratorService`

Coordinates the repository scanning workflow. It handles cache checks, scan execution via `ScannerService`, processing results, generating reports via `ReporterService`, and storing snapshots.

### `RemediationWorkflowService`

Orchestrates the AI remediation lifecycle. It delegates complex multi-agent flows to LangGraph4j, manages stateful transitions (cloning, fixing, verifying), handles manual resume/discard actions, and processes autonomous publication.

### `PullRequestLifecycleService`

Processes GitHub pull request events via webhooks or manual reconciliation. It updates vulnerability states and handles positive memory creation and metrics tracking when PRs are merged or closed.

### `LlmService`

Has two modes:

- simple Spring AI conversation endpoint;
- LangChain4j tool-calling remediation loop.

It constructs prompts, loads approved memory, restores durable history, applies model timeouts, executes tools, persists idempotent steps and evidence, enforces verification, and emits progress.

### `AgentToolService`

Implements the nine remediation tools. It owns secure paths, search caps, exact replacement, supported build/test commands, dynamic Semgrep rule selection and target rescans, process and rule timeouts, and file hashes.

### `FilePatchService`

Creates timestamped backups under `.auditagent/backups/<timestamp>/<relative-file>`, finds the newest backup, restores exact or latest backups, and creates simple unified diffs. Its legacy `applyPatch(Vulnerability)` supports exact and line-number replacement, but the current agent uses `AgentToolService.applyPatch()` for unique exact replacement and hash evidence.

### `ScannerService`

Detects languages, chooses rule folders, builds the Semgrep command, validates exit/output, parses JSON, calculates basic metrics, and maps findings. Exit codes 0 and 1 are accepted; empty output, malformed JSON, and other exit codes throw a scan failure before report persistence.

### `ManagedProcessRunner`

Runs Semgrep with concurrent stdout/stderr draining so either OS pipe cannot block the other. It preserves complete JSON stdout, retains the final 64 KiB of stderr, emits monotonic liveness heartbeats, enforces the configured deadline, and terminates the root and observed descendant processes after a five-second graceful window. Descendants are also stopped after a normal or non-zero root exit so native RPC workers cannot retain workspace files.

`ManagedProcessEnvironment` supplies the scrubbed environment. HOME, Gradle, and XDG state remain inside the managed clone. Semgrep is the deliberate TEMP/TMP/TMPDIR exception: the runner creates a random `aa-*` directory directly under the real JVM OS-temp root because deeply nested Windows temp paths can make Semgrep's native RPC `socketpair` fail. The directory is unique to one scan, receives owner-only POSIX permissions where supported (and the current user's inherited temp ACL on Windows), is validated before use and deletion, and is cleaned only after descendant termination. Other managed build/test tools keep their temp directories inside the workspace.

### `ReporterService`

Creates a portable enterprise Markdown assessment and delegates management-ready PDF generation to a lazily initialized `PdfReportRenderer`. PDFBox writes the PDF entirely in memory with repository/branch/commit context, an executive summary, severity metrics, scan scope, paginated findings, bounded code previews, and confidentiality/page labels. Its reusable font metadata cache is directed to `auditagent-pdfbox-font-cache` below the JVM temporary directory so a locked-down service home is not required and application startup does not eagerly scan fonts. The full persisted Markdown is used for Markdown exports. Standalone HTML generation and escaping remain for storage and payload compatibility, but the current frontend neither executes nor downloads that HTML.

`ScanOrchestratorService.exportReport()` revalidates repository read access, resolves the latest branch snapshot, and returns a `ReportArtifact`. PDF and Markdown are rendered on demand from persisted findings and metadata so both exports are bound to the authorized repository, branch, and scanned base SHA; the stored Markdown remains the durable scan representation. Unsupported formats fail explicitly, and filenames are normalized and length-bounded safe repository/branch-derived values.

### `DatabaseService`

Owns schema migration, report and vulnerability upserts, memory, runs, evidence, changes, retrieval, users, authorization, sessions, repositories, snapshots, publications, webhooks, and retention.

### GitHub services

- `GitHubAuthService`: state, PKCE, encrypted OAuth, hashed sessions, CSRF, refresh.
- `GitHubApiClient`: REST calls, App JWT, private-key parsing, permission queries, PR operations.
- `GitHubProvider`: managed paths, exact clone, diff/hash/status, commit/push/PR, cleanup.
- `SourceControlProvider` and `PullRequestProvider`: interfaces that keep orchestration testable.

### Memory services

- `ConversationMemoryService`: durable message conversion, complete-turn grouping, budgeted history.
- `RepositoryMemoryService`: renders matching approved memories into a prompt section.
- `MemoryRedactor`: secret patterns and character caps.
- `SecurityConceptCatalog`: deterministic security categories, aliases, frameworks, and search text.
- `FindingFingerprint`: normalized SHA-256 finding identity.

### Skills

`SkillManagerService` scans `.auditagent/skills`, validates lowercase/digit/hyphen folder names, parses the final frontmatter-separated instruction body, and rediscovers on each lookup/list request.

### Observability

`AopLoggingConfig` applies a CGLIB method interceptor to services and controllers. It logs method identity, argument count, return type, and exception class—not argument values.
Also includes token usage tracking, persisting `token_usage` metrics via DuckDB and tracking operations through Micrometer counters (`ai.tokens.consumed`, `findings.resolved`).

## Graph orchestration (LangGraph4j)

The backend now uses `langgraph4j` to orchestrate agents and workflows:
- `RemediationGraph`: Core agent logic using a `StateGraph` to loop through LLM calls, tool execution, and stuck detection.
- `RemediationWorkflowGraph`: Outer state machine wrapping the remediation graph with `clone_workspace`, `approval_ready` (which now automatically publishes), `publish`, and `reject` nodes.
- `GitHubWebhookController`: Handles asynchronous `pull_request` webhook payloads, identifying merges and instantly updating the grouped vulnerability findings.
- `MultiAgentRemediationGraph`: A 3-agent orchestration graph (Triage, Patch, Verification) with retry edges, subsuming the single-agent `RemediationGraph`.
- `AuditWorkflowGraph`: Unified end-to-end graph orchestrating scan, chat, remediation, and publication workflows into a single timeline.
- `DuckDbCheckpointSaver`: Connects LangGraph4j's state-saving mechanisms natively to DuckDB, enabling durable suspend/resume operations.
- `WorkflowState`, `RemediationState`, & `MultiAgentState`: Custom state objects to hold parameters during graph execution and multi-agent handoffs.

## Domain states

### Finding states

`DETECTED`, `ANALYZING`, `GENERATING_FIX`, `VERIFYING`, `PATCH_FAILED`, `AWAITING_APPROVAL` (transient), `PR_OPEN`, `FIXED`, `IGNORED`.

Not every enum value is currently assigned directly by the managed coordinator; some exist for fine-grained agent/report compatibility.

### Run statuses

`ACTIVE`, `AWAITING_APPROVAL`, `PUBLISHING`, `PUBLISH_FAILED`, `PR_OPEN`, `PR_MERGED`, `PR_CLOSED`, `APPROVED`, `REJECTED`, `FAILED`, `INTERRUPTED`, `CONFLICTED`, `DISCARDED`.

### Run phases

Planning, workspace preparation, context, patching, verifying, approval, committing, pushing, PR creation/tracking, and terminal states. Phase is the operational checkpoint; status is the broader recoverability/lifecycle state.

## Error and cleanup behavior

- Scan failures emit a `status` event and clean the scan workspace.
- Remediation failure persists a failed/conflicted run when possible, updates the finding, cleans the workspace, and emits status.
- Unverified completion cleans the workspace and transitions to a failed state.
- Approved verification autonomously proceeds through `ApprovalReadyNode` and `PublicationNode`.
- Grouped file findings are bulk updated to `PR_OPEN` and then `FIXED` by the webhook processor.
- Successful PR creation cleans the local workspace because remote state is durable.
- Publication failure keeps state required for retry.
- Cleanup failure is logged with a redacted message and does not overwrite primary workflow state.

## Legacy code warning

`OrchestratorService` still exposes local-path methods and `ThreadState` compatibility behavior. Current controllers do not route shared requests through it. Treat it as migration/legacy code; do not use it as a pattern for new authorization-sensitive features.
