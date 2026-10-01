# Feature catalog

This catalog connects each feature to its user/business outcome and main implementation. It is intentionally detailed so future changes can identify affected areas.

## Identity and repository access

| Feature | User or business behavior | Implementation |
|---|---|---|
| GitHub OAuth sign-in | Users authenticate with their real GitHub identity. | `AuthController`, `GitHubAuthService`, `GitHubApiClient` |
| Browser-bound OAuth state | A callback from a different browser cannot complete the flow. | Random state, hashed DB state, encrypted PKCE verifier, HttpOnly `AUDITAGENT_OAUTH_STATE` cookie, constant-time comparison |
| PKCE S256 | Stolen authorization codes cannot be exchanged without the verifier. | 64-byte verifier and SHA-256 URL-safe challenge |
| Expiring token refresh | User access can continue without exposing refresh tokens. | `GitHubAuthService.accessToken()` refreshes one minute before expiry |
| Encrypted credentials | OAuth access and refresh tokens are ciphertext at rest. | AES-GCM with random 12-byte IV in `TokenCipher` |
| Opaque server session | Browser uses a cookie; DB stores only its hash. | `AUDITAGENT_SESSION`, SHA-256 session hash, session expiry and last-seen timestamp |
| Timezone-independent auth lifetime | OAuth state and authenticated sessions keep their configured lifetime on UTC and non-UTC servers. | UTC expiry values plus `CURRENT_TIMESTAMP AT TIME ZONE 'UTC'` comparisons in `DatabaseService` |
| CSRF protection | Authenticated cross-site mutation attempts are rejected. | `ApiSecurityFilter`; `X-CSRF-Token` on all protected non-GET/HEAD requests |
| Strict API allowlist | Only OAuth entry/callback, session discovery, preflight, and GitHub webhook bypass session authentication. | `ApiSecurityFilter` |
| Credentialed origin restriction | Browser API calls are accepted only from configured frontend origin. | `CorsConfig` |
| Installed repository discovery | Users see only repositories exposed by their GitHub App installations. | GitHub `/user/installations` and installation repositories APIs |
| Repository read revalidation | Stored repository metadata is not enough; current user access is rechecked. | `userCanRead()` in branch, scan, report, chat, memory operations |
| Branch discovery | Branches are fetched from GitHub after repository selection. | `GET /api/github/repositories/{id}/branches` |
| Permission separation | User token proves user access; installation token performs bot operations. | `GitHubAuthService`, `GitHubApiClient`, `GitHubProvider` |
| Secure cookie auto-enable | HTTPS callback or frontend URLs force Secure cookies. | `GitHubAppConfig.useSecureCookies()` |
| Logout | Session row and cookie are removed. | `POST /api/auth/logout` |

## Scanning and findings

