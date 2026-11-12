# Frontend guide

## Technology and build

The frontend uses React 19, Vite 5, Tailwind CSS 4, Lucide React, Vitest, Testing Library, and jsdom. It is an ES module application.

- Development: Vite port 5173 with `/api` proxy.
- Production build: output replaces `src/main/resources/static`.
- Entry: `frontend/src/main.jsx`.
- Global Tailwind, semantic design tokens (`--surface-*`, `--border-*`, `--text-*`, `--accent-*`, `--sev-*`), typography pairing (Plus Jakarta Sans for UI + JetBrains Mono for code/metrics), reusable surface/field/action primitives, focus treatment, reduced-motion behavior, and scrollbar/diff helpers: `index.css`.
- Fonts imported in `frontend/index.html`: Plus Jakarta Sans (400-800) and JetBrains Mono (400-700) with preconnect links.
- `App.css` contains older starter-style rules and is not imported by the current `App.jsx`.

## Context Architecture & Composition Root

The frontend strictly implements **SOLID principles** using a Context-Driven Domain Architecture, eliminating monolithic "god-component" antipatterns and deep prop-drilling:

```text
App (Backward-compatible entrypoint)
  v
AppProviders (Composition Root)
  +-- ApiProvider (DIP: Injects auditApi client, consumed via useApi())
  +-- AuthProvider (SRP: Session, CSRF, login/logout, LLM onboarding state via useAuth())
  +-- WorkspaceProvider (SRP: Repository discovery, branch selection, configuration via useWorkspace())
  +-- ModalProvider (SRP/OCP: Centralized dialogs, confirmation modals, drawer state via useModals())
  +-- AuditProvider (SRP: Scan execution, remediation workflows, findings, memory threads via useAudit())
        v
AppShell (Lean layout orchestrator)
  +-- AppHeader
  +-- ScanPanel (Collapsible sidebar, RepoBranchSelector, UploadDropzone, ScanProgressTracker)
  +-- WorkspaceContainer (OCP: Declarative workspace tab router consuming WORKSPACE_TABS)
  +-- ModalManager (OCP: Centralized dialog & overlay manager)
```

### Domain Context Hierarchy

1. **`ApiContext` (`src/context/ApiContext.jsx`)**:
   Provides the dependency-injected API client (`useApi()`) to the entire component tree, satisfying the **Dependency Inversion Principle (DIP)** and enabling seamless mocking for unit tests (**Liskov Substitution Principle (LSP)**).

2. **`AuthContext` (`src/context/AuthContext.jsx`)**:
   Encapsulates user identity, OAuth session verification (`/api/auth/session`), CSRF tokens, login/logout transitions, and AI credential onboarding state (`needsLlmOnboarding`).

3. **`WorkspaceContext` (`src/context/WorkspaceContext.jsx`)**:
   Encapsulates installed GitHub repository discovery, branch selection, default branch fallback logic, and scan scope configuration (`config`, `updateConfig`).

4. **`ModalContext` (`src/context/ModalContext.jsx`)**:
   Centralizes visibility and payloads for all modal dialogs, drawers, sidebars, and confirmation alerts (`activeModal`, `confirmAction`, `isTourOpen`, `isViewingDocs`, `isViewingTokenUsage`, `isProfileModalOpen`). Implements priority-based Escape key dismissals (`handleTopLevelClose`).

5. **`AuditContext` (`src/context/AuditContext.jsx`)**:
   Bridges scan lifecycle (`useScanController`), autonomous remediation (`useRemediationController`), AI chat assistant (`useChatController`), and durable memory thread synchronization (`useMemoryThread`). Provides memoized finding statistics, severity counts, and bulk action dispatches.

6. **`AppProviders` (`src/context/AppProviders.jsx`)**:
   Assembles the providers in strict dependency order (`ApiProvider` → `AuthProvider` → `WorkspaceProvider` → `ModalProvider` → `AuditProvider`).

### Declarative Extension Points (OCP)

- **Workspace Tab Registry (`src/features/navigation/workspaceTabs.jsx`)**:
  Tabs are declared as objects in `WORKSPACE_TABS` (id, label, icon, badge calculator, and view renderer). `WorkspaceContainer.jsx` dynamically renders the active tab. Adding new operational views requires registering an item in the array without modifying `App.jsx` or layout containers.
- **Modal Manager (`src/components/ModalManager.jsx`)**:
  All overlays (`DocsView`, `FindingDrawer`, `ChatWidget`, `TokenUsageLayout`, `FindingsModal`, `ConfirmDialog`, `OnboardingTour`, `UserProfileModal`, `LlmOnboardingModal`) are mounted within a dedicated modal root driven by `useModals()`, isolating dialog markup from workspace layout panels.

### Startup

