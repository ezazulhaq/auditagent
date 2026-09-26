# AuditAgent Architecture & AI Agent Context

This document provides comprehensive context for AI assistants working on the **AuditAgent** project. It explains the architecture, agent workflows, key components, and implementation details to enable effective code modifications and troubleshooting.

## Documentation maintenance (mandatory)

- Treat the documentation suite in `doc/` as part of the implementation, not as an optional follow-up.
- For every future enhancement, bug fix, refactor, API or SSE change, schema or migration change, security-control change, configuration change, dependency or build change, scanner/rule change, agent/tool/prompt change, UI or user-workflow change, operational change, or status/lifecycle change, review the documentation index at `doc/README.md` and update every affected document in the same change.
- Keep `doc/03-feature-catalog.md` synchronized with all user-visible and implementation features. Update `doc/07-api-reference.md`, `doc/08-data-and-persistence.md`, `doc/10-security-model.md`, `doc/11-setup-configuration-operations.md`, and `doc/15-source-inventory-and-glossary.md` whenever their respective contracts, schema, security boundary, configuration, or source inventory changes.
- Document the current implemented behavior only. Clearly label legacy, inactive, planned, fallback, or limited behavior; do not copy obsolete local-path or conversational-approval descriptions into current guidance.
- Write for both laypeople and maintainers: explain the business/use-case effect, user journey, technical implementation, failure/recovery behavior, security implications, configuration, tests, and future-change considerations where applicable.
- Before completing a change, validate local documentation links and run `.githooks/pre-commit` (or allow the installed Git hook to run). The hook is an enforcement check; it does not replace thoughtful documentation updates.
- Use `AUDITAGENT_SKIP_DOCS_CHECK=1` only for a demonstrably documentation-neutral staged change, and state the reason in the change handoff.

---

## 🎯 Project Overview

AuditAgent is an intelligent security auditing and compliance tool that combines traditional security scanning (Semgrep) with AI-powered autonomous remediation. It enables developers to:

1. **Scan** codebases for security vulnerabilities
2. **Chat** with an AI to understand and triage issues
3. **Automatically fix** vulnerabilities using AI agents that can read, patch, compile, test, and verify changes
4. **Approve/reject** proposed fixes with full rollback capability

### Tech Stack
- **Backend**: Java 21, Spring Boot (WebFlux), Spring AI, OpenAI SDK, DuckDB
- **Frontend**: React 19, Vite, TailwindCSS 4, Lucide React
- **Security Scanner**: Semgrep (CLI integration)
- **LLM Provider**: OpenAI-compatible API (`auditagent.llm.openai.model-name`)
- **Memory/Search**: DuckDB transactional persistence and FTS/BM25 retrieval; no embeddings, vector index, VSS, Lance, or external semantic-search service.
- **Source Control**: GitHub App authentication, GitHub REST APIs, and JGit-managed isolated clones and pull requests

---

## 🏗️ Architecture & Data Flow

### High-Level Workflow

```
User Request → Frontend (React SSE Client) 
    ↓
Backend Controller (AuditController) 
    ↓
OrchestratorService (Session State Management)
    ↓
├─→ ScannerService (Semgrep Integration)
├─→ LlmService (LLM Chat & Fix Generation)
├─→ FilePatchService (Safe File Modifications)
├─→ DatabaseService (DuckDB Persistence)
└─→ ReporterService (Markdown/HTML Report Generation)
```

### Three Main Workflows

## GitHub App Authentication and Repository Identity (Current)

This section takes precedence over historical local-path, browser `clientId`, and conversational-approval descriptions below.

