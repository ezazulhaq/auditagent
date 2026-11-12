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
| `DatabasePreflightCleanup.java` | Startup pre-flight bean applying idempotent DDL schema migrations and purging legacy orphaned child records before Hibernate boots. |
| `GitHubAppConfig.java` | GitHub endpoints, secrets, cookies, workspace settings. |
| `Jackson2Config.java` | Jackson compatibility bean. |
| `JevProperties.java` | Binds `auditagent.jev` properties (master toggle, model, timeouts, retries, triage, and supervisor routing). |
| `LangChainModelConfig.java` | Tool-calling OpenAI model. |
| `LlmProperties.java` | Binds `auditagent.llm` properties (user keys requirement flag, execution mode, provider, and fallback OpenAI settings). |
| `MemoryConfig.java` | Retention, context, retrieval, content limits, and context summarization settings. |
| `RabbitMqConfig.java` | Declares RabbitMQ exchanges (`auditagent.direct`, `auditagent.dlx`), queues (`auditagent.scan.queue`, `auditagent.remediation.queue`), DLQ, routing keys, message converter, and container factory. |
| `ScannerConfig.java` | Validated Semgrep timeout and heartbeat settings. |
| `WebFluxConfig.java` | WebFlux multipart and codec in-memory size configuration. |

### `client.jev`

| File | Purpose |
|---|---|
| `JevClient.java` | REST client targeting OpenRouter `POST /api/v1/systemone` with timeout (3s), retry policy, and Micrometer metrics. |
| `JevQuestion.java` | Structured question model serializing `instructions` and `criteria` mapping for `choice`, `noul`, and `score` types. |
| `JevRequest.java` | Request payload binding model identifier, state prompt, and `questions` serialized as a key-value record/object. |
| `JevAnswer.java` | Deserialized answer supporting OpenRouter SystemOne fields (`choice`, `noul`, `score`, `confidence`, `probabilities`). |
| `JevResponse.java` | Response payload binding answers map and usage telemetry. |
| `JevUsage.java` | Token accounting model supporting `input_tokens`, `output_tokens`, and total tokens. |

### `controller`

| File | Purpose |
|---|---|
| `ApiExceptionHandler.java` | Safe 400/403/409/422 JSON errors. |
| `AuditController.java` | Scan, analyze, report, skill, and chat APIs. |
| `AuthController.java` | Session, OAuth start/callback, logout. |
| `GitHubRepositoryController.java` | Installed repositories, branches, and tracked GitHub issues. |
| `GitHubWebhookController.java` | Signed/replay-protected PR, issue, and issue comment webhook. |
| `ObservabilityController.java` | Aggregated user-level observability metrics (e.g. token usage). |
| `MemoryController.java` | Thread restore/forget and run resume/discard. |
| `NotificationController.java` | User notification retrieval, creation, and mark-read endpoints. |
| `ProjectUploadController.java` | Multipart upload scan, upload report restore, and export APIs. |
| `RunPublicationController.java` | Preview, structured decision, publication retry, active run lookup by vulnerability. |
| `UserLlmConfigController.java` | User Bring-Your-Own-Key LLM configuration endpoints (fetch, save, live test). |

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
| `TestConnectionRequest.java` | provider, API key, base URL, model name. |
| `TestConnectionResponse.java` | success flag, message, tested model. |
| `TestJevConnectionRequest.java` | API key, base URL, model name for SystemOne (TypeSafe Jev) connectivity test. |
| `UserLlmConfigDto.java` | user ID, provider, base URL, model names, masked API key, SystemOne/Jev config & system defaults, timestamps. |
| `UserLlmConfigRequest.java` | provider, API key, base URL, model name, supervisor model name, SystemOne/Jev preferences (enabled, model, URL, key). |
| `queue/ScanTaskPayload.java` | Immutable DTO carrying scan request parameters, authentication context, and priority for async worker execution. |
| `queue/RemediationTaskPayload.java` | Immutable DTO carrying remediation request parameters, target vulnerability, authentication context, and priority for async worker execution. |

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

### `entity`

| File | Purpose |
|---|---|
| `AppUser.java` | JPA aggregate root for authenticated users. |
| `JobEventLog.java` | JPA entity mapping `job_event_logs` table for durable real-time event streaming and worker telemetry. |
| `UserLlmConfig.java` | JPA entity mapping `user_llm_configs` table with encrypted primary & SystemOne/Jev API keys and model preferences. |