| Feature | Behavior | Implementation |
|---|---|---|
| Pinned-commit scan | A report identifies the exact branch commit scanned. | `branchHead()`, `cloneAtCommit()`, `ScanSnapshot` |
| Scan history clearing | Users can delete all scan reports, snapshots, stats, and vulnerabilities for a selected repository branch. | `DELETE /api/reports/history`, `DatabaseService`, `DashboardView` |
| Isolated scan clone | Scanner never operates on a browser-supplied local directory. | unique `scan-<UUID>` workspace |
| Commit-aware cache | Report is reused only if stored base SHA equals current branch SHA and force rescan is false. | `ScanOrchestratorService.scan()` |
| Force rescan | User can bypass an otherwise valid cache. | `ScanRequest.forceRescan`, UI checkbox |
| Language detection | File extensions choose relevant rule directories. | Java/Kotlin, Python, JavaScript/JSX, TypeScript/TSX, HTML/HTM |
| Scan exclusions | Generated, dependency, IDE, lock, static-output, template, and test paths are excluded. | `ScannerService.runSemgrep()` |
| Bundled rules | Local rules are used without Semgrep metrics or version check. | 666 YAML files: HTML 6, Java 125, JavaScript 173, Python 337, TypeScript 25 |
| Fallback rule selection | All rule directories are used when no supported language is detected. | `getRuleConfigs()` |
| Progress streaming | UI receives init, language, rule, scan, parse, and periodic Semgrep liveness updates. | SSE from a virtual thread |
| Deadlock-safe scanner I/O | Semgrep stdout and stderr are drained concurrently; diagnostic stderr retains only its final 64 KiB. | `ManagedProcessRunner` |
| Windows RPC-compatible isolation | Each Semgrep run receives one random, short, private directory directly under the JVM OS-temp root; it is removed after the process tree stops. This avoids native Windows RPC `socketpair` failures caused by deeply nested temp paths. | `ManagedProcessRunner`, `ManagedProcessEnvironment` |
| Scan deadline and cleanup | A scan defaults to a 15-minute deadline; timeout/interruption and non-zero root exits terminate observed Semgrep descendants before workspace cleanup. | `ScannerConfig`, `ManagedProcessRunner` |
| Semgrep exit handling | Exit 0 and 1 are accepted; other exits, empty output, and invalid JSON are terminal scan failures and are not persisted as empty reports. | `ScannerService.runSemgrep()` |
| Finding parsing | Rule, path, line, snippet, description, severity, language, and fingerprint are mapped. | Semgrep JSON `results` |
| Severity mapping | Semgrep ERROR→HIGH, WARNING→MEDIUM, INFO→LOW; otherwise MEDIUM. | `ScannerService` |
| Context fallback | When Semgrep omits usable lines, four source lines from the finding are read. | `readContextFromFile()` |
| Stable finding identity | Matching rescans reuse ID, status, and proposed fix. | SHA-256 rule/path/code fingerprint and DB upsert |
| Scan metrics | Files, lines, duration, project, scanner, and timestamps are persisted. | `ScanMetadata` |
| Enterprise PDF report | Management-ready title, confidentiality label, repository/commit context, executive summary, severity metrics, scope, paginated finding details, code previews, and page labels are generated in memory from the persisted scan. | `ReporterService.generatePdf()`, `PdfReportRenderer`, Apache PDFBox |
| Markdown report | Portable full-detail summary and finding evidence are generated for engineering review and download. | `ReporterService.generateMarkdown()` |
| Legacy HTML compatibility | Standalone HTML continues to be persisted and returned in legacy payload fields so cached/older consumers are not broken, but the current UI does not render or download it. | `ReporterService.generateHtml()`, `html_report` compatibility field |
| Report restore | Latest repository/branch report loads on selection or reload. | `/api/reports`, frontend restore effect |
| Dual-format report export | Authenticated users can download the latest authorized report as PDF or Markdown with a safe repository/branch filename. | `GET /api/reports/export`, `ReportArtifact`, `auditApi.reportArtifact()` |
| PDF report viewing | The UI fetches the PDF as a protected Blob, embeds it for viewing, offers open/download actions, and revokes temporary object URLs on cleanup. | `ReportView` |

## User interface