- Users authenticate through the GitHub App web flow. `GET /api/auth/github/login` creates a one-time OAuth state and PKCE verifier, stores only protected server-side state, and binds the flow to a short-lived HttpOnly browser-state cookie. The callback validates both state values before exchanging the authorization code.
- The server stores GitHub access and refresh tokens only as AES-GCM ciphertext through `TokenCipher`. Expiring access tokens are refreshed through `GitHubAuthService`; plaintext tokens must never be persisted, logged, returned to the frontend, placed in clone URLs, or included in command arguments.
- Successful login creates an opaque session token. DuckDB stores only its hash in `user_sessions`, together with the authenticated user ID, CSRF token, and expiry. The session cookie is HttpOnly, SameSite=Lax, path `/`, and Secure whenever explicitly configured or HTTPS callback/frontend URLs are used.
- `ApiSecurityFilter` protects `/api/**` except the OAuth entry/callback, session discovery, preflight requests, and the GitHub webhook. Every protected non-GET/HEAD request requires the session's `X-CSRF-Token`. Production CORS must allow only the configured frontend origin and credentials.
- The authenticated server session is the security identity. Browser-generated `clientId`, `threadId`, model output, repository paths, and request-supplied usernames are never authorization identities.
- Repositories come only from GitHub App installations through `GET /api/github/repositories`; branches come from `GET /api/github/repositories/{repositoryId}/branches`. The backend resolves and persists repository ID, installation ID, permissions, clone URL, and default branch. Shared APIs reject arbitrary client-supplied filesystem paths.
- User OAuth tokens are used for repository discovery and current-user permission checks. Remote bot writes use just-in-time installation tokens, which are never persisted.
- The `RemediationWorkflowGraph` executes autonomously without manual user approval. It generates a PR automatically after analysis and verification. 
- GitHub webhook events (`closed` and `merged`) trigger backend synchronization, automatically transitioning associated vulnerability statuses to `FIXED`. The frontend polls the backend during the `PR_OPEN` state to reflect these updates instantly.

### Authentication and Repository APIs

- `GET /api/auth/session` reports configuration/authentication state and returns the CSRF token for an authenticated session.
- `GET /api/auth/github/login` starts OAuth with state and PKCE.
- `GET /api/auth/github/callback` validates the browser-bound state, exchanges the code, and creates the secure session.
- `POST /api/auth/logout` revokes the local session.
- `GET /api/github/repositories` lists repositories visible through installed GitHub Apps.
- `GET /api/github/repositories/{repositoryId}/branches` verifies access and lists branches.

## Memory-Aware System (Current)

The historical workflow descriptions below are useful background, but this section takes precedence.

- An authenticated user owns every conversation and run. A stable thread is keyed by the server user ID plus `repositoryId` and branch, with one user-specific general thread before repository selection. `clientId` and `targetRepo` remain legacy-data concepts only and must not be accepted as security identities by shared APIs.
- DuckDB is the source of truth for messages, runs, checkpoints, tool episodes, verification evidence, changed files, and approved remediation memories. In-memory state must not be required for restart recovery.
- Persist only redacted details. Message/tool/error detail expires after 30 days, while thread summaries, run outcomes, hashes, verification metrics, and approved memories remain until explicitly forgotten.
- POST /api/chat persists user input before calling the LLM and persists the assistant response before returning. It loads findings from DuckDB; client-supplied findings are not trusted.
- A remediation creates an agent_runs record before model work. Every remediation SSE event carries runId; approval requires the matching runId and vulnId.
- On startup, active runs become INTERRUPTED. Read-only work can resume. A patched run must match its persisted after-hashes or becomes CONFLICTED; mutating tools are never replayed automatically.
- Only a verified and user-approved run can create a reusable repository memory. Rejected and failed episodes remain diagnostic history only and are excluded from positive retrieval.

### Retrieval Rules

- Approved memories are indexed with DuckDB FTS over title, summary, root cause, remediation pattern, and deterministic security search text.
- Search uses normalized, bounded terms through prepared statements. Candidate ranking is fixed: exact finding fingerprint, exact rule ID, category/type, BM25, language/framework/file pattern, then confidence, recency, and prior successful reuse.
- The security concept catalog supplies aliases such as SQL injection/SQLi/prepared statement, XSS/output encoding, and path traversal/canonical path.
- FTS failure is non-fatal. Mark retrieval degraded and use the structured metadata ranking; scanning and remediation still work.
- FTS indexes are manually rebuilt under one lock when approved-memory source and indexed versions differ. DuckDB FTS is lexical retrieval enriched by security metadata; do not describe it as vector similarity.

#### 1. **Scan Workflow**
```
POST /api/scan → OrchestratorService.scanRepo() 
    → ScannerService.scan() (runs Semgrep CLI)
    → Parse SARIF results into Vulnerability objects
    → ReporterService.generate[Markdown|Html]()
    → DatabaseService.saveReport()
    → Stream SSE events to frontend (progress, findings, completion)
```

#### 2. **Chat Workflow**
```
POST /api/chat → LlmService.chat()
    → Build context with active vulnerabilities
    → Call LLM with system prompt (Chat Agent persona)
    → Parse response for command tags: [TRIGGER_SCAN] or [TRIGGER_FIX:VULN-ID]
    → Frontend intercepts tags and triggers corresponding workflows
```