### `repository`

| File | Purpose |
|---|---|
| `JobEventLogRepository.java` | Spring Data JPA repository for persisting and querying `JobEventLog` events by job ID. |
| `UserLlmConfigRepository.java` | Spring Data JPA repository for `UserLlmConfig`. |

### `graph`

| File | Purpose |
|---|---|
| `AuditWorkflowGraph.java` | Unified end-to-end graph orchestrating scan, chat, remediation, and publication. |
| `DatabaseCheckpointSaver.java` | Native LangGraph4j state persistence backed by PostgreSQL / Supabase with thread-safe serialization and recovery. |
| `DuckDbCheckpointSaver.java` | Deprecated compatibility subclass extending `DatabaseCheckpointSaver`. |
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
| `AgentToolService.java` | LLM agent tool suite implementing segregated role interfaces (`WorkspaceInspector`, `WorkspacePatcher`, `WorkspaceBuildVerifier`, `WorkspaceRescanner`). |
| `ChatService.java` | Interface for AI conversational assistance, vulnerability triage, and command dispatching (ISP/DIP). |
| `ConversationMemoryService.java` | Durable LangChain message history, context summarization, and aggressive tool output truncation. |
| `DatabaseService.java` | Unified persistence facade delegating to Conversation, Remediation, User, and Scan domain data services. |
| `FilePatchService.java` | Backup, rollback, diff, and legacy patch helper. |
| `FindingFingerprint.java` | Stable normalized SHA-256 finding identity. |
| `FindingTriageService.java` | Evaluates Semgrep scan findings using TypeSafe Jev model via virtual threads to detect false positives, mocks, and low-confidence detections. |
| `GitHubApiClient.java` | OAuth/App/installation/repository/PR HTTP APIs, issue comments (`createIssueComment`), and PR diff retrieval (`getPullRequestDiff`). |
| `GitHubAuthService.java` | OAuth, tokens, sessions, CSRF, refresh. |
| `GitHubProvider.java` | JGit clone/diff/hash/commit/push/PR/cleanup. |
| `IssueLifecycleService.java` | GitHub App issue webhook event processor, triage matching, autonomous remediation, and issue thread tracking. |
| `LlmErrorClassifier.java` | Classifies and normalizes LLM provider failures (404 model not found, 401 unauthorized, 402 quota exhausted, 429 rate limit, 400 context length, connection refused), strips Java exception prefixes, extracts clean JSON provider error messages, and indicates configuration resolution flags. |
| `LlmService.java` | Core LLM provider delegation, tool-calling remediation loop, failure reflection (`reflectOnFailure`), and PR diff learning (`summarizeDiffForGlobalPattern`). Implements `ChatService`. |
| `LlmTokenTracker.java` | Dedicated component for recording token consumption to Micrometer metrics and database audit tables (SRP). |
| `ManagedProcessEnvironment.java` | Secret scrubbing, contained runtime directories, and validated scanner-temp override. |
| `ManagedProcessRunner.java` | Concurrent stream draining, heartbeat, deadline, process-tree termination, and short per-scan temp lifecycle. |
| `MemoryRedactor.java` | Secret masks and caps. |
| `PostgresKnowledgeBaseService.java` | Native PostgreSQL/Supabase full-text search (`tsvector`/`tsquery`), candidate retrieval, and domain ranking weights for approved remediation memory. |
| `DuckDbKnowledgeBaseService.java` | Deprecated compatibility subclass extending `PostgresKnowledgeBaseService`. |
| `PullRequestLifecycleService.java` | Pull request webhook events, and vulnerability state transitions. |
| `PullRequestProvider.java` | Pull-request abstraction. |
| `PdfReportRenderer.java` | Paginated enterprise PDF generation through Apache PDFBox. |
| `ProjectUploadService.java` | Project upload scan orchestration, persistence, and report export. |
| `queue/TaskProducerService.java` | AMQP task producer routing scan and remediation jobs to RabbitMQ direct exchanges with priority headers. |
| `realtime/EventBroadcastService.java` | Service broadcasting job events to PostgreSQL durable log and Supabase Realtime / WebSocket subscribers. |
| `RemediationWorkflowService.java` | GitHub-native remediation workflow orchestration and async worker task execution. |
| `reporting/ReportFormat.java` | Enum defining supported report export targets (`MARKDOWN`, `HTML`, `PDF`). |
| `reporting/ReportExportContext.java` | Encapsulates immutable scan findings and metadata parameters for report generation. |
| `reporting/SecurityMetricsCalculator.java` | Calculates severity distributions, counts, and risk posture assessments (SRP). |
| `reporting/ReportExporter.java` | Strategy interface for exporting audit assessment reports to target formats (OCP). |
| `reporting/MarkdownReportExporter.java` | Markdown export strategy implementing `ReportExporter`. |
| `reporting/HtmlReportExporter.java` | Interactive HTML export strategy implementing `ReportExporter`. |
| `reporting/PdfReportExporter.java` | PDF export strategy implementing `ReportExporter` via `PdfReportRenderer`. |
| `reporting/ReportGenerator.java` | High-level reporting contract implemented by `ReporterService` (DIP). |
| `ReporterService.java` | Reporting facade coordinating format-specific exporters (`MarkdownReportExporter`, `HtmlReportExporter`, `PdfReportExporter`) via `ReportGenerator`. |
| `RepositoryAccessService.java` | Centralized repository authorization check. |
| `RepositoryMemoryService.java` | Renders approved retrieval into prompt. |
| `scanner/SecurityScanner.java` | Polymorphic strategy interface for vulnerability and security scanners (OCP/DIP). |
| `scanner/ScannerRegistry.java` | Spring component registry dynamically resolving `SecurityScanner` beans by engine name. |
| `ScannerService.java` | Default Semgrep CLI implementation of `SecurityScanner`. |
| `ScanExecutionException.java` | Terminal scanner execution/output failure. |
| `ScanOrchestratorService.java` | GitHub-native scan orchestration and async worker task execution consuming `SecurityScanner`. |
| `ScanTimeoutException.java` | Distinct scanner deadline failure. |
| `SecurityConceptCatalog.java` | Categories, aliases, framework and query metadata. |
| `SkillManagerService.java` | Dynamic skill discovery/parsing. |
| `SourceControlProvider.java` | Repository/workspace abstraction. |
| `storage/SupabaseStorageService.java` | Service providing object storage operations (upload, download, signed URL) for reports and artifacts via Supabase Storage REST API with local filesystem fallback. |
| `TokenCipher.java` | AES-GCM OAuth encryption. |
| `tool/WorkspaceInspector.java` | Role interface for reading files, search queries, and SHA-256 hash checks (ISP). |
| `tool/WorkspacePatcher.java` | Role interface for applying patches and rolling back files (ISP). |
| `tool/WorkspaceBuildVerifier.java` | Role interface for compiling projects, running tests, and linting (ISP). |
| `tool/WorkspaceRescanner.java` | Role interface for rescanning individual files and full static analysis (ISP). |
| `UserLlmClientFactory.java` | Factory dynamically instantiating and caching user-scoped `ChatModel` (Spring AI) and `OpenAiChatModel` (LangChain4j) clients. |
| `UserLlmConfigService.java` | Service managing user LLM configurations, AES-GCM encryption, key masking, and connectivity testing. |
| `UserLlmConfigurationException.java` | Domain exception thrown when required user LLM configuration is missing (HTTP 422 `LLM_KEY_REQUIRED`). |
| `ZipArchiveExtractor.java` | Safe archive validation, extraction, ZipSlip protection, and directory cleanup. |