1. `AuthProvider` calls `/api/auth/session`.
2. `AppShell` displays `<SkeletonApp />` while loading, `<LoginPage />` if unauthenticated, or the authenticated workspace shell.
3. If `authSession.userKeysRequired && !authSession.llmConfigured`, `ModalManager` renders the mandatory, unclosable `<LlmOnboardingModal />`.
4. When authenticated, `WorkspaceProvider` loads installed repositories and branches.
5. When `repositoryId:branch` selection stabilizes, `AuditProvider` restores thread memory and cached report in parallel.
6. Chat and remediation states hydrate from durable storage.

### Selection race control

`repositoryKeyRef` in `WorkspaceContext` and `AuditContext` prevents duplicate restore calls for the same repository/branch. Request abort controllers and monotonic request IDs prevent stale asynchronous responses from winning.

## API client

`createAuditApi()` is dependency-injectable for tests.

- Always uses `credentials: include`.
- Caches the CSRF token only in module memory after session discovery.
- Adds CSRF only to non-safe methods.
- Converts non-2xx replies into capped error messages.
- Normalizes fresh, cached, and restored reports into the same camelCase shape.
- Requires a terminal `complete` or `cached` event for scan success.
- Converts a terminal scan `error` event into an exception carrying the backend `code` and exact safe message.

## Progressive Web App (PWA) & Service Worker

The frontend is packaged as a PWA via `vite-plugin-pwa` with automatic worker updates.
- Workbox is configured with `navigateFallbackDenylist: [/^\/api/]` to ensure browser navigations to `/api/auth/github/login` and server redirects to `/api/auth/github/callback` bypass the service worker's SPA HTML cache and hit the backend directly.
- All non-API SPA navigation requests continue to resolve to the cached `index.html` shell.

### SSE parser

The fetch-based parser supports:

- LF and CRLF frame boundaries;
- optional space after `data:`;
- comments;
- multiple `data:` lines;
- arbitrary network chunks;
- a final frame without trailing blank line;
- clear protocol errors for invalid JSON or missing readable body.

It does not use browser `EventSource` because protected mutations need POST bodies and CSRF headers.

## Feature controllers

### `useMemoryThread`

- Maintains stable thread ID and repository/branch key.
- Restores via `POST /memory/threads`.
- Maps durable roles: `USER` → user, `AI` → agent, other → system.
- Preserves server timestamps as localized display time.
- Ignores old restore results using request IDs.
- Forget deletes the current thread and immediately creates/restores a new one.

### `useScanController`

- Uses a reducer for atomic scan transitions.
- Validates repository and branch before calling the API.
- Ensures a thread exists.
- Streams progress messages and surfaces terminal scan errors.
- Ignores stale events.
- Preserves the previous successful report while a later scan starts or fails.
- Supports cancellation through `AbortController`.
- Records start and finish timestamps; Semgrep heartbeat progress never resets them.

### `useRemediationController`

Tracks:

- selected finding;
- analysis and publication activity;
- active run and run ID;
- automated PR publication progress state;
- PR preview data;
- grouped finding status tracking;
- batch analysis state (`batchState`: active, total, completed, failed, currentVulnId);
- active analyzing status per vulnerability (`isAnalyzingMap`).

It:

- hydrates recoverable state;
- clears stale remediation when a new scan begins;
- starts analysis and consumes run-bound events;
- executes multi-finding concurrent batch analysis (`analyzeBatch`) with cancellation (`cancelBatch`);
- loads the current preview;
- sends the exact vulnerability and digest in a decision;
- refreshes the report after decisions/publication;
- retries publication;
- resumes interrupted work;
- discards unpublished work.

### `useChatState`

Maintains messages, input, open/closed state, and unread count. Non-user messages increment unread while closed. It supplies restore, reset, remove, and toggle operations.

### `useChatController`

Handles three paths:

1. Local `scan`.
2. Local exact finding commands.
3. Backend model chat.

For model responses it removes the temporary "Agent thinking..." message, adds the response, and processes scan/fix tags. Scan takes precedence if both are present.

## Components

### `LoginPage`

Shows a responsive two-column enterprise access experience with product capabilities, protected-access context, server configuration warning, session error, GitHub button, and the no-write-before-approval promise.

### `AppHeader`

Shows brand and enterprise context, secure-session status, responsive repository scan summary, high/medium counts, GitHub avatar/name, and sign out.

### `ScanPanel`

