# Data and persistence

## Overview

AuditAgent uses one DuckDB file, configured by `auditagent.database.path` and defaulting to `audit_reports.duckdb`. `DatabaseService` is the sole persistence boundary. Most methods are synchronized to serialize access to the shared file.

## Startup behavior

1. Load DuckDB JDBC driver.
2. If a file database already exists and no pre-memory backup exists, copy it to `<db>.pre-memory-v1.bak`.
3. Run schema creation/migration in a transaction.
4. Record schema versions in order. Current schema version is 4; a fresh database records versions 1, 2, 3, and 4.
5. Change runs in `ACTIVE` or `PUBLISHING` to `INTERRUPTED`.
6. Start the FTS index check on a virtual thread when enabled.

Initialization errors are logged. The service currently does not fail application startup explicitly, so health/functional checks are important.

## Tables

### Legacy-compatible scan tables

#### `reports`

| Column        | Meaning                                                                                                          |
|---------------|------------------------------------------------------------------------------------------------------------------|
| `repo_path`   | Primary report key. Current format is `github:<repositoryId>:<branch>`.                                         |
| `md_report`   | Generated enterprise Markdown retained as the durable scan representation and for compatibility.                 |
| `html_report` | Generated standalone HTML retained for schema and API compatibility; it is not rendered by the current frontend. |
| `findings`    | JSON list of findings.                                                                                           |
| `metadata`    | JSON scan metadata.                                                                                              |

#### `scan_history_stats`

Stores the aggregated finding counts per scan for a repository and branch:

- history ID (primary key);
- repository ID, branch, and base SHA;
- creation timestamp;
- total findings, and high/medium/low/info severity counts.

Used to render the historical trend timeline on the dashboard.

PDF bytes are not stored in DuckDB and no schema migration is required for PDF reporting. PDF and downloadable Markdown are generated in memory from the authorized latest report, findings, metadata, repository identity, branch, and scan snapshot base SHA. This avoids duplicate binary storage, binds both exports to the selected snapshot, and ensures an export reflects current persisted finding statuses. The existing stored Markdown/HTML, report key, and scan-snapshot relationship remain unchanged.

#### `vulnerabilities`

| Column                                                                   | Meaning                             |
|--------------------------------------------------------------------------|-------------------------------------|
| `id`                                                                     | Visible `VULN-XXXXXX` identity.     |
| `repo_path`                                                              | Report key.                         |
| `file`, `type`, `description`, `code_context`, `severity`, `line_number` | Finding evidence.                   |
| `status`, `proposed_fix`                                                 | Remediation lifecycle.              |
| `rule_id`, `language`, `finding_fingerprint`                             | Migrated identity/retrieval fields. |

No secondary fingerprint index is used because of a DuckDB 1.1.x update limitation; lookup remains bounded by repository.

### Conversation tables

#### `memory_threads`

One stable row per `client_id` plus `repo_path`. In the current flow, `client_id` is the opaque server user ID, and `repo_path` is the GitHub report key. The historical column name does not make a browser client ID authoritative.

Fields include summary, state, created, updated, and last-accessed timestamps.

#### `memory_messages`

Stores:

- UUID and thread/run references;
- per-thread sequence number;
- role and message type;
- redacted content and content hash;
- estimated tokens;
- tool metadata JSON;
- creation and expiry.

Message types are `SYSTEM`, `TEXT`, `TOOL_REQUEST`, and `TOOL_RESULT` as used by the memory service.

### Graph orchestration tables

#### `graph_checkpoints`

Stores LangGraph4j state snapshots after every node execution.

- `checkpoint_id`: Unique identifier for the checkpoint.
- `run_id`: The remediation run ID.
- `thread_id`: The conversation thread ID.
- `graph_name`: The name of the graph being executed.
- `node_name`: The graph node that produced this state.
- `iteration`: The iteration loop counter.
- `state_json`: The serialized `WorkflowState` or `RemediationState` (redacted).
- `state_hash`: SHA-256 of `state_json` for tamper detection.
- `parent_checkpoint_id`: For resuming interrupted or failed runs.
- `interrupt_reason`: Non-NULL when graph is paused. (Note: Human approval pauses have been replaced with autonomous progression).
- `created_at`: Checkpoint timestamp.
- `expires_at`: Expiration for 30-day TTL.

#### `graph_node_events`

Tracks execution metrics and outcomes for individual graph nodes.

- `event_id`: Unique identifier for the event.
- `run_id`: The remediation run ID.
- `checkpoint_id`: The checkpoint associated with this event.
- `graph_name`: The name of the graph being executed.
- `node_name`: The executed graph node.
- `event_type`: The type of event (e.g., `NODE_START`, `NODE_END`, `TOOL_CALL`).
- `iteration`: The iteration loop counter.
- `duration_ms`: Node execution duration.
- `detail_json`: Event-specific payload (redacted).
- `status`: Node execution status (e.g., `OK`, `ERROR`, `TIMEOUT`).
- `created_at`: Event timestamp.
- `expires_at`: Expiration for 7-day TTL.

