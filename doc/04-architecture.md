# Architecture

## System shape

AuditAgent is a single full-stack application:

```text
Browser (React)
  | credentialed JSON and SSE over /api
  v
Spring WebFlux controllers
  | authentication, CSRF, ownership, validation
  v
RemediationWorkflowService / LlmService
  +--> GitHub OAuth and REST API
  +--> JGit isolated workspaces
  +--> Semgrep process
  +--> Maven/Gradle processes
  +--> OpenAI-compatible LLM provider
  +--> PostgreSQL & DuckDB FTS
```

The Vite frontend is compiled into `src/main/resources/static` for the packaged application. In development, Vite runs on port 5173 and proxies `/api` to Spring Boot on port 8173.

## Trust boundaries

### Browser to server

The browser is untrusted for identity, repository location, findings, PR publication state, and code content. The server accepts repository ID, branch, thread ID, run ID, and finding ID only as references. It resolves and revalidates ownership and GitHub access. Webhooks drive the synchronized state transitions in the backend.

### Server to GitHub

Two credentials have different jobs:

- User OAuth token: repository discovery and current-user permission checks.
- Installation token: exact-SHA clone, branch push, and pull-request API work.

Installation tokens are created just in time and are not persisted.

### Server to repository code

Repository contents and build scripts are untrusted. AuditAgent:

- clones only under a configured managed root;
- resolves target files below a real, non-symlink repository root;
- rejects traversal and symbolic-link components;
- removes server credentials and process-injection environment variables;
- relocates home, temp, Maven, Gradle, and XDG caches under the clone;
- applies timeouts and output caps.

These Java checks are defense in depth, not a complete OS sandbox.

### Server to AI model

The model can request only nine named tools with explicit schemas. Tool execution is server-side. The model cannot directly call GitHub publication, change authorization state, or decide that verification passed.

## Main request flows

### Authentication

```text
GET /auth/github/login
  -> random state + PKCE verifier
  -> DB stores hash(state) + encrypted verifier
  -> HttpOnly browser-state cookie
  -> GitHub authorization
GET /auth/github/callback
  -> compare state with browser cookie
  -> atomically consume unexpired DB state
  -> exchange code + verifier
  -> load GitHub user
  -> encrypt OAuth tokens
  -> create hashed opaque session + CSRF token
  -> set HttpOnly session cookie
```

### Scan

```text
authenticate + own thread + read repository
  -> installation token
  -> read current branch head SHA
  -> use cache only if latest snapshot SHA matches
  -> clone exact SHA to scan workspace
  -> detect languages
  -> choose local rule directories
  -> run Semgrep
  -> parse findings + stable fingerprints
  -> generate Markdown and HTML
  -> transactionally save report/findings
  -> save scan snapshot
  -> stream result
  -> delete scan workspace
```

### Remediation

```text
authenticate + own thread + read repository
  -> load latest scan snapshot and bound finding
  -> reject duplicate active fingerprint
  -> create durable run and publication record
  -> clone exact scanned SHA
  -> run tool-calling agent
  -> persist messages, steps, changes, evidence, checkpoints
  -> require patch + build + target -> compile, test, rescan
  -> create PR preview and digest
  -> autonomously publish pull request
```

### Publication

```text
autonomous trigger
  -> own run + match finding
  -> current base SHA == scanned SHA
  -> workspace hashes unchanged
  -> create/reuse bot commit
  -> push/reuse branch
  -> create/reuse PR
  -> persist each checkpoint
  -> mark PR_OPEN and clean local workspace
  -> webhook/reconciliation later marks merged or closed
```

## Backend layers

### Controllers

Controllers translate HTTP into application operations. `ApiSecurityFilter` handles the cross-cutting authentication/CSRF boundary before protected controllers.

### Orchestration

`RemediationWorkflowService`, `ScanOrchestratorService`, `RepositoryAccessService`, and `PullRequestLifecycleService` coordinate the GitHub-native flows. They delegate workflow orchestration to LangGraph4j via `RemediationWorkflowGraph`, which manages the stateful transitions between workspace cloning, agent remediation loops, and autonomous pull request publication. The remediation loop itself can run either in a single-agent mode (`RemediationGraph`) or a multi-agent mode (`MultiAgentRemediationGraph`), where specialized sub-agents handle Triage, Patching, and Verification. Webhooks from GitHub process PR events independently and `useRemediationController` handles UI polling for updates. `OrchestratorService` remains in the codebase as a legacy local-path coordinator but is not used by current shared controllers. Future shared features should use managed repository identity, not revive local-path authorization.

### Integration services

- `GitHubAuthService`: OAuth, sessions, refresh.
- `GitHubApiClient`: GitHub HTTP API and App JWT.
- `GitHubProvider`: JGit workspace and publication operations.
- `ScannerService`: Semgrep.
- `LlmService`: OpenAI chat and remediation loop.
- `AgentToolService`: constrained repository tools.

### Persistence

`DatabaseService` owns all PostgreSQL schema and access. Services should not create ad-hoc tables or raw alternative persistence paths.

### Presentation

`ReporterService` creates the persisted Markdown and legacy standalone HTML representations. For the current product experience, `PdfReportRenderer` uses Apache PDFBox to generate a paginated PDF in memory from the authorized persisted report when requested. React fetches that protected artifact for viewing and offers PDF and Markdown downloads; no generated report HTML is executed by the current UI.

## Frontend layers

```text
App.jsx
  +-- authentication and selection orchestration
  +-- useMemoryThread
  +-- useScanController -> scanReducer
  +-- useRemediationController
  +-- useChatState + useChatController
  +-- presentational feature components
  +-- auditApi -> fetch + SSE parser
```

State that must survive reload lives in PostgreSQL. Ephemeral view state—open tab, selected row, timer, chat visibility—lives in React.

## Concurrency model

- Spring WebFlux handles HTTP reactively.
- Long workflows create unicast Reactor sinks.
- Scan, remediation, decision, retry, resume, and background FTS work use Java 21 virtual threads.
- Process output is drained on a virtual thread to avoid deadlock.
- PostgreSQL access methods are mostly synchronized because the service shares a file database.
- FTS rebuilds have an explicit `ReentrantLock`.
- Frontend request IDs and abort controllers prevent stale asynchronous results from winning.

## Repository layout

```text
auditagent/
  .auditagent/skills/patch-engineer/  remediation playbook
  .devcontainer/                  Java 21, Node.js 24, and Python 3.12 development container
  frontend/                       React/Vite application and tests
  rules/                          bundled Semgrep rules
  src/main/java/.../
    aspect/                       method lifecycle logging
    config/                       security, CORS, AI, memory configuration
    controller/                   HTTP and SSE endpoints
    domain/                       durable and API domain records/classes
    dto/                          request/response payloads
    service/                      business logic and integrations
  src/main/resources/
    application.yaml              runtime defaults
    static/                       compiled frontend
  src/test/                       Java tests
  doc/                            this documentation set
```

## Architectural invariants

- GitHub repository ID plus branch is the shared repository identity.
- Report keys use `github:<repositoryId>:<branch>`.
- The scan snapshot's `baseSha` is the immutable base for remediation and PR generation.
- The `vulnerabilities` table serves as the source of truth for tracking vulnerability status (e.g., from `DETECTED` to `PR_OPEN` and `FIXED`).
- All Git writes go through the autonomous publication pipeline via `GitHubProvider`.
- Only `DatabaseService` evolves schema.
- Only verified, merged work creates positive memory.
- All repository processes use the managed environment scrubber.