#### 3. **Fix Workflow**
```
POST /api/analyze → OrchestratorService.analyzeVulnerability()
    → Fetch vulnerability from DB
    → Orchestrate remediation via LangGraph4j graph (`MultiAgentRemediationGraph`)
    → 15-Stage Star Topology (Supervisor LLM routes to specialized code review sub-agents)
    → Agents dynamically load instructions via `SkillManagerService` from `.auditagent/skills/stage-*/SKILL.md`
    → Autonomously verify and create a PR
    → Mark status as PR_OPEN and stream back to frontend
    ↓
GitHub Webhook (`action: closed`, `merged: true`)
    → Updates vulnerability status to FIXED
    → UI polls and updates progress timeline automatically
```

---

## 🤖 The AI Agents

### 1. Chat Agent (Intelligent Audit & Compliance Agent)

**Purpose**: Conversational interface for vulnerability triage and guidance.

**Implementation**: `LlmService.chat(String userMessage, List<Vulnerability> findings)`

**System Prompt**: Located in `LlmService.java:119-131`
- Role: "Intelligent Audit and Compliance Agent"
- Context: Full list of active vulnerabilities (ID, severity, file, line, status, description)
- Command Tags:
  - `[TRIGGER_SCAN]` → Frontend initiates new scan
  - `[TRIGGER_FIX:VULN-ID]` → Frontend calls `/api/analyze` with the vulnerability ID

**Key Behaviors**:
- Matches user intent to vulnerabilities (e.g., "fix SQL injection" → finds matching VULN-ID)
- Asks for clarification if multiple matches or ambiguous intent
- Provides security best practices and vulnerability explanations

**Frontend Integration**: `frontend/src/App.jsx:handleChatSubmit()`
- Uses regex to detect command tags in LLM response
- Automatically triggers scan or fix workflows based on detected tags

---

### 2. Multi-Agent Remediation (The 15-Stage Workflow)

**Purpose**: Generate secure code patches for identified vulnerabilities by simulating a rigorous code review process.

**Implementation**: `MultiAgentRemediationGraph` (LangGraph4j)

**Skill Configuration**: Prompts are dynamically loaded from `.auditagent/skills/stage-*/SKILL.md` (e.g., `stage-discovery`, `stage-security`, `stage-data-flow`).

**Architecture**: 
- **Star Topology**: A `SupervisorNode` acts as the router. It observes the `MultiAgentState` (which includes all outputs from previous agents) and selects the next worker agent to execute.
- **Worker Agents**: Generated dynamically by `WorkerAgentGraphFactory`. There are 15 distinct worker stages (Discovery, Syntax, Baseline, Security Review, Data-Flow, RCA, Validation, etc.).
- **Execution Limits**: Due to LangGraph4j's inherent limits, the compilation configuration explicitly overrides `.recursionLimit(150)` to ensure complex multi-stage graphs are not abruptly terminated.

**Output Processing**: 
- Multi-agent workflow natively validates the fix via build checks and test runs natively before submitting a PR.

---

## 📁 Key Files & Components

### Backend Core Services

#### `OrchestratorService.java`
**Responsibility**: Session state management, workflow coordination, SSE streaming.

**Key Methods**:
- `scanRepo()` - Orchestrates scan workflow with progress streaming
- `analyzeVulnerability()` - Fetches vulnerability, generates fix, returns for approval
- `approveFix()` - Applies or rejects fix, updates DB, regenerates reports
- `getCachedReport()` - Checks DuckDB for existing scan results

**Session State**: `ThreadState` inner class
```java
class ThreadState {
    String repoPath;
    List<Vulnerability> findings;
    ScanMetadata metadata;
    Vulnerability activeVuln;      // Currently being analyzed
    int activeVulnIndex;            // Index in findings list
}
```

**Concurrency Model**: 
- Uses `Thread.startVirtualThread()` (Java 21 virtual threads) for async work
- SSE streaming via `Sinks.Many<String>` (Reactor pattern)

---

#### `LlmService.java`
**Responsibility**: All interactions with the LLM API.

**Key Methods**:
- `chat(String userMessage, List<Vulnerability> findings)` - Chat Agent
- `generateFix(Vulnerability vuln)` - Patch Engineer
- `cleanCodeFix(String code)` - Post-process model output