#### `subagent_state`

Tracks state handoffs and boundaries for multi-agent delegation (Phase 3+).

- `state_id`: Unique state record identifier.
- `run_id`: The remediation run ID.
- `parent_agent`: Name of the parent delegating agent.
- `child_agent`: Name of the delegated sub-agent.
- `handoff_direction`: The direction of handoff (e.g., `FORWARD`, `RETRY`).
- `context_json`: Serialized sub-agent input context (redacted).
- `result_json`: Serialized sub-agent execution result (redacted).
- `status`: Execution state of the sub-agent.
- `sequence_number`: Ordering of sub-agent transitions.
- `created_at`: Event timestamp.
- `expires_at`: Expiration for 30-day TTL.

### Agent-run tables

#### `token_usage`

Stores LLM token consumption metrics per request, joined with finding and repository context.

* `id` (VARCHAR PK): Random UUID.
* `user_id` (VARCHAR): The authenticated user who initiated the prompt.
* `repo_path` (VARCHAR): The structured repository and branch context (e.g., `github:{repoId}:{branch}`). This is reliably synced from the originating memory thread.
* `vulnerability_id` (VARCHAR, nullable): The finding ID if the usage occurred during remediation.
* `thread_id` (VARCHAR): The originating conversation. Tracks both UI streaming chat tokens and agentic loops.
* `run_id` (VARCHAR, nullable): The agent run ID.
* `model_name` (VARCHAR): Dynamically resolved identifier for the underlying model/provider, populated from `auditagent.llm.openai.model-name` configuration instead of a framework literal.
* `tokens` (INTEGER): Total tokens consumed (prompt + completion). Tracks standard calls and Flux-streamed chat responses.
* `created_at` (TIMESTAMP): Insertion time.

#### `agent_runs`

The durable lifecycle record:

- thread, finding, fingerprint, report/workspace key;
- phase and status;
- iteration, retry, max iteration;
- patch/build/rescan/test booleans;
- checkpoint JSON;
- final summary and safe error;
- create/update/complete timestamps.

#### `agent_steps`

Stores redacted tool actions, arguments, results, duration, status, and idempotency key. `(run_id, idempotency_key)` is unique.

#### `verification_results`

Stores kind (`BUILD`, `RESCAN`, `TEST`), status (`PASS`, `FAIL`, `SKIPPED`), redacted evidence, optional exit code, and timestamp.

#### `run_changes`

Stores file, before/after SHA-256, backup path, and change state. Current applied changes use `APPLIED`.

### Approved memory tables

#### `repository_memories`

Stores repository-scoped approved and rejected remediation:

- finding fingerprint, rule, category, type;
- language, framework, file pattern;
- title, summary, root cause, remediation pattern (or rejection reason);
- deterministic search text;
- confidence, source run, approval flag (`TRUE` for merged, `FALSE` for rejected), usage count, timestamps.

The repository/fingerprint pair is unique. Both positive memory (on PR merge) and negative memory (on manual reject) are written here and indexed in FTS.

#### `global_remediation_patterns`

Stores cross-repository security intelligence (positive fixes and negative anti-patterns) keyed by Semgrep rule ID:

- `pattern_id` (primary key);
- `rule_id` (not null, unique index `idx_global_pattern_rule`);
- `category`;
- `positive_pattern` (abstract remediation patterns synthesized from merged PR diffs);
- `negative_pattern` (anti-pattern warnings synthesized from failed agent trajectories);
- `confidence`, `source_run_id`, `created_at`, `updated_at`.

Accumulation semantics:
When saving new positive or negative patterns for an existing `rule_id`, patterns accumulate rather than overwrite. Existing and new text are concatenated using a `\n---\n` separator and capped at 4,000 characters via SQL `LEFT()`. Managed through `saveGlobalPatternTx` and queried via `getGlobalPattern`.

#### `memory_fts_state`

Tracks source version, indexed version, last result, and timestamp so full-text rebuilding is explicit and serialized.

### Authentication and GitHub tables

#### `app_users`

Maps stable server UUID to GitHub numeric ID, login, display name, and avatar.

#### `github_authorizations`

Stores AES-GCM ciphertext for access/refresh tokens and expiry timestamps.

#### `user_sessions`

Stores only session hash, user, CSRF token, and lifetime timestamps.

#### `oauth_states`

Stores only OAuth state hash and encrypted PKCE verifier with a ten-minute expiry.

OAuth-state and user-session expiry values are generated in UTC and stored in DuckDB `TIMESTAMP` columns without a
timezone marker. Queries and retention cleanup therefore compare them with
`CURRENT_TIMESTAMP AT TIME ZONE 'UTC'`. Comparing these fields with raw `CURRENT_TIMESTAMP` would reinterpret the
stored value in DuckDB's configured timezone and could expire a fresh login immediately on a non-UTC server. This is
a query-semantics rule and requires no schema migration. OAuth access/refresh expiry is likewise generated and
evaluated as UTC `LocalDateTime` values in Java.

