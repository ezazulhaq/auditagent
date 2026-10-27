# AuditAgent Architectural & System Invariants

This guide records the critical invariants and ground truths of the AuditAgent codebase. When navigating documentation in `doc/` (@doc), always use these principles to discern current, active implementation from legacy or historical notes.

---

## 1. Authentication & Security Identity

- **GitHub App OAuth + PKCE**:
  - Initiated via `GET /api/auth/github/login` with single-use OAuth state and PKCE code verifier.
  - State and PKCE are bound to a short-lived `HttpOnly` browser cookie.
  - Callback (`GET /api/auth/github/callback`) validates the cookie before exchanging authorization code.
- **Session-Based Security**:
  - Authenticated session is represented by an opaque session token.
  - Server stores only the SHA-256 hash in `user_sessions`, tied to user ID, CSRF token, and expiry.
  - Browser-supplied `clientId`, `threadId`, model output, paths, or usernames are never security identities.
- **Token Protection & Cipher**:
  - GitHub access and refresh tokens are stored exclusively as AES-GCM ciphertext via `TokenCipher`.
  - Plaintext tokens are **never** logged, persisted, exposed in API responses, placed in clone URLs, or passed as command arguments.
  - Bot writes use just-in-time installation tokens; user tokens are used strictly for repository discovery and permission verification.
- **CSRF Enforcement**:
  - `ApiSecurityFilter` guards `/api/**`.
  - Non-GET/HEAD mutations require matching `X-CSRF-Token` header.

---

## 2. Repository & Workspace Management

- **GitHub App Repositories Only**:
  - Repositories are discovered solely through installed GitHub Apps via `GET /api/github/repositories`.
  - Arbitrary client-supplied filesystem paths are strictly rejected by shared APIs.
- **Isolated Managed Workspaces**:
  - Clones are maintained by JGit under isolated directory trees rooted at `AUDITAGENT_WORKSPACE_ROOT`.
  - Target files are resolved strictly below a real, non-symlink repository root; path traversal components (`..`) and symlink escapes are rejected.
  - Mutating changes are isolated to feature branches, never applied directly to production roots without review.

---

## 3. Autonomous Remediation Workflow (LangGraph4j)

- **16-Stage Star Topology**:
  - Remediation is orchestrated using a state machine implemented in `LangGraph4j` (`MultiAgentRemediationGraph`).
  - A `SupervisorNode` reviews the collective state (`MultiAgentState`) and routes execution to specialized worker nodes.
  - Stage 1 is always `stage-planning`, which outputs a structured multi-agent remediation plan.
- **Dynamic Skill Loading**:
  - System prompts are dynamically loaded by `SkillManagerService` from `.auditagent/skills/stage-*/SKILL.md` (e.g., `stage-planning`, `stage-discovery`, `stage-security`, `stage-data-flow`).
  - Runtime skills live in `.auditagent/skills/`; developer/agent workspace skills live in `.agents/skills/`.
- **Recursion Limits**:
  - Due to multi-stage complexity, graph compilation explicitly sets `.recursionLimit(150)`.
- **Automated Verification & PR Publication**:
  - Fixes are compiled and verified through native build/test commands before publication.
  - Autonomous flow generates a GitHub pull request and transitions the vulnerability status to `PR_OPEN`.
- **Webhook Synchronization**:
  - Merging the PR in GitHub triggers a `pull_request` webhook (`action: closed`, `merged: true`) to `/api/webhooks/github`.
  - `PullRequestLifecycleService` transitions the vulnerability and associated file findings to `FIXED`.

---

## 4. Data & Persistence

- **Transactional Source of Truth**:
  - PostgreSQL / DuckDB is the sole source of truth for findings, reports, user sessions, and durable agent memories.
  - In-memory state is never relied upon for restart recovery.
- **Schema Evolution**:
  - Controlled and ordered via `schema_version`.
- **Vulnerability Fingerprints**:
  - Fingerprint is a deterministic SHA-256 hash of normalized rule ID, relative file path, and normalized code snippet.
  - Rescans re-identify existing vulnerabilities, preserving ID, status, and history.
- **Lexical Retrieval (No Vector / No Embeddings)**:
  - Approved remediation memories are indexed via DuckDB FTS / BM25 over title, summary, root cause, and remediation patterns.
  - There are **no** vector embeddings, vector databases, LanceDB, or external semantic search engines.

---

## 5. Documentation Maintenance Policy

- **Mandatory Synchronization**:
  - `doc/` is treated as code.
  - Every change to APIs, SSE events, database schemas, security controls, configuration, dependencies, scanner rules, agent tools, prompts, or workflows **must** update the corresponding guides in `doc/` in the same commit/change.
  - Always update `doc/03-feature-catalog.md` alongside any functional enhancement.
  - Validate documentation links before completing tasks.
