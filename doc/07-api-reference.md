# API reference

## Common rules

- Base path: `/api`.
- Cookies: `AUDITAGENT_SESSION` is sent with credentials.
- Protected mutation header: `X-CSRF-Token`.
- JSON content type for request bodies.
- Long operations return `text/event-stream`; each `data:` payload is JSON.
- Shared repository operations accept a GitHub `repositoryId` and branch, never a local path.

### Unauthenticated routes

- `GET /api/auth/session`
- `GET /api/auth/github/login`
- `GET /api/auth/github/callback`
- `OPTIONS /api/**`
- `POST /api/webhooks/github` (HMAC authenticated)

All other `/api/**` routes require a session. All protected non-GET/HEAD routes require CSRF.

## Authentication

### `GET /api/auth/session`

Response:

```json
{
  "configured": true,
  "installationUrl": "https://github.com/apps/example/installations/new",
  "authenticated": true,
  "user": {
    "id": "server-user-id",
    "login": "octocat",
    "name": "Octo Cat",
    "avatarUrl": "https://..."
  },
  "csrfToken": "opaque"
}
```

Unauthenticated responses omit `user` and `csrfToken`.

### `GET /api/auth/github/login`

Returns 302 to GitHub and sets the ten-minute OAuth-state cookie.

### `GET /api/auth/github/callback?code=...&state=...`

Validates browser/DB state and PKCE, creates a session, expires OAuth-state cookie, and redirects to configured frontend URL.

### `POST /api/auth/logout`

Invalidates the session and returns:

```json
{"authenticated": false}
```

## Repositories

### `GET /api/github/repositories`

Returns `ManagedRepository[]`:

```json
{
  "repositoryId": 42,
  "installationId": 9,
  "owner": "acme",
  "name": "payments",
  "fullName": "acme/payments",
  "cloneUrl": "https://github.com/acme/payments.git",
  "defaultBranch": "main",
  "privateRepository": true,
  "permission": "WRITE"
}
```

### `GET /api/github/repositories/{repositoryId}/branches`

Returns a JSON array of branch names after access revalidation.

## Threads and memory

### `POST /api/memory/threads`

Body:

```json
{"repositoryId": 42, "branch": "main"}
```

`repositoryId` may be null only for a general user thread. A selected repository requires a nonblank branch.

Response:

```json
{
  "threadId": "uuid",
  "messages": [],
  "activeRun": null,
  "publication": null,
  "activeFinding": null,
  "ftsAvailable": true
}
```

Only general chat messages (`run_id IS NULL`) are returned in `messages`; run details are represented by the active objects.

### `GET /api/memory/threads/{threadId}`

Returns the same shape after ownership validation.

### `DELETE /api/memory/threads/{threadId}`

Safely discards unpublished recoverable work and deletes conversation/run detail.

```json
{"threadId": "uuid", "forgotten": true}
```

### `DELETE /api/memory/repositories?repositoryId=42&branch=main`

Deletes approved memory for that repository/branch.

## Scan and report

### `POST /api/scan` — SSE

Body:

```json
{
  "repositoryId": 42,
  "branch": "main",
  "scannerName": "semgrep",
  "forceRescan": false,
  "threadId": "uuid"
}
```

Events:

- `progress`: `step`, `progress`, `message`; the `run_semgrep` stage also carries periodic liveness messages.
- `workspace_prepared`: `repository`, `branch`, `baseSha`.
- `cached`: terminal report payload.
- `complete`: terminal report payload.
- `error`: terminal failure payload with `code` equal to `SCAN_TIMEOUT` or `SCAN_FAILED` and a safe `message`; the stream then completes without saving a report or snapshot.

Terminal error example:

```json
{
  "type": "error",
  "code": "SCAN_TIMEOUT",
  "message": "Semgrep exceeded the configured timeout of 900 seconds."
}
```

Terminal report payload fields:

```json
{
  "type": "complete",
  "repositoryId": 42,
  "repository": "acme/payments",
  "branch": "main",
  "baseSha": "40-char SHA",
  "report_available": true,
  "html_report": "<!doctype html>...",
  "findings": [],
  "metadata": {},
  "message": "Scan complete..."
}
```

`report_available` tells current clients that authenticated artifacts can be loaded. `html_report` remains in scan and restore payloads for backward compatibility; the current frontend does not render or download it.

### `GET /api/reports?repositoryId=42&branch=main`

Returns the latest report payload with type `report`.

### `GET /api/reports/history?repositoryId=42&branch=main`

Returns a JSON array of `ScanHistoryRecord` objects representing past scans for the given repository and branch, ordered by creation time ascending.

### `GET /api/reports/export?repositoryId=42&branch=main&format=pdf`

Returns the latest report artifact after revalidating the authenticated user's current read access to the repository. `format` accepts `pdf`, `markdown`, or the `md` alias.

Successful response behavior:

| Format            | Content type                   | Filename suffix | Source                                                                                              |
|-------------------|--------------------------------|-----------------|-----------------------------------------------------------------------------------------------------|
| `pdf`             | `application/pdf`              | `.pdf`          | Generated in memory from persisted findings, metadata, repository, branch, and scanned base SHA.    |
| `markdown` / `md` | `text/markdown; charset=UTF-8` | `.md`           | Full-detail Markdown rendered from persisted report data and bound to the selected branch/base SHA. |

Both responses use `Content-Disposition: attachment` with a normalized repository/branch-derived filename, `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`, and an explicit content length. The frontend may still display the fetched PDF Blob in its authenticated report view.