### `service/data`

| File | Purpose |
|---|---|
| `ConversationDataService.java` | Domain data service managing `MemoryThread` aggregate root and `MemoryMessage` collections. |
| `RemediationDataService.java` | Domain data service managing `AgentRun` aggregate root, steps, verification results, changes, checkpoints, and repository memories. |
| `UserDataService.java` | Domain data service managing `AppUser` aggregate root, user sessions, GitHub authorizations, OAuth state, and notifications. |
| `ScanDataService.java` | Domain data service managing `ManagedRepository` aggregate root, scan snapshots, scan history stats, reports, vulnerabilities, webhooks, tracked issues, and token usage. |

### `worker`

| File | Purpose |
|---|---|
| `ScanTaskConsumer.java` | RabbitMQ AMQP listener handling asynchronous repository scanning tasks with prefetch limits and dead-letter error handling. |
| `RemediationTaskConsumer.java` | RabbitMQ AMQP listener handling asynchronous multi-agent remediation tasks with concurrency control and dead-letter error handling. |

## Frontend production files

| File | Purpose |
|---|---|
| `src/main.jsx` | React DOM bootstrap. |
| `src/context/ApiContext.jsx` | Dependency inversion provider (`useApi`) for injecting API client instances. |
| `src/context/AuthContext.jsx` | Authentication session, CSRF, login/logout, and LLM onboarding state provider (`useAuth`). |
| `src/context/WorkspaceContext.jsx` | Repository discovery, branch selection, and scan target configuration provider (`useWorkspace`). |
| `src/context/ModalContext.jsx` | Centralized modal and dialog manager (`useModals`, `MODAL_TYPES`) implementing OCP. |
| `src/context/AuditContext.jsx` | Scan execution, remediation workflows, finding inventory, and persistent memory provider (`useAudit`). |
| `src/context/AppProviders.jsx` | Context composition root assembling domain providers in dependency hierarchy. |
| `src/test/test-utils.jsx` | Test harness utility providing `renderWithProviders` with mockable `ApiProvider`. |
| `src/App.jsx` | Top-level orchestration and layout. |
| `src/index.css` | Tailwind import and global helpers. |
| `src/App.css` | Unused legacy starter styles. |
| `src/types.js` | JSDoc API/state types. |
| `src/api/auditApi.js` | Credentialed API/SSE client and result normalization. |
| `src/api/sse.js` | Robust JSON SSE parser. |
| `src/components/AppHeader.jsx` | Brand, scan summary, identity, logout. |
| `src/components/CopyButton.jsx` | Reusable clipboard copy button supporting multiple sizes, accessible labels, and feedback states. |
| `src/components/NotificationDropdown.jsx` | Bell icon notification dropdown with unread badge and real-time updates. |
| `src/components/ModalManager.jsx` | Centralized modal, drawer, and confirmation dialog container consuming `useModals()`. |
| `src/features/navigation/workspaceTabs.jsx` | Declarative Workspace Tab Registry implementing Open/Closed Principle (OCP). |
| `src/features/navigation/WorkspaceContainer.jsx` | Dynamic workspace tab router and view renderer driven by the tab registry. |
| `src/features/observability/TokenUsageLayout.jsx` | User-level UI displaying aggregated token usage and observability metrics by repository name and model. |
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
| `src/features/findings/hooks/useFindingsFilter.js` | Custom hook encapsulating finding filters, search, grouping, and selection logic (SRP). |
| `src/features/findings/constants/findingStyles.js` | Styling and status formatting utilities for finding severities and lifecycle states. |
| `src/features/findings/components/FindingsToolbar.jsx` | Search input, lifecycle status dropdown, severity pills, and view mode switcher. |
| `src/features/findings/components/FindingRow.jsx` | Individual finding row component with severity badge, false-positive badge, status badge, and location. |
| `src/features/findings/components/FileGroup.jsx` | Collapsible directory/file group header and findings list. |
| `src/features/findings/components/BulkActionBar.jsx` | Floating batch progress and bulk actions toolbar for batch analysis/ignoring. |
| `src/features/issues/IssuesView.jsx` | Tracked GitHub issues view displaying webhook/label-triggered issues, remediation status, finding links, PR links, and search/filtering. |
| `src/features/profile/LlmConfigForm.jsx` | Modular form coordinating LLM provider credentials, model selection, SystemOne decision engine, and live connection testing. |
| `src/features/profile/constants/llmProviders.js` | Metadata and model presets for supported LLM providers (OpenRouter, OpenAI, Groq, DeepSeek, Ollama, OpenAI-compatible). |
| `src/features/profile/hooks/useLlmConfigForm.js` | Custom hook encapsulating LLM configuration fetching, form state, connection tests, and persistence (SRP). |
| `src/features/profile/components/ProviderSelector.jsx` | Radio cards grid for selecting LLM provider. |
| `src/features/profile/components/SystemOneSettings.jsx` | Non-autoregressive SystemOne (TypeSafe Jev) fast decision engine configuration card. |
| `src/features/profile/LlmOnboardingModal.jsx` | Unclosable onboarding modal displayed on login when user credentials are required and unconfigured. |
| `src/features/profile/UserProfileModal.jsx` | User profile modal accessible from header menu for inspecting identity and updating LLM credentials. |
| `src/features/remediation/FindingDrawer.jsx` | Coordinated remediation drawer presenting code diffs, AI timeline, and approval flow. |
| `src/features/remediation/constants/remediationConstants.js` | Remediation stage timeline step definitions. |
| `src/features/remediation/components/RemediationTimeline.jsx` | Multi-stage progress timeline tracking finding lifecycle from detection to GitHub merge. |
| `src/features/remediation/components/VulnerableCodeViewer.jsx` | Syntax-highlighted code snippet viewer with line indicators and clipboard copy. |
| `src/features/remediation/components/ApprovalPackageViewer.jsx` | Verified fix package viewer with changed files, verification evidence, and interactive DiffViewer. |
| `src/features/remediation/components/RemediationDecisionBar.jsx` | Bottom action bar handling diff review, approve & create PR, rejection with feedback, and retry. |
| `src/features/remediation/useRemediationController.js` | Run/publication browser state, SSE event dispatching, failed run hydration, and background PR polling. |
| `src/features/report/ReportView.jsx` | Authenticated PDF viewer plus PDF/Markdown downloads, loading, retry, and Blob URL cleanup. |
| `src/features/report/WorkspaceTabs.jsx` | Main tab navigation (Dashboard, Findings, Issues, Audit Report) with count badges. |
| `src/features/scan/ScanPanel.jsx` | Sidebar panel coordinating audit scope selection, ZIP archive upload dropzone, and progress tracking. |
| `src/features/scan/components/RepoBranchSelector.jsx` | Repository/branch selection dropdowns, scanner engine, force fresh rescan toggle, and scan trigger. |
| `src/features/scan/components/UploadDropzone.jsx` | Drag-and-drop ZIP archive upload dropzone with file size validation and project naming. |
| `src/features/scan/components/ScanProgressTracker.jsx` | Real-time progress bar, wall-clock timer, and stage checklist. |
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
| `.agents/skills/git-feature-workflow/SKILL.md` | Developer assistant skill for Mandatory Step 0 Pre-Flight Reflex, branch isolation, grouped commits, and release workflow. |
| `.agents/skills/systematic-debugging/SKILL.md` | Developer assistant skill for structured root-cause debugging. |
| `.agents/skills/frontend-design/SKILL.md` | Developer assistant skill for distinctive UI design. |
| `.agents/rules/git-commit-policy.md` | Developer workspace rule: Mandatory Step 0 Pre-Flight Reflex, branch protection, and explicit user approval prior to git commit and push. |
| `.agents/rules/pr-merge-policy.md` | Developer workspace rule: Pull Requests must strictly target and merge into dev only. |
| `.agents/rules/solid-principles-policy.md` | Developer workspace rule: Strict adherence to SOLID principles (Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion) across all future frontend and backend architectural additions. |
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