| Feature | Behavior | Implementation |
|---|---|---|
| Authentication gate | Unauthenticated users see an enterprise access page with configuration status, protected-access guidance, and the no-write-before-approval promise. | `LoginPage` |
| Onboarding walkthrough tour | A dynamic onboarding modal guides first-time users through the Dashboard, Findings, Chat, and Remediation workflows. | `OnboardingTour.jsx`, `AppHeader.jsx`, `App.jsx` |
| Repository/branch panel | Selection, permission context, installation guidance, scanner, rescan toggle, wall-clock elapsed time, progress, and errors are grouped. Panel can be collapsed/expanded to maximize workspace area. | `ScanPanel`, `useScanElapsedSeconds`, `App.jsx` state |
| Responsive enterprise layout | Scan rail, workspace tabs, metrics, finding rows, report, full-screen mobile drawer, and bottom-sheet chat adapt from 320px mobile widths through large desktop workspaces. | Tailwind classes and `index.css` design primitives |
| Dashboard | Executive metric cards, severity distribution, risk posture, repository context, and scan-execution evidence. | `DashboardView` |
| Dark/light theme toggle | Persistent theme switcher (Sun/Moon) in header supporting high-contrast enterprise light mode and dark mode. | `useTheme`, `AppHeader`, `index.css` |
| Keyboard shortcuts | `Ctrl+K` to search/focus repository, `Ctrl+J` to toggle chat, `Esc` to close drawers/modals, `1/2/3` to switch tabs. Improves power-user navigation. | `useKeyboardShortcuts`, `App.jsx` |
| Scan history timeline | Historical view of finding severities over recent scans for the same repository and branch. | `scan_history_stats`, `DashboardView`, `GET /api/reports/history` |
| Severity-prioritized finding inventory | Stable `HIGH` to `MEDIUM` to `LOW` to `INFO` ordering; multi-term search across finding metadata; count-aware severity and lifecycle-status filters; responsive reset and no-result recovery actions. These view-only controls do not mutate findings or remediation state. | `FindingsView`, `filterFindings()` |
| Bulk action bar on findings | Checkbox selection on findings with bulk "Ignore" or "Analyze" actions allowing triaging in batches. A floating action bar summarizes selection. | `FindingsView`, `App.jsx` |
| Finding drawer | Detailed code, verified PR preview, side-by-side & unified diff viewer, lifecycle-aware actions, full-screen mobile presentation, initial close focus, and Escape dismissal. | `FindingDrawer`, `DiffViewer` |
| Workspace tabs | Sticky, horizontally scrollable Dashboard, Findings, Audit Report, and Documentation navigation. | `WorkspaceTabs` |
| In-app documentation viewer | Renders markdown files directly in the frontend using `@tailwindcss/typography`, removing the need to leave the application to read guides. | `DocsMainPanel`, `DocsSidebar`, `DocHandler` |
| Durable chat | Messages, timestamps, unread count, restore, auto-scroll, desktop floating panel, mobile bottom sheet, and Escape dismissal. | `useChatState`, `useMemoryThread`, `ChatWidget` |
| Chat rich rendering | Role-based MessageBubbles with Bot avatars, left-accent system messages, hover timestamps, and CSS entrance animations. | `MessageBubble`, `index.css` |
| Chat code blocks | Syntax-highlighted fenced blocks (Prism vscDarkPlus) with copy-to-clipboard and language badges. | `CodeBlock` |
| Chat auto-collapse | Code blocks over 12 lines and prose over 300 chars automatically collapse with a gradient fade and "Read more" toggle. | `CodeBlock`, `CollapsibleText` |
| Chat input upgrades | Auto-growing textarea supporting multi-line prompts via Shift+Enter, Enter to send. | `ChatInput` |
| Chat smart scroll | Auto-scrolls to bottom only if user is already near bottom; otherwise shows a bounce-animated "New messages ↓" pill. | `ChatWidget`, `NewMessagesPill` |
| Chat stream cancellation | "Stop" button appears during generation to abort the SSE stream connection. | `ChatInput`, `useChatController` |
| Stale response protection | Old scan and thread requests cannot overwrite a newer selection. | request counters and `AbortController` |
| Last good report preservation | Starting or failing a new scan keeps the prior successful result visible. | `scanReducer` |
| Structured recovery banner | Interrupted and publication-failed runs show appropriate actions. | `ChatWidget` |
| PR link | Open PR is visible in drawer and chat. | restored or streamed publication state |
| Explicit forget confirmation | Destructive memory actions require browser confirmation. | `window.confirm()` |

## Chat and AI behavior