Coordinates audit scope selection, file upload dropzone, and real-time progress tracking. Decomposed into three focused sub-components adhering to SRP:
- `RepoBranchSelector`: Handles installed repository and branch selection, scanner engine configuration, force-rescan toggle, and scan launch.
- `UploadDropzone`: Provides drag-and-drop file uploading and validation for `.zip` project archives (max 50MB), filename and size card with remove action, editable project name input, and ZIP scan initiation.
- `ScanProgressTracker`: Displays real-time progress bar, wall-clock timer, and stage checklist (`SCAN_STEPS`) with explicit terminal error handling.
- Expand/collapse rail state is controlled via `ModalContext` (`toggleSidebar`), rendering an icon-only collapse bar on desktop.

### `DashboardView`

Shows guided empty-state steps or executive metric cards, severity distribution, action-oriented risk posture, repository context, and scan-execution metadata. All values are derived from the existing report and findings state. All memoization hooks (`fileVulnerabilityCounts`, `cards`) are called unconditionally at the top of the component prior to `metadata` null-checks, ensuring invariant React hook execution across repository selection state transitions.

### `DocsSidebar` and `DocsMainPanel`

Provide the in-app documentation viewing experience. `DocsSidebar` fetches the list of available documentation files via `auditApi`, formats their filenames for display, and handles selection. `DocsMainPanel` fetches the raw markdown content of the selected document and renders it using `react-markdown` with `@tailwindcss/typography` styles applied.

### `FindingsView`

Displays and manages the security findings inventory. Decomposed into focused modules under SRP and ISP:
- `useFindingsFilter`: Custom hook encapsulating search query, severity, status, grouping mode (`list` vs `grouped`), and multi-selection logic.
- `FindingsToolbar`: Renders search input, lifecycle status dropdown, severity badge pills, view mode switcher, and filter reset.
- `FindingRow`: Memoized individual row component presenting severity badges, false positive triaged tags, status badge, code location, and row selection checkbox.
- `FileGroup`: Collapsible directory/file group header with select-all for files.
- `BulkActionBar`: Floating batch analysis progress bar with cancel action, or selection toolbar providing bulk "Analyze" and "Ignore" actions.

### `WorkspaceTabs`

Sticky workspace tab navigation bar positioned at the top of the main panel providing seamless switching between **Dashboard**, **Findings**, **Issues**, and **Audit Report**. Driven by `WorkspaceContainer` and the extensible `WORKSPACE_TABS` registry (OCP).

### `IssuesView`

Presents an executive and operational dashboard for GitHub issues tagged for autonomous remediation via webhooks, labels (`auditagent-fix`), or `@auditagent` mentions. Provides:

- Metrics row tracking total tagged issues, PR open count, fixed issues, in-progress analyses, and issues needing attention.
- Search input filtering issues across title, issue number, linked finding ID, labels, and status explanation.
- Status dropdown filtering by lifecycle status (`ANALYZING`, `AWAITING_APPROVAL`, `PR_OPEN`, `FIXED`, `UNMATCHED`, `FAILED`, `DISCARDED`).
- Interactive issue cards displaying GitHub issue numbers linking directly to GitHub, title, tags, status badge with animated spinners during active remediation, pull request badges with direct links to created PRs, and clickable target finding badges that open the finding drawer.
- Zero-state guidance outlining how developers can tag issues to trigger autonomous remediation.

### `FindingDrawer`

Presents finding details, code diffs, AI timeline, and approval flow. Decomposed under SRP into:
- `RemediationTimeline`: 5-step lifecycle progress tracker (Detected, Analyzing & fixing, Verified & awaiting approval, Pull request created, Fix merged).
- `VulnerableCodeViewer`: Syntax-highlighted code snippet viewer with line indicators and clipboard copy.
- `ApprovalPackageViewer`: Displays verified fix details, base commit, planned branch, changed files list, verification evidence, and `DiffViewer`.
- `RemediationDecisionBar`: Sticky bottom action bar handling diff review, approve & create PR, rejection with reason, and retry publishing. Features a non-blocking `isLoadingPreview` spinner while loading verified diffs, and retains loaded diff previews across background thread reconciliation polling intervals so approval buttons never flicker or revert to the review prompt prematurely.
- Quick-copy finding ID: A dedicated copy button beside the finding ID in the drawer header enables one-click copying of the identifier to the clipboard.
- Drawer width is adjustable via a draggable edge resizer on desktop.

### `Profile & LLM Configuration Components`

Located in `frontend/src/features/profile/`:

- **`LlmConfigForm`**: Modular form coordinating LLM provider credentials, model selection, SystemOne decision engine, and live connection testing. Decomposed into:
  - `useLlmConfigForm`: Custom hook encapsulating loading, state changes, testing, and saving.
  - `ProviderSelector`: Radio card grid for selecting between OpenRouter, OpenAI, Groq, DeepSeek, Ollama, and OpenAI-compatible providers.
  - `SystemOneSettings`: Non-autoregressive SystemOne (TypeSafe Jev) fast decision engine configuration card.
  - `llmProviders.js`: Provider metadata and model presets.