| `.githooks/pre-commit`                          | Automated pre-commit hook enforcing Protected Branch Guard ('dev', 'master', 'main') and Documentation Guard ('doc/*.md'). |
| `scripts/install-git-hooks.ps1`                 | Configures repository hooks path to `.githooks` for PowerShell/Windows users. |
| `scripts/install-git-hooks.sh`                  | Configures repository hooks path to `.githooks` and executable bit on Unix-like systems. |
| `.devcontainer/devcontainer.json`               | Temurin Java 21, Node.js 24 & Python 3.12 development container. |
| `release_notes/*.md`                            | Versioned release notes tracking product, tooling, and infrastructure updates (v2.0.0, v2.0.1, v2.1.0, v2.1.1, v2.2.0, v2.2.1, v2.3.0, v2.3.1, v2.3.2, v2.3.3, v2.4.0, v2.4.1, v2.5.0, v2.6.0). |

## Test file map

### Backend

`AuditagentApplicationTests`, `ApiSecurityFilterTest`, `AuditControllerReportExportTest`, `GitHubWebhookControllerTest`, `GitHubRepositoryControllerTest`, `IssueLifecycleServiceTest`, `AgentToolServiceSecurityTest`, `ConversationMemoryServiceTest`, `DatabaseMemoryServiceTest`, `FindingTriageServiceTest`, `GitHubAuthServiceTest`, `GitHubProviderIntegrationTest`, `JevClientTest`, `LlmErrorClassifierTest`, `LlmServiceAgentLoopTest`, `ManagedProcessRunnerTest`, `ProjectUploadControllerTest`, `ProjectUploadServiceTest`, `RabbitMqTaskQueueTest`, `RemediationWorkflowPreparationTest`, `RunPublicationControllerTest`, `ScanOrchestratorReportExportTest`, `ScanOrchestratorServiceTest`, `ReporterServiceTest`, `ScannerServiceTest`, `SupabaseStorageServiceTest`, `SupervisorNodeTest`, `TokenCipherTest`, `UserLlmClientFactoryTest`, `UserLlmConfigControllerTest`, `UserLlmConfigServiceTest`, and `ZipArchiveExtractorTest`.