| Feature | Behavior | Implementation |
|---|---|---|
| Finding-aware chat | Model receives current persisted findings and recent conversation. | `AuditController.chat()`, `LlmService.chat()` |
| Browser findings not trusted | Request contains no findings list; backend loads the report. | `ChatRequest` and report lookup |
| Direct local commands | `scan`, exact `VULN-...`, and `fix VULN-...` work without a model round trip. | `useChatController` |
| Model command tags | Model may request scan or analysis. | `[TRIGGER_SCAN]`, `[TRIGGER_FIX:...]` |
| No chat approval | Tags do not map to publication decisions. | publication exists only in `RunPublicationController` |
| Dual model integration | Spring AI handles chat; LangChain4j handles tool calling. | `ChatModel` and `OpenAiChatModel` |
| Dynamic skill loading | `.auditagent/skills/*/SKILL.md` is rediscovered on access. | `SkillManagerService` |
| Default remediation prompt | Agent remains functional if `patch-engineer` skill is unavailable. | `buildAgenticSystemPrompt()` fallback |
| Repository memory injection | Up to eight matching memories (both approved successes and rejected failures) enrich remediation context via PostgreSQL FTS/BM25 (mirrored from PostgreSQL). | `DatabaseService.searchRepositoryMemories()`, `DatabaseService.searchRejectedMemories()` |
| Knowledge base improvement | System learns from failures by asking user for a rejection reason, storing it, and providing it to the `MEMORY_REVIEW` agent on subsequent runs. Unmerged closed PRs lower confidence scores. | `FindingDrawer.jsx`, `RunPublicationController`, `PullRequestLifecycleService`, `DatabaseService` |
| Self-training global intelligence | Asynchronous failure reflection extracts anti-patterns from failed trajectories, while merged PR diff learning captures positive patterns into cross-repository rule intelligence. | `LlmService.reflectOnFailure()`, `LlmService.summarizeDiffForGlobalPattern()`, `RepositoryMemoryService`, `DatabaseService` |

## LangGraph4j orchestration

| Feature | Behavior | Implementation |
|---|---|---|
| State graph engine | Replaces `while` loops with a node-based state machine for agent execution. | `RemediationGraph` |
| Multi-agent architecture | 16-stage Star Topology orchestrating specialized code review sub-agents via a Supervisor LLM (configured to use a fast, typed System 1 model like JEV). Mandatory PLANNING stage generates a remediation plan before execution begins. | `MultiAgentRemediationGraph`, `SupervisorNode`, `WorkerAgentGraphFactory` |
| Context summarization | When conversation turns are dropped due to context budget limits, an LLM-based summary preserves key context instead of silently discarding it. Tool outputs are aggressively truncated before persistence. | `ConversationMemoryService`, `MemoryConfig.summarizationEnabled` |
| Docker sandbox | Optionally wraps agent shell commands (compile, test, lint) inside an isolated Docker container with no network access, read-only root filesystem, and configurable resource limits. | `AgentToolService.buildDockerCommand()`, `AgentConfig.sandboxEnabled` |
| PostgreSQL checkpoints | Graph state is persisted to PostgreSQL after every step for durability and recovery. | `DatabaseCheckpointSaver (formerly DuckDbCheckpointSaver)` |
| Idempotent tool execution | Prevents duplicate tool calls during graph replays using state-based tracking. | `ToolExecutionNode` |
| Human-in-the-loop workflow pause | Graph execution pauses before decision evaluation (via `interruptBefore("process_decision")`), checkpoints state in PostgreSQL, and waits for explicit user decision (`APPROVE_AND_CREATE_PR` or `REJECT`) before routing to `publish` or `reject`. | `CompileConfig.interruptBefore("process_decision")`, `RemediationWorkflowGraph`, `RemediationWorkflowService.decide()` |
| Workflow automation | End-to-end orchestration of workspace clone, agent remediation loop, verification, approval gate, PR creation, and webhook status reconciliation. | `RemediationWorkflowGraph`, `RemediationWorkflowService` |
| Unified audit workflow | Scan, chat, and remediation are orchestrated under a single unified graph timeline. | `AuditWorkflowGraph` |
| Graph visualization | Real-time graph execution timeline with node-level progress and tool details. | `GraphTimeline.jsx`, `graph_step` SSE events |
| Token streaming | Real-time chat token streaming via SSE to improve UX. | `ChatNode`, `ChatWidget.jsx` |

## Agent tools and verification