- **`LlmOnboardingModal`**: A non-closable modal displayed immediately upon login if user keys are required but not yet configured, preventing users from attempting AI workflows with missing credentials.
- **`UserProfileModal`**: Accessible from the user avatar dropdown menu in `AppHeader` ("Profile & LLM Keys"). Allows users to inspect their active GitHub identity, review their masked API key (`sk-or-••••••••3a8f`), update configurations, test connections, and save updates with automatic cache refresh.

### `TokenUsageLayout`

Presents AI observability and token consumption data from `/api/observability/tokens` in an aggregated table grouped by repository full name and model. Shown via the `AppHeader` Activity/Observability action.

### `ReportView`

Loads the latest PDF through the credentialed `auditApi.reportArtifact()` client and displays it in the framed artifact surface. The header adapts from stacked mobile controls to a compact desktop action row and provides **Open PDF**, **Download PDF**, and **Download Markdown**. Loading, terminal error, retry, empty, and disabled-action states are explicit. The PDF Blob URL is revoked when the component reloads or unmounts so report bytes are not retained by a stale view. Markdown is fetched independently only when requested.

### `ChatWidget`

Shows durable messages, recovery actions, PR link, memory deletion controls, input, unread badge, automatic scrolling, and explicit approval-boundary guidance. It is a floating desktop assistant and a full-width mobile bottom sheet, and supports Escape dismissal.

## UI state rules for future changes

- Do not derive authorization from React state.
- Never store GitHub tokens or session secrets in `localStorage`/`sessionStorage`.
- Preserve report normalization so cached/fresh/restore results are interchangeable. `report_available` is authoritative for new payloads, with legacy `html_report` presence used only as a compatibility fallback.
- React strictly to `pr_created`, `complete`, and automated transitions.
- The UI triggers `refreshReport` (or API polling) when the run is marked as `PR_OPEN` to instantly catch webhook updates and group status changes.
- Keep chat publication-independent.
- Refresh the report after any lifecycle transition that changes finding status.
- Add explicit UI for any new run status; otherwise restored work may be impossible to operate.

## Accessibility and usability notes

The code supplies labels for search, filters, progress, close, chat, send, sign out, and the report iframe. Buttons have disabled states, errors use alert roles in key panels, keyboard focus has a consistent visible treatment, the finding drawer receives initial close-button focus, open overlays respond to Escape, and reduced-motion preferences are honored. Future work should add complete focus trapping, restore trigger focus after every overlay closes, richer live status announcements, and a formal accessibility audit.

## Interaction performance and responsiveness (INP)

AuditAgent applies strict interaction-responsiveness optimizations to maintain instant (sub-16ms) click reaction times across all buttons, drawers, and tabs:

- **Non-blocking syntax highlighting**: In `FindingDrawer` (`VulnerableCodeViewer`) and `DiffViewer`, Prism syntax highlighting (which bundles 290 grammars and takes hundreds of milliseconds to execute) is never evaluated synchronously during drawer open. Frame 1 immediately paints clean monospace code inside a `<pre><code>` block, while Prism tokenization is deferred via `requestAnimationFrame`. This eliminates up to 3 seconds of synchronous main-thread blocking when opening findings or analyzing code.
- **Scan timer isolation**: The 1-second elapsed timer hook (`useScanElapsedSeconds`) is encapsulated directly inside `ScanPanel.jsx` rather than residing at the root `App.jsx` level. Active scans update only the elapsed indicator in the scan rail without triggering full-application re-render cascades every 1,000ms.
- **Granular row props and memoization**: `FindingRow` is wrapped in `React.memo` and accepts a scalar `runStatus` prop rather than the full mutable `runs` dictionary. Finding list updates or individual finding state changes re-render only the affected row. `FileGroup`, `FindingsView`, `DashboardView`, `WorkspaceTabs`, `AppHeader`, `GraphTimeline`, `CodeBlock`, and `MessageBubble` are all memoized to prevent parent-driven re-renders.
- **CSS layout containment**: `.finding-row-contained` in `index.css` applies `content-visibility: auto` and `contain-intrinsic-size: auto 65px` to finding rows, isolating row layouts and preventing DOM mutations from triggering full-page layout reflows across large finding sets.
- **Concurrent transition scheduling**: Finding selection (`setSelectedFinding`) and tab navigation (`setActiveTab`) in `App.jsx` are scheduled using React `startTransition`, ensuring that urgent user input (such as button clicks and typing) is prioritized without thread freezing.
- **Ref-stabilized listeners & memoized contexts**: `useKeyboardShortcuts` holds handler functions in refs to attach `window.addEventListener('keydown')` only once on mount instead of re-registering on every re-render. `ToastProvider` memoizes its context value to avoid invalidating child component trees on toast state updates.