#### `managed_repositories`

Stores repository and installation identity, owner/name/full name, clone URL, default branch, privacy, last known permission, and update time. Operations still revalidate current access.

#### `scan_snapshots`

Connects repository/branch to base SHA, report key, temporary workspace path, creator, and time. The scan workspace is later deleted; its path is historical metadata, not a recovery source.

#### `run_publications`

Publication checkpoint saves use a transaction that updates by `run_id` and inserts only when the row does not yet
exist. Schema version 3 removes the optional `(repository_id, pr_number)` secondary index because DuckDB 1.1.x cannot
reliably update `pr_number` while that index exists. Webhook lookup remains correct and repository-bounded through a
table scan, while one durable publication row per run remains protected by the `run_id` primary key.

Stores:

- owner and repository/installation;
- report key and pinned base;
- remediation workspace;
- planned branch;
- PR digest and generator/time;
- commit and push checkpoint;
- PR number, URL, state;
- safe publication error.

#### `publication_checkpoints`

Stores finer-grained publication progress markers (`COMMITTED`, `PUSHED`, `PR_CREATED`) for resume and retry capabilities, though primary tracking relies on `run_publications`.

#### `webhook_deliveries`

Deduplicates GitHub delivery IDs and stores event type/time.

## Finding identity and rescan behavior

Fingerprint material:

```text
lower(trim(ruleId))
normalized lower path with /
lower code with each line trimmed and joined by spaces
```

SHA-256 produces the fingerprint. On report save, a matching repository/fingerprint reuses the earlier finding ID, status, and proposed fix. This prevents every rescan from resetting human and remediation state.

## Thread identity and ownership

- General key: `github:general:<githubUserId>`.
- Repository key: `github:<repositoryId>:<branch>`.
- Unique thread lookup uses server `userId` plus key.
- Controllers verify `memoryThreadBelongsTo(threadId, userId)` before reading or acting.

## Conversation context selection

The memory service reconstructs LangChain4j messages from durable rows.

1. Tool requests and following tool results are grouped as one indivisible turn.
2. The first two turns are retained, normally preserving system/task context.
3. Newest complete turns are added backward until the configured 12,000-token estimate is reached.
4. A system notice is inserted when middle history was compacted.
5. Token estimate is approximate characters divided by four.

General chat and run-specific tool histories are queried separately.

## Redaction and retention

Before persistence, patterns mask:

- AWS access keys matching `AKIA...`;
- bearer tokens;
- password/passwd/secret/token/API/access-key assignments;
- PEM private keys.

Default caps:

- chat: 8,000 characters;
- tool: 4,000 characters;
- public safe errors: 500 characters;
- PR summary: 12,000 characters;
- PR diff: 200,000 characters.

Hourly scheduled cleanup:

- deletes expired message and agent-step detail;
- deletes expired user sessions and OAuth states using the same UTC-normalized clock as authentication;
- nulls old run error detail;
- nulls old verification evidence.

Run outcome, hashes, summary, status, publication, and approved memory remain until explicit deletion.

## Approved memory retrieval

### Categories and aliases

The concept catalog recognizes injection/SQLi, XSS, path traversal, command injection, unsafe deserialization, CSRF, and XXE. It infers Spring, React, Django, Flask, Express, or Angular when present.

### Candidate sources

- Cross-repository `global_remediation_patterns` queried directly by Semgrep rule ID.
- DuckDB BM25 FTS when available for repository-scoped memories.
- Always-on structured metadata fallback.
- Maximum 50 candidates from each query path by default.
- Maximum eight returned prompt memories.

### Ranking priority

Large deterministic weights enforce:

1. exact fingerprint;
2. exact rule ID;
3. category;
4. vulnerability type;
5. BM25;
6. language;
7. framework;
8. file extension pattern;
9. confidence;
10. prior usage and recency.

Usage counters increment for selected memories.

### FTS lifecycle

DuckDB's `fts` extension is loaded or installed. The index covers title, summary, root cause, remediation pattern, and search text using Porter stemming, English stopwords, accent stripping, and lowercase normalization. A rebuild occurs when source/index versions differ. Failure sets `ftsAvailable=false`, records a safe failure, and preserves metadata retrieval.

This is lexical retrieval, not vector similarity.

## Deletion semantics

- Forget thread: deletes steps, verification, changes, runs, messages, and thread. Publication records are not explicitly deleted by `deleteMemoryThread`; unpublished work is first discarded, while remote publication is protected.
- Forget repository memory: deletes only `repository_memories` for the report key and rebuilds FTS.
- Logout: deletes only the current session.

## Migration rules for future work

- Add ordered, transactional migration logic in `DatabaseService`.
- Never reset or replace the database to add a feature.
- Preserve existing reports and finding identity.
- Create a recovery backup before a high-risk migration.
- Use prepared statements for user/query-derived values.
- Redact and cap new persisted prose.
- Decide explicit retention and deletion behavior.
- Add idempotency, ownership, migration, and restart tests.