**Spring AI Integration**:
```java
ChatModel chatModel;  // Auto-injected from application.yaml config
Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
ChatResponse response = chatModel.call(prompt);
```

**Configuration** (`application.yaml`):
```yaml
auditagent:
  llm:
    provider: openai-compatible
    openai:
      base-url: ${OPENAI_BASE_URL:}
      api-key: ${OPENAI_API_KEY:}
      model-name: ${PROVIDER_MODEL:gpt-4o}
```

---

#### `ScannerService.java`
**Responsibility**: Execute Semgrep CLI, parse SARIF output, extract vulnerabilities.

**Key Method**: `scan(String targetRepo, Consumer<Map<String, Object>> progressCallback)`

**Returns**: `ScanResult` containing:
- `List<Vulnerability> findings`
- `int filesScanned`
- `long totalLoc` (lines of code)
- `int errorCount` (parser warnings)

**Semgrep Command**:
```bash
semgrep scan --json --config=auto <targetRepo>
```

**Output Format**: SARIF (Static Analysis Results Interchange Format)
- Parses `results[]` array
- Maps to `Vulnerability` domain objects with auto-generated IDs (VULN-XXXXXX)

---

#### `FilePatchService.java`
**Responsibility**: Safe file modifications with backup/rollback capability.

**Key Methods**:
- `applyPatch(String repoPath, Vulnerability vuln)` - Replace vulnerable code with fix
- `rollbackFile(String filePath)` - Restore from backup

**Safety Features**:
- Creates `.auditagent/backups/` directory (configurable via `application.yaml`)
- Timestamped backups before each modification
- Line-by-line replacement (not full file overwrite)
- Returns `false` if file doesn't match expected structure (no destructive changes)

---

#### `DatabaseService.java`
**Responsibility**: DuckDB persistence for scan results, durable agent memory, recovery state, and lexical repository-memory retrieval.

**Migration and identity rules**:
- Schema evolution is ordered and transactional through `schema_version`. Before the first memory migration, create a one-time backup of the existing DuckDB file.
- Never reset the database or discard prior reports / vulnerabilities data during a migration.
- A vulnerability fingerprint is SHA-256 of normalized rule ID, normalized relative path, and normalized vulnerable code. On a matching rescan, reuse the existing vulnerability ID, status, and proposed fix.
- Durable memory is stored in `memory_threads`, `memory_messages`, `agent_runs`, `agent_steps`, `verification_results`, `run_changes`, `repository_memories`, and `memory_fts_state`.
- DatabaseService owns approved-memory FTS rebuilds and the structured retrieval fallback.