| Feature | Behavior | Implementation |
|---|---|---|
| Read file | Reads numbered lines; full reads are capped by configuration. | `read_file` |
| Search codebase | Literal substring search, optional simple glob, 30-result cap. | `search_codebase` |
| Apply exact patch | Requires one unique exact match and creates a backup first. | `apply_patch` |
| Compile | Maven `compile -q` with contained local repository, or Gradle `compileJava`. | `compile_project` |
| Target rescan | Runs all bundled rules on the selected file. | `rescan_file` |
| Tests | Maven all/specific tests or Gradle tests. | `run_tests` |
| Rollback | Restores latest timestamped file backup. | `rollback_file` |
| Secure file resolution | Rejects traversal, symlink components, non-real repository roots, and excluded search directories. | `AgentToolService`, `FilePatchService` |
| Process isolation hygiene | Removes credentials and injection variables; relocates home, temp, caches. | `ManagedProcessEnvironment` |
| Process and rule timeouts | Compilation, tests, model calls, and individual Semgrep rules have configurable limits. | `AgentConfig`, `ScannerConfig` |
| Output caps | Tool output, UI previews, durable messages, and approval diff are bounded. | agent, memory, and remediation services |
| Iteration budget | Agent has 15 rounds by default and prompt guidance for context/patch/verification phases. | `AgentConfig.maxIterations` |
| Repetition nudge | Repeating a tool four times prompts a change in approach. | `injectStuckNudgeIfNeeded()` |
| Late-patch nudge | Past two-thirds of the budget without a patch, the model is told to prioritize a fix. | same |
| Empty-response cap | Three consecutive empty model responses abort by default. | `maxConsecutiveEmptyResponses` |
| Timeout recovery | Timed-out calls add a concise retry instruction. | `CompletableFuture.get()` |
| Durable tool idempotency | Completed mutating calls with the same run/tool/arguments hash are not repeated. | `agent_steps` unique key |
| Verification gate | Final response is blocked until patch, build, target rescan, and tests satisfy policy. | `LlmService.runAgentLoop()` |
| Skip semantics | Unsupported Maven/Gradle build or tests count as explicit `SKIPPED` evidence. | tool prefixes and verification persistence |
| Run-change hashes | Before/after hashes and backup path are recorded. | `run_changes` |
| Structured trajectory | Tool, error, timeout, nudge, and final actions are tracked and logged. | trajectory list |

## Pull request publication, human-in-the-loop approval, and webhooks

| Automated webhook sync | GitHub App webhooks track PR state (`merged`, `closed`) and auto-update grouped finding statuses (e.g., instantly marking all file findings as `FIXED`). | `GitHubWebhookController` |
| Frontend webhook polling | UI polling seamlessly updates the PR timeline and displays the merged status without requiring a manual page refresh. | `useRemediationController` |
| Pull request preview | Shows exact diff, files, base, planned branch, evidence, and summary. | `ApprovalPreview` |
| PR payload digest | SHA-256 binds run, finding, base, branch, file hashes, and verification map prior to pushing. | canonical sorted map JSON |
| Diff cap | Approval diff is limited to 200,000 characters. | `MAX_DIFF_CHARS` |
| Structured decisions | Only `REJECT` or `APPROVE_AND_CREATE_PR`. | `RunDecisionRequest` |
| Fresh permission checks | User and App writes are revalidated at approval time. | `userCanPush()`, `installationCanPublish()` |
| Stale-base protection | Advanced base branch conflicts before Git writes. | current head vs `baseSha` |
| Workspace tamper protection | Changed hashes or dirty post-commit workspace conflict. | `changedFileHashes()`, `workspaceHead()` |
| Deterministic branch | Finding ID plus first eight run characters. | `branchName()` |
| Traceable bot commit | Bot author, run trailer, and approving login. | `GitHubProvider.publish()` |
| Idempotent publication | Existing approved commit or existing matching PR is reused. | commit trailer and PR search |
| Publication checkpoints | Commit, push, and PR progress are saved independently. | `PublicationCheckpoint`, `run_publications` |
| Retry publication | Approved failures resume without a second approval or duplicate commit. | `/retry-publish` |
| PR merge tracking | Signed webhook or restore reconciliation changes final status. | webhook and `getState()` |
| Positive-memory gate | Only merged PR creates approved memory. | `handlePullRequestEvent()` |
| Workspace cleanup schedule | Stale JGit workspaces from terminal runs are cleaned up hourly to prevent disk exhaustion. | `cleanupStaleWorkspaces()` |
| Co-located finding auto-marking | On PR merge, all findings in the patched file are marked FIXED, which may over-mark findings not explicitly addressed. | `handlePullRequestEvent()` |
| PR reconciliation polling | Frontend polls PR_OPEN status every 60 seconds to reconcile async webhook updates smoothly. | `App.jsx` |