### Frontend

Tests cover App, API, SSE, scan state/panel/wall-clock timing, thread restore, remediation controller/drawer, findings, dashboard (`DashboardView.test.jsx`), tracked issues, report, and user LLM configuration dialogs (`LlmConfigForm.test.jsx`, `LlmOnboardingModal.test.jsx`, `UserProfileModal.test.jsx`).

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

**AuditAgent Mode** — process execution profile configured via `AUDITAGENT_MODE=all|web|worker` (or `auditagent.mode`), allowing single-process development or decoupled web ingress and queue worker instances.

**Agent run** — one durable attempt to remediate one finding.

**BM25** — lexical ranking algorithm formerly used by DuckDB FTS; now replaced by PostgreSQL native text search ranking (`ts_rank_cd`).

**BYOK (Bring Your Own Key)** — architecture where each user provides their own LLM API credentials and selects their preferred provider/model, stored encrypted at rest via AES-GCM and scoped to their server identity.

**Base SHA** — exact Git commit scanned and used to prepare a remediation.

**CSRF** — attack that causes a signed-in browser to send an unwanted mutation; prevented with a session-bound header token.

**Dead Letter Queue (DLQ)** — RabbitMQ destination queue (`auditagent.dlq`) collecting poison-pill or failed tasks routed via dead-letter exchange `auditagent.dlx`.