**Schema**:
```sql
CREATE TABLE IF NOT EXISTS reports (
    repo_path VARCHAR PRIMARY KEY,
    markdown_report TEXT,
    html_report TEXT,
    findings_json TEXT,
    metadata_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vulnerabilities (
    id VARCHAR PRIMARY KEY,
    repo_path VARCHAR,
    file_path VARCHAR,
    line_number INTEGER,
    severity VARCHAR,
    vuln_type VARCHAR,
    description TEXT,
    code_snippet TEXT,
    language VARCHAR,
    proposed_fix TEXT,
    status VARCHAR,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Key Methods**:
- `saveReport()` - Store full scan results
- `getReport()` - Retrieve cached report
- `getVulnerabilityById()` - Fetch specific vulnerability
- `updateVulnerabilityStatus()` - Update status (DETECTED → FIXED/IGNORED)

---

### Frontend Architecture

#### `frontend/src/App.jsx`
**Primary UI Component**: Single-file React application with SSE client.

**Key State**:
```javascript
const [scanning, setScanning] = useState(false);
const [analyzing, setAnalyzing] = useState(false);
const [htmlReport, setHtmlReport] = useState("");
const [currentFindings, setCurrentFindings] = useState([]);
const [chatMessages, setChatMessages] = useState([]);
const [waitingForApproval, setWaitingForApproval] = useState(false);
```

**Durable UI state**:
- Restore authentication from `/api/auth/session`; use the returned CSRF token on protected mutations and never store GitHub credentials in browser storage.
- Restore the stable user-owned thread, messages, pending approval, publication state, and interrupted run from `/api/memory/threads` on reload or repository/branch change.
- Track `activeRunId`; do not submit an approval for a stale run.
- Show Resume/Discard controls for interrupted or conflicted runs and confirmed Forget conversation / Forget repository memory controls.

**SSE Event Handling**:
```javascript
eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    switch(data.type) {
        case "progress":    // Update progress bar
        case "status":      // Add status message to chat
        case "complete":    // Display report, update findings
        case "cached":      // Load cached results
        case "analysis_complete": // Show proposed fix, enable approval
    }
};
```

**Chat Command Tag Detection**:
```javascript
if (text.includes('[TRIGGER_SCAN]')) {
    handleScanRepo();  // Initiate scan workflow
}
const fixMatch = text.match(/\[TRIGGER_FIX:(VULN-\d+)\]/);
if (fixMatch) {
    handleAnalyzeVulnerability(fixMatch[1]);  // Initiate fix workflow
}
```

---

## 🔄 Detailed Workflow Examples

### Example 1: User Asks "Fix SQL Injection in Database.java"

1. **User Input** → `POST /api/chat`
   ```json
   {
     "message": "Fix SQL injection in Database.java",
     "findings": [/* all current vulnerabilities */]
   }
   ```

2. **Chat Agent Processing** (`LlmService.chat()`):
   - System prompt includes full vulnerability list
   - Model matches intent to `VULN-000042` (SQL Injection in Database.java:156)
   - Response: `"I'll analyze and fix VULN-000042 (SQL Injection) in Database.java. [TRIGGER_FIX:VULN-000042]"`

3. **Frontend Tag Detection** (`App.jsx`):
   - Regex finds `[TRIGGER_FIX:VULN-000042]`
   - Calls `handleAnalyzeVulnerability("VULN-000042")`

4. **Analysis Request** → `POST /api/analyze`
   ```json
   {
     "threadId": "session-abc123",
     "vulnId": "VULN-000042",
     "targetRepo": "/path/to/repo"
   }
   ```

5. **Patch Generation** (`OrchestratorService.analyzeVulnerability()`):
   - Fetches `VULN-000042` from DB
   - Calls `LlmService.generateFix(vuln)`
   - Stores proposed fix in `vuln.proposedFix`
   - Returns SSE event:
     ```json
     {
       "type": "pr_created",
       "runId": "run-id",
       "pullRequestNumber": 42,
       "pullRequestUrl": "https://github.com/org/repo/pull/42",
       "runStatus": "PR_OPEN",
       "message": "Pull request #42 created."
     }
     ```

6. **Automated Status Update via Webhook**:
   - User merges PR in GitHub.
   - GitHub sends `pull_request` (`action: closed`, `merged: true`) webhook to `/api/webhooks/github`.
   - `PullRequestLifecycleService.handlePullRequestEvent()` updates vulnerability (and any other findings in the same file) to `FIXED`.
   - Frontend polls `GET /api/reports` and instantly updates the UI timeline.

---

### Example 2: Scan Workflow with Caching

1. **User Clicks "Start Scan"** → `POST /api/scan`
   ```json
   {
     "threadId": "session-xyz",
     "targetRepo": "/workspace/myproject",
     "projectName": "MyProject",
     "scannerName": "semgrep",
     "forceRescan": false
   }
   ```

2. **Cache Check** (`OrchestratorService.scanRepo()`):
   - Query DuckDB: `SELECT * FROM reports WHERE repo_path = '/workspace/myproject'`
   - If found → Return cached report immediately (SSE type: "cached")
   - If not found or `forceRescan=true` → Continue to step 3

3. **Semgrep Execution** (`ScannerService.scan()`):
   - Stream progress events: 5% (init), 25% (scanning), 75% (parsing), 100% (complete)
   - Parse SARIF JSON output
   - Create `Vulnerability` objects with auto-generated IDs

4. **Report Generation**:
   - `ReporterService.generateMarkdown()` → Text summary
   - `ReporterService.generateHtml()` → Rich HTML table with severity badges

5. **Persistence**:
   - `DatabaseService.saveReport()` → Store in DuckDB
   - Insert/update both `reports` and `vulnerabilities` tables

6. **SSE Complete Event**:
   ```json
   {
     "type": "complete",
     "html_report": "<table>...</table>",
     "findings": [/* all vulnerabilities */],
     "metadata": {
       "projectName": "MyProject",
       "totalFilesScanned": 142,
       "scanDuration": "12.34 seconds"
     },
     "message": "✅ Scan complete! Found 7 issues in 142 files."
   }
   ```

---

## 🛠️ Development Guidelines for AI Assistants

### Architectural & Code Quality Standards (SOLID & OOP)
- **Single Responsibility Principle (SRP)**: Ensure every class, service, or component has only one reason to change. Avoid monoliths and "god classes". Keep components tightly scoped (e.g., separating authorization, scanning, and PR workflows).
- **Open-Closed Principle (OCP)**: Design modules to be open for extension but closed for modification. Use interfaces and polymorphism to extend behavior without modifying existing tested code.
- **Liskov Substitution Principle (LSP)**: Ensure that implementations and subclasses can seamlessly replace their base abstractions without breaking functionality or introducing unexpected behavior.
- **Interface Segregation Principle (ISP)**: Create small, highly specific interfaces rather than large, monolithic ones. Do not force clients to depend on methods they do not use.
- **Dependency Inversion Principle (DIP)**: Depend on abstractions rather than concrete implementations. Use constructor injection to provide decoupled dependencies.
- **Object-Oriented Design**: Encapsulate internal state and avoid leaking implementation details. Keep business logic within the service and domain layers, and maintain strict boundaries between DTOs (Data Transfer Objects), domain models, and persistence entities.

### When Modifying GitHub Authentication or Authorization
- Treat `GitHubAuthService`, `ApiSecurityFilter`, `TokenCipher`, and `DatabaseService` as one security boundary. Preserve OAuth state/PKCE validation, browser-state cookie binding, hashed sessions, encrypted expiring tokens, CSRF checks, and ownership checks.
- Never accept a local path, GitHub login, browser `clientId`, or model-generated value as proof of identity or repository authorization. Resolve repositories by GitHub repository ID and recheck access through GitHub.
- Keep user OAuth tokens separate from installation tokens. User tokens support discovery and user permission checks; short-lived installation tokens support bot clone/push/PR operations and must remain memory-only.
- Do not log request headers, cookies, OAuth codes, PKCE verifiers, session tokens, CSRF tokens, authorization headers, private keys, webhook secrets, or clone credentials. Route persisted error detail through redaction and size caps.
- OAuth entry/callback and webhook routes are intentionally unauthenticated by session. Do not broaden that allowlist. The webhook must always require a valid HMAC signature, and failed processing must release its delivery claim so GitHub can retry.
- Preserve strict same-origin/production CORS behavior and secure-cookie auto-enablement for HTTPS deployments. Frontend API calls must remain relative and credentialed.
- Add or update tests for state/PKCE mismatch, session ownership, CSRF, token encryption/refresh, permission revocation, webhook HMAC/replay, and secret redaction whenever this boundary changes.

### When Modifying Memory, Retrieval, or Recovery
- Treat DatabaseService as the persistence boundary. Add schema through a new ordered migration; do not use ad-hoc table creation outside migrations.
- Preserve redaction and limits: messages are capped at 8,000 characters, tool results at 4,000 characters, and AWS keys, bearer tokens, passwords, private keys, and common secret assignments must be redacted before persistence.
- Keep the 12,000-token model context budget. Preserve system prompt, task/checkpoint, compact summary, retrieved memories, and newest complete turns. Tool requests must remain paired with their results.
- Do not add embeddings or vector search. Extend SecurityConceptCatalog and structured metadata if retrieval needs better domain coverage.
- Keep repository-memory retrieval repository-scoped and approved-only. Retrieve at most 50 candidates and inject at most eight.
- Only successful, approved runs may create reusable memory. Do not use model prose to mark a run verified, a vulnerability fixed, or ignored.
- Verification requires a successful patch, supported-build success (or explicit skip reason), disappearance of the target finding fingerprint, and passing tests when runnable (or explicit skip reason).
- Use run_changes hashes and backups for recovery. A hash mismatch must become CONFLICTED, not an automatic overwrite.

### Memory APIs
- POST /api/memory/threads creates/restores a user-owned stable thread using optional `repositoryId` plus `branch`.
- GET /api/memory/threads/{threadId} restores messages and recoverable run state.
- DELETE /api/memory/threads/{threadId} forgets conversation and run detail after safe rollback handling.
- DELETE /api/memory/repositories?repositoryId=...&branch=... forgets approved repository/branch memory and triggers an FTS rebuild.
- POST /api/runs/{runId}/resume and POST /api/runs/{runId}/discard operate on durable run state.

### When Modifying Scan Logic
- **Entry Point**: `ScannerService.java:scan()`
- **SARIF Parsing**: Look for `results[].locations[].physicalLocation`
- **Progress Streaming**: Use provided `progressCallback` consumer
- **Error Handling**: Increment `errorCount` for parser failures, don't halt scan

### When Modifying LLM Integration
- **Model Configuration**: `application.yaml:11-13` (model ID, region, credentials)
- **Prompt Engineering**: 
  - Chat Agent: `LlmService.java:119-131`
  - Patch Engineer: `.auditagent/skills/patch-engineer/SKILL.md`
- **Response Parsing**: Always handle null responses (`Optional` pattern used throughout)
- **Command Tags**: Frontend regex in `App.jsx` (lines ~800-850)

### When Modifying Fix Application
- **Backup Location**: Configurable via `application.yaml:auditagent.agent.backup-dir`
- **Rollback Support**: Use `FilePatchService.rollbackFile()` if patch fails
- **DB Updates**: Always call `DatabaseService.updateVulnerabilityStatus()` after status changes
- **Report Regeneration**: Required after any vulnerability status update

### When Adding New Agent Capabilities
1. Define skill in `.auditagent/skills/<skill-name>/SKILL.md`
2. Add frontmatter:
   ```yaml
   ---
   name: skill-name
   description: One-line description
   input_schema:
     type: object
     properties:
       param_name:
         type: string
     required:
       - param_name
   ---
   ```
3. Add system prompt instructions after frontmatter
4. Register in `SkillManagerService.java` if dynamic loading needed
5. Call via `LlmService` with skill-specific context

### Testing Considerations
- **Semgrep Availability**: Ensure `semgrep` CLI is in PATH
- **LLM API Keys**: Set `OPENAI_API_KEY` (and `OPENAI_BASE_URL` if using a proxy/OpenRouter)
- **Model Access**: Ensure account has access to the configured `PROVIDER_MODEL`
- **DuckDB Path**: Database created at `./auditagent.duckdb` (relative to working directory)
- **Frontend Proxy**: Vite dev server proxies `/api/*` to `localhost:8173`
- **GitHub App**: Configure the App credentials, callback, installation URL, webhook secret, token-encryption key, and a server-managed workspace root; use mocked GitHub APIs and temporary Git remotes for automated tests

---

## 🔍 Common Troubleshooting Scenarios

### "Empty response from LLM model"
- **Cause**: Model refused to respond (content filtering) or API error
- **Check**: `LlmService.java:44-46` - null safety checks
- **Fix**: Add retry logic or fallback prompt

### "Cannot apply fix: no active vulnerability in memory context"
- **Cause**: The run ID/vulnerability ID is stale, the run was discarded, or a prior restart marked the run interrupted.
- **Check**: `agent_runs` status, checkpoint, run changes, and the restored memory-thread response.
- **Fix**: Resume only a recoverable matching run, or discard it. Approval must send the active matching `runId` and `vulnId`.

### "Repository memory retrieval is degraded"
- **Cause**: DuckDB FTS extension installation, loading, or rebuilding failed.
- **Check**: `memory_fts_state` and the memory-thread response's retrieval health.
- **Fix**: The structured metadata fallback remains active. Resolve the DuckDB extension issue and rebuild FTS; do not block scans or remediation.

### SSE Connection Drops Mid-Scan
- **Cause**: Long-running scan exceeds proxy timeout
- **Check**: Vite config timeout, Spring WebFlux backpressure settings
- **Fix**: Increase timeout or add heartbeat events

### Patch Applied But Still Shows in Report
- **Cause**: Status update successful but rescan not triggered
- **Check**: `DatabaseService.updateVulnerabilityStatus()` called?
- **Fix**: Manual rescan or implement auto-rescan after fix approval

### Frontend Shows Cached Report When It Shouldn't
- **Cause**: `forceRescan=false` and cache exists
- **Check**: `ScanRequest.forceRescan` default value
- **Fix**: UI should set `forceRescan=true` if user explicitly requests new scan

---

## 📊 Domain Model Reference

### `Vulnerability.java`
```java
String id;                    // VULN-XXXXXX (auto-generated)
String filePath;              // Relative to repo root
int lineNumber;               // Line where vulnerability starts
String codeSnippet;           // Vulnerable code + surrounding context
Severity severity;            // CRITICAL, HIGH, MEDIUM, LOW, INFO
String vulnType;              // E.g., "SQL Injection", "XSS"
String description;           // Human-readable explanation
String ruleId;                // Full Semgrep rule ID, when available
String language;              // E.g., "java", "javascript"
String findingFingerprint;    // Stable hash for rescan identity and retrieval
String proposedFix;           // Generated secure code (null until analyzed)
VulnerabilityStatus status;   // DETECTED, FIXED, IGNORED
```

### `ScanMetadata.java`
```java
String projectName;
String scannerName;
String startTime;             // yyyy-MM-dd HH:mm:ss
String endTime;
String scanDuration;          // "X.XX seconds"
int totalFilesScanned;
long totalLocScanned;
```

### `Report.java`
```java
String repoPath;              // Primary key
String markdownReport;        // Text version
String htmlReport;            // Rendered HTML table
List<Vulnerability> findings;
ScanMetadata metadata;
Timestamp createdAt;
```

---

## 🚀 Startup & Runtime Configuration

### Required Environment Variables
```bash
export AWS_REGION="us-east-1"
export AWS_ACCESS_KEY_ID="your_access_key"
export AWS_SECRET_ACCESS_KEY="your_secret_key"

export GITHUB_APP_ID="your_app_id"
export GITHUB_APP_CLIENT_ID="your_client_id"
export GITHUB_APP_CLIENT_SECRET="your_client_secret"
export GITHUB_APP_PRIVATE_KEY="your_pkcs8_private_key"
export GITHUB_APP_WEBHOOK_SECRET="your_webhook_secret"
export AUDITAGENT_TOKEN_ENCRYPTION_KEY="your_high_entropy_encryption_key"
export GITHUB_APP_CALLBACK_URL="https://audit.example.com/api/auth/github/callback"
export GITHUB_APP_INSTALLATION_URL="https://github.com/apps/your-app/installations/new"
export AUDITAGENT_FRONTEND_URL="https://audit.example.com"
export AUDITAGENT_WORKSPACE_ROOT="/var/lib/auditagent/workspaces"
export AUDITAGENT_SECURE_COOKIES="true"
```

### Spring Boot Configuration (`application.yaml`)
```yaml
server:
  port: 8173

spring:
  application:
    name: auditagent
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://openrouter.ai/api/v1}
      chat:
        options:
          model: ${PROVIDER_MODEL:anthropic/claude-3.5-sonnet}

management:
  health:
    db:
      enabled: false
```

### Running the Application

**Development Mode** (Both Frontend & Backend):
```bash
./run-dev.sh
# Backend: http://localhost:8173
# Frontend: http://localhost:5173
# Logs: backend.log, frontend.log
```

**Backend Only**:
```bash
./mvnw spring-boot:run
```

**Frontend Only**:
```bash
cd frontend
npm run dev
```

---

## Core Services & Orchestration

The agentic capabilities are orchestrated by several Spring Boot services:
- **`GitHubAuthService`**: Runs state/PKCE OAuth, manages encrypted expiring GitHub authorization, creates hashed opaque sessions, exposes the CSRF identity, and enforces session ownership.
- **`GitHubApiClient` / `GitHubProvider`**: Discover installed repositories, check user and installation permissions, create just-in-time installation tokens, manage exact-SHA JGit workspaces, and publish idempotent pull requests.
- **`RepositoryAccessService`**: Centralizes access control logic.
- **`ScanOrchestratorService`**: Coordinates the repository scanning workflow and reports.
- **`RemediationWorkflowService`**: Orchestrates the AI remediation lifecycle, agent execution, and state transitions.
- **`PullRequestLifecycleService`**: Processes GitHub pull request events and updates vulnerability states.
- **`OrchestratorService`**: Coordinates scans and durable runs, attaches run IDs to SSE, validates approval freshness, resumes/discards safely, and streams recovery state.
- **`LlmService`**: Persists conversation messages around LLM calls, builds a budgeted context from complete turns, runs the tool loop with checkpoints/idempotency, and enforces evidence-based verification.
- **`ScannerService`**: Integrates with external security scanning engines (like Semgrep).
- **`FilePatchService`**: Handles safe file modifications, backup creations, and rollbacks.
- **`DatabaseService`**: Manages reports, vulnerability identity, conversations, run recovery, verification evidence, approved repository memory, migrations, retention cleanup, and DuckDB FTS/BM25 fallback retrieval.