Errors use the normal API error envelope. Typical failures are `400` for an unsupported format or missing scan, `401` for no session, `403` for revoked repository access, and `500` when a persisted artifact is unavailable or PDF generation fails.

## Chat

### `POST /api/chat` — SSE

Body:

```json
{
  "repositoryId": 42,
  "branch": "main",
  "threadId": "uuid",
  "message": "Explain the SQL injection"
}
```

Events:

- `token`: Streams the assistant's response iteratively.
- `complete`: Indicates the stream has finished.

## Remediation

### `POST /api/analyze` — SSE

Body:

```json
{
  "repositoryId": 42,
  "branch": "main",
  "threadId": "uuid",
  "vulnId": "VULN-ABC123"
}
```

Events:

- `workspace_prepared`: includes `runId`, repository, branch, base SHA.
- `agent_progress`: includes `runId`, `step`, `message`, iteration, max iterations.
- `graph_step`: indicates a state transition within the graph execution (Phase 4).
- `tool_detail`: provides detailed inputs and outputs of a tool executed during a step (Phase 4).
- `approval_ready`: includes `runId` and `preview` (automatically proceeds to publication).
- `analysis_complete`: terminal logical result with optional preview, and message.
- `status`: remediation failure.

`agent_progress.step` values currently include `agent_thinking`, `tool_call`, `tool_result`, `agent_nudge`, `agent_error`, and `agent_complete`.

### `GET /api/runs/{runId}/approval-preview` (Legacy/Debugging)

Response:

```json
{
  "runId": "uuid",
  "vulnerabilityId": "VULN-ABC123",
  "repository": "acme/payments",
  "baseBranch": "main",
  "baseSha": "...",
  "branchName": "auditagent/fix-vuln-abc123-12345678",
  "changedFiles": ["src/.../File.java"],
  "diff": "diff --git ...",
  "verification": {
    "BUILD": "PASS: ...",
    "RESCAN": "PASS: ...",
    "TEST": "PASS: ..."
  },
  "summary": "## Fix Summary ...",
  "approvalDigest": "sha256 hex"
}
```

### `POST /api/runs/{runId}/decision` (Legacy/Automated) — SSE

*Note: The `RemediationWorkflowGraph` now autonomously calls publication logic without requiring this endpoint.*

Body:

```json
{
  "vulnId": "VULN-ABC123",
  "decision": "APPROVE_AND_CREATE_PR",
  "approvalDigest": "reviewed digest",
  "reason": "optional text explaining why it was rejected"
}
```

The other decision is `REJECT`; its digest may be null, but `reason` will be stored in PostgreSQL and used as negative context in the FTS for future fixes.

Events:

- `publish_progress`: phase and message.
- `pr_created`: branch, commit, PR number/URL, `PR_OPEN`.
- `complete`: rejection result.
- `publish_failed`: safe message and current run status.

The stream reports domain failures as events after it starts; HTTP setup/validation failures may use status codes.

### `POST /api/runs/{runId}/retry-publish` — SSE

Allowed for approved `PUBLISH_FAILED` or eligible `INTERRUPTED` runs.

### `POST /api/runs/{runId}/resume?threadId=...` — SSE

Allowed for unapproved `INTERRUPTED` remediation whose hashes still match.

### `POST /api/runs/{runId}/discard`

Allowed only before remote publication makes discard unsafe.

```json
{"runId": "uuid", "status": "DISCARDED", "rolledBack": true}
```

## Observability

### `GET /api/observability/tokens`

Retrieves token consumption statistics aggregated per repository and model for the authenticated user.

**Response (200 OK):**

```json
[
  {
    "repoPath": "user/repo:main",
    "modelName": "spring-ai",
    "totalTokens": 14250,
    "callCount": 12
  }
]
```

## Skills

### `GET /api/skills`

Returns discovered skill objects. This route is protected by session via the API filter even though the controller itself does not request the user object.

## Documentation

### `GET /api/docs`

Returns a JSON array of all available `.md` documentation filenames within the repository's `doc/` directory.

### `GET /api/docs/{filename}`

Returns the raw `text/plain` markdown content of the requested documentation file. Access is restricted exclusively to files within the `doc/` directory to prevent directory traversal attacks.

## GitHub webhook

### `POST /api/webhooks/github`

Receives asynchronous GitHub App webhook events (e.g., `pull_request` close and merge actions) to synchronize backend state and automatically update grouped vulnerability statuses to `FIXED`.

Required headers:

- `X-Hub-Signature-256`: HMAC signature for validation.
- `X-GitHub-Delivery`: Unique delivery ID.
- `X-GitHub-Event`: Event type (must be `pull_request`).

Behavior:

- Validates HMAC signature.
- Deduplicates using delivery ID.
- Transitions associated vulnerabilities to `FIXED` upon PR merge.
- Returns accepted/duplicate/ignored flags. Signature failure is 403. A duplicate delivery returns success without processing again.

## Error responses

Typical JSON:

```json
{"message": "Safe redacted explanation"}
```

| HTTP status | Meaning                                                                                                    |
|-------------|------------------------------------------------------------------------------------------------------------|
| `400`       | Missing/invalid input.                                                                                     |
| `401`       | No valid session, produced by security filter.                                                             |
| `403`       | CSRF, ownership, GitHub access, signature, or authorization failure.                                       |
| `409`       | State conflict such as stale base, invalid lifecycle, or changed workspace.                                |
| `500`       | Unhandled integration/runtime failure; not all exceptions have a dedicated advice mapping.                 |
