# Change guide

## First principle

Change the smallest coherent boundary, preserve durable/security invariants, add tests, and update documentation. Do not use historical local-path or chat-publication code as the basis for new shared behavior.

The versioned `.githooks/pre-commit` guard requires a staged `doc/*.md` update when implementation, configuration, rules, skills, build, or runtime files are staged. Enable it after cloning with `scripts/install-git-hooks.ps1` or `scripts/install-git-hooks.sh`. The hook checks presence only; the author remains responsible for updating every affected guide.

## Common change recipes

### Add a new API endpoint

1. Choose the controller by business boundary.
2. Confirm whether it must be authenticated and whether it mutates.
3. For mutation, rely on the API filter and send CSRF from frontend.
4. Resolve user from `GitHubAuthService`, then verify thread/run/repository ownership.
5. Accept repository IDs, not paths/usernames.
6. Redact public errors.
7. Add controller/filter/service tests.
8. Update API, feature, security, and source-inventory docs.

### Add a scan language

1. Add extension mapping in language detection.
2. Add `rules/<language>` and validate every YAML rule with Semgrep.
3. Add language label parsing if needed.
4. Review exclude patterns.
5. Add parser/selection tests.
6. Update rule counts and scanning docs.

Note: TypeScript detection selects `rules/typescript`; Kotlin currently maps to Java rules.

### Add or update Semgrep rules

1. Place YAML under the correct language/framework/category path.
2. Use stable rule IDs and clear messages/severity.
3. Add positive and negative fixtures if a rule-test convention is introduced.
4. Run Semgrep validation and a representative scan.
5. Consider fingerprint impact: rule ID/code changes can create new identities.
6. Update the rule inventory count.

### Add an agent tool

1. Define a narrow schema in `LlmService.buildToolSpecifications()`.
2. Add one dispatcher case.
3. Implement secure behavior in `AgentToolService`.
4. Classify mutating/idempotent behavior.
5. Persist relevant step/change/evidence.
6. Add path, secret, timeout, output-cap, error, and resume tests.
7. Update the patch-engineer skill and AI/security docs.
8. Never expose publication or authorization as a model tool.

### Support another build system

1. Detect a stable root marker.
2. Use argument arrays, never a shell string.
3. Configure the managed process environment.
4. Set timeout and bounded output.
5. Define explicit success/failure/skip prefixes.
6. Update verification parsing and tests.

### Change verification policy

Update together:

- tool result semantics;
- `LlmService` booleans and final gate;
- persisted verification status;
- PR preview;
- publication digest inputs;
- run/finding states;
- recovery;
- tests and docs.

Never let model prose replace deterministic evidence.

### Add a finding or run state

1. Add enum.
2. Define allowed transitions and recoverability.
3. Update database queries such as recoverable/active checks.
4. Update startup interruption rules.
5. Update managed coordinator transitions.
6. Add frontend hydration, drawer, chat banner, and action behavior.
7. Add lifecycle/restart tests and state documentation.

### Change GitHub publication logic

Treat `GitHubAuthService`, `ApiSecurityFilter`, `RemediationWorkflowService`, `PullRequestLifecycleService`, `GitHubProvider`, `GitHubApiClient`, and `DatabaseService` as one boundary.

Preserve:

- owner/run/finding binding;
- fresh user/App permissions;
- base SHA;
- digest and hashes;
- structured decision;
- idempotent commit/push/PR;
- checkpoint-before-next-action;
- merge-only `FIXED`;
- webhook HMAC/replay.

### Change frontend scan state

- Preserve normalized result shape.
- Preserve last successful result during subsequent failures.
- Ignore stale request IDs.
- Handle missing terminal SSE as an error.
- Add reducer/controller/component tests.

### Change memory or retrieval

- Add schema migration in `DatabaseService`.
- Keep repository scoping and approved-only filtering.
- Bound candidate count and query text.
- Keep metadata fallback.
- Preserve complete tool turns in context.
- Redact and define retention.
- Do not introduce embeddings/vector search without an explicit architecture decision that replaces current project constraints.

### Add configuration

1. Add typed property and validation.
2. Add YAML default or environment mapping.
3. Add Spring metadata.
4. Test invalid/default behavior.
5. Document default, unit, risk, and production guidance.

### Change report HTML

- Escape every dynamic value.
- Review iframe behavior and downloaded-file XSS.
- Avoid adding remote scripts without a deployment/CSP decision.
- Test empty and special-character findings.

## Database migration procedure

1. Copy a representative old DB.
2. Add ordered idempotent migration.
3. Begin transaction.
4. Create/add without dropping user data.
5. Backfill deterministically if required.
6. Add indexes after data is valid.
7. Record schema version.
8. Roll back on failure.
9. Test second startup.
10. Verify finding IDs/status/proposed fixes and existing reports.

## Documentation impact matrix

| Change                       | Documents                       |
|------------------------------|---------------------------------|
| User-visible behavior        | User guide, feature catalog     |
| Business lifecycle           | Product guide, architecture     |
| Endpoint/payload/SSE         | API reference, frontend/backend |
| DB table/retention/retrieval | Data guide                      |
| Model/tool/verification      | AI guide, security model        |
| Auth/path/Git/webhook        | Security model                  |
| Env/config/startup           | Operations                      |
| Test behavior                | Testing                         |
| New file/module/status/term  | Source inventory/glossary       |

## Review checklist

### Correctness

- Does the feature work for success, empty, retry, stale, and restart cases?
- Is state persisted before irreversible next steps?
- Are cached/restored and fresh paths equivalent?

### Security

- Is identity server-derived?
- Is repository access current?
- Are mutations CSRF-protected?
- Can a model/browser alter authorization or target path?
- Are secrets absent from logs, errors, DB prose, commands, and URLs?
- Are paths and process environments contained?
- Is publication bound to exact content?

### Operations

- Are timeout, cap, cleanup, retention, and failure recovery defined?
- Does it work behind HTTPS proxy and through SSE timeout?
- Does it need migration or key rotation?

### Quality

- Tests cover positive and negative paths.
- Frontend build and static assets are refreshed.
- Documentation and configuration metadata are current.
- Unrelated user changes are preserved.