## Persistence, memory, and recovery

| Feature | Behavior | Implementation |
|---|---|---|
| Transactional schema migration | Existing data is preserved while tables/columns and compatibility changes are applied. | schema version 3 |
| Pre-memory backup | Existing file DB receives one `.pre-memory-v1.bak` copy. | startup migration |
| PostgreSQL source of truth | Conversations, runs, evidence, changes, auth, and reports survive process restart. | `DatabaseService` |
| PostgreSQL-compatible GitHub persistence | Login, authorization refresh, repository discovery, and publication checkpoints update without binder or indexed-column errors. | Workflow upserts reuse `EXCLUDED.updated_at`; publication checkpoints use transactional update-then-insert |
| Stable thread identity | One user/repository/branch thread, plus optional user general thread. | unique client/repo index; server user ID used as client key |
| Owner checks | Thread and run access require the authenticated user. | controller and remediation checks |
| Message ordering | Per-thread monotonically increasing sequence. | transaction and unique index |
| Complete tool turns | Tool requests remain paired with their tool results during context compaction. | `ConversationMemoryService` |
| 12,000-token context | First two turns and newest complete turns are selected within budget. | approximate character/4 estimate |
| Secret redaction | AWS keys, bearer tokens, common assignments, and private keys are masked. | `MemoryRedactor` |
| Detail retention | Messages/tool detail expire after 30 days by default; durable outcomes remain. | hourly scheduled cleanup |
| Restart interruption | `ACTIVE` and `PUBLISHING` become `INTERRUPTED` at startup. | `markActiveRunsInterrupted()` |
| Safe resume | Applied after-hashes must match before model work resumes. | `RemediationWorkflowService.resume()` |
| Structured retrieval fallback | Metadata ranking remains when DuckDB FTS is unavailable. | `searchRepositoryMemories()` |
| Lexical FTS | Porter stemmer, English stopwords, lowercasing, accent stripping, BM25. | DuckDB FTS extension |
| Deterministic ranking | Fingerprint, rule, category/type, BM25, language/framework/file, confidence, use, recency. | weighted score |
| Retrieval health | Frontend thread restore receives `ftsAvailable`. | memory response |
| Forget scopes | Conversation/run detail and approved repository memory are separate operations. | memory DELETE endpoints |

## Observability and compatibility

| Feature | Behavior | Implementation |
|---|---|---|
| Method lifecycle logs | Controller/service calls log class, method, argument count, return type, and exception class without values. | AOP CGLIB interceptor |
| Redacted public errors | Common API exceptions return capped, redacted messages. | `ApiExceptionHandler` |
| Token usage tracking | Tracks AI token consumption and finding resolution via Micrometer and PostgreSQL with a user-level dashboard view. | `MeterRegistry`, `token_usage` table, `TokenUsageView` |
| Java virtual threads | Long scans, runs, output readers, and background index work avoid platform-thread blocking. | `Thread.startVirtualThread()` |
| Reactive streams | Long workflows return SSE through Reactor `Flux`. | WebFlux and unicast sinks |
| Jackson compatibility bridge | Jackson 2 mapper is supplied for libraries while Spring Boot 4 defaults differ. | `Jackson2Config` |
| Frontend SSE protocol | CRLF/LF, comments, chunking, multiline data, and EOF frames are supported. | `readJsonSse()` |
| Environment-specific Spring profiles | Local, development, and production settings can be separated without committing credentials. | base `application.yaml`, tracked `application-example.yaml`, ignored `application-{local,dev,prod}.yaml` files |
| Dev Container configuration | Standardized development container configured with Java 21, Node.js 24, and Python 3.12. | `.devcontainer/devcontainer.json` |