**Finding** — one Semgrep result represented by `Vulnerability`.

**Finding fingerprint** — stable SHA-256 of normalized rule ID, path, and vulnerable code.

**FTS** — full-text search. Now powered by PostgreSQL / Supabase native `tsvector`, `plainto_tsquery`, and GIN indexing over `repository_memories`.

**`getPullRequestDiff`** — `GitHubApiClient` method retrieving PR diffs via `application/vnd.github.v3.diff` for self-training.

**Global remediation pattern** — cross-repository positive remediation pattern or negative anti-pattern keyed by Semgrep rule ID in `global_remediation_patterns`.

**Installation token** — short-lived GitHub App credential used for bot repository operations.

**Managed repository** — server-resolved GitHub repository from an installation.

**Managed workspace** — isolated clone below the configured workspace root.

**Memory thread** — stable conversation keyed to user and repository/branch.

**OAuth user token** — GitHub credential used for repository discovery and user permission checks.

**OpenRouter** — unified LLM gateway providing standardized API access to diverse frontier and open-weight models, configured as the default provider for AuditAgent.

**Positive repository memory** — merged, approved remediation pattern eligible for future prompt retrieval.

**PR_OPEN** — remediation was published, but merge is not yet confirmed.

**Prefetch Count** — AMQP channel flow-control limit configured on workers (`prefetch = 1` for remediation, `prefetch = 2` for scans) to enforce backpressure.

**Publication digest** — SHA-256 binding of run/finding/base/branch/file hashes/verification evidence.

**RabbitMQ Task Queue** — durable message broker managing asynchronous scanning and multi-agent remediation job dispatching with priority levels.

**Recovery checkpoint** — persisted phase/status/iteration/Git hashes enabling safe retry or resume.

**`reflectOnFailure`** — `LlmService` method asynchronously generating anti-pattern warnings from failed agent trajectories when hitting maximum iterations.

**Report key** — `github:<repositoryId>:<branch>`, used to scope report, finding, and repository memory.

**`saveGlobalPatternTx`** — `DatabaseService` transactional method persisting, accumulating, and capping positive and negative patterns in `global_remediation_patterns`.

**SSE** — server-sent events, used to stream JSON progress from POST workflows.

**`summarizeDiffForGlobalPattern`** — `LlmService` method asynchronously extracting abstract positive remediation patterns from merged PR diffs.

**Supabase Storage** — S3-compatible object storage layer backed by Supabase used for durable PDF/Markdown audit reports and artifacts.

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
