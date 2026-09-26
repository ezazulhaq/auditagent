# Frontend guide

## Technology and build

The frontend uses React 19, Vite 5, Tailwind CSS 4, Lucide React, Vitest, Testing Library, and jsdom. It is an ES module application.

- Development: Vite port 5173 with `/api` proxy.
- Production build: output replaces `src/main/resources/static`.
- Entry: `frontend/src/main.jsx`.
- Global Tailwind, design tokens, reusable surface/field/action primitives, focus treatment, reduced-motion behavior, and scrollbar/diff helpers: `index.css`.
- `App.css` contains older starter-style rules and is not imported by the current `App.jsx`.

## Application orchestration

`App.jsx` owns:

- authentication session and configuration errors;
- repository and branch lists;
- selected repository, branch, scanner, and force-rescan;
- active workspace tab;
- scan timer;
- sidebar expansion state;
- latest restored thread payload;
- composition of scan, remediation, memory, and chat controllers.

### Startup

1. Fetch `/api/auth/session`.
2. Show loading state, login page, or authenticated application.
3. When authenticated, fetch repositories.
4. When repository changes, fetch branches and prefer the default.
5. When repository/branch is complete, restore thread and report in parallel.
6. Hydrate chat and remediation state from the thread response.

### Selection race control

`selectedKeyRef` prevents duplicate restore for the same repository/branch. Request aborts and the memory hook's monotonic request counter prevent a slower old selection from overwriting a newer one.

## API client

`createAuditApi()` is dependency-injectable for tests.

- Always uses `credentials: include`.
- Caches the CSRF token only in module memory after session discovery.
- Adds CSRF only to non-safe methods.
- Converts non-2xx replies into capped error messages.
- Normalizes fresh, cached, and restored reports into the same camelCase shape.
- Requires a terminal `complete` or `cached` event for scan success.
- Converts a terminal scan `error` event into an exception carrying the backend `code` and exact safe message.

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

It:

- hydrates recoverable state;
- clears stale remediation when a new scan begins;
- starts analysis and consumes run-bound events;
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

Shows repository/permission, branch, scanner, force-rescan toggle, run button, installation help, elapsed time, accessible progress state, stage timeline, message, and error. Elapsed time is derived from start/finish timestamps and the current wall clock, so delayed browser interval callbacks cannot undercount a long scan. It is a desktop rail and a full-width mobile section. It supports expanding and collapsing via a state managed by `App.jsx` to maximize workspace area, rendering a narrow icon-only bar when collapsed.

### `DashboardView`

Shows guided empty-state steps or executive metric cards, severity distribution, action-oriented risk posture, repository context, and scan-execution metadata. All values are derived from the existing report and findings state.

### `DocsSidebar` and `DocsMainPanel`

Provide the in-app documentation viewing experience. `DocsSidebar` fetches the list of available documentation files via `auditApi`, formats their filenames for display, and handles selection. `DocsMainPanel` fetches the raw markdown content of the selected document and renders it using `react-markdown` with `@tailwindcss/typography` styles applied.

### `FindingsView`

Performs local sorting and filtering, then reports the original selected finding object to the parent so drawer and remediation actions remain unchanged. `filterFindings()` creates a new array, preserves scanner order within each severity, and applies the fixed priority `HIGH`, `MEDIUM`, `LOW`, `INFO`.

Search is null-safe, case-insensitive, whitespace-trimmed, and uses AND semantics for multiple terms across ID, type, path, description, rule ID, language, severity, and status. Severity buttons show counts for all four levels. The lifecycle-status selector is derived from statuses present in the current audit and ordered through the remediation lifecycle, including intermediate and failure states. Search, severity, and status compose; Reset and the no-match recovery action clear all three.

The responsive inventory presents severity, type, ID, filename/line, directory, and lifecycle status with horizontally scrollable severity controls, stacked mobile filters, mobile card rows, and a desktop table layout. Result-count changes are announced through a polite live region.

### `FindingDrawer`

The most security-sensitive UI:

- `DETECTED`: allows analysis.
- `PUBLISHED` / `PR_OPEN`: The drawer polls the backend API `/api/reports` every 5 seconds to catch GitHub webhook state changes (e.g., `FIXED` upon merge) without needing a page refresh.
- `AWAITING_APPROVAL`: Automatically overridden by autonomous PR creation, though structurally preserved for legacy compatibility.
- `PUBLISH_FAILED`: shows retry.
- `PR_OPEN`: links the PR and explains merge is pending.
- `FIXED`: states that GitHub merge fixed it.

The approve button is not rendered until the preview is loaded. The drawer uses a full-screen mobile layout, focuses its close control when opened, and supports Escape dismissal without changing any decision callback or value.

### `TokenUsageView`

Presents AI observability and token usage data from `/api/observability/tokens` in an aggregated table. Shown via `AppHeader` modal.

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
