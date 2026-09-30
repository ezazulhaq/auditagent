# Testing and quality

## Test commands

### Backend

Windows:

```powershell
.\mvnw.cmd test
```

Unix:

```bash
./mvnw test
```

An opt-in installed-Semgrep smoke test exercises the real binary through the managed short-temp environment. Run it only where Semgrep is on `PATH`:

```powershell
.\mvnw.cmd "-Dauditagent.semgrep.integration=true" "-Dtest=ManagedProcessRunnerTest#installedSemgrepCompletesWithManagedShortTemp" test
```

### Frontend

```bash
cd frontend
npm run test:run
npm run lint
npm run build
```

Interactive frontend tests:

```bash
npm test
```

## Backend automated coverage

### Application and filter

- Spring context loads.
- Protected mutation without matching CSRF is blocked.
- Authenticated mutation with matching CSRF is allowed.

### Authentication and encryption

- PKCE login stores only hashed state and encrypted verifier.
- Callback persists encrypted expiring authorization and hashed session.
- Browser-state mismatch is rejected before state consumption.
- OAuth-state/session lookup and cleanup preserve future UTC expiries when PostgreSQL runs in a non-UTC timezone.
- GitHub authorization, repository, and publication checkpoint updates execute against the supported PostgreSQL version.
- Token encryption is randomized authenticated encryption.

### GitHub publication and webhook

- One bot commit is published and an existing PR is reused.
- Retry after push does not create another commit.
- Webhook signature is validated.
- Delivery is processed once.
- Invalid signature is rejected before recording.
- Processing failure releases delivery for retry.

### Authorization and publication

- Rejection cleans workspace and never publishes.
- Non-owner cannot preview or approve.
- Duplicate publication trigger does not create a second PR.
- Revoked App write permission blocks publication.
- Stale publication digest conflicts before writes.
- Advanced base conflicts before publish.
- Wrong finding binding is rejected.
- Run/publication records exist before clone and stale clone becomes conflict.

### Agent tools and process safety

- Server secrets are stripped and runtime directories are contained.
- Lexical traversal is rejected.
- Symlink file escape is rejected when supported.
- Symlink backup-directory escape is rejected when supported.
- A subprocess that fills stderr before writing stdout completes without a pipe deadlock.
- Scan timeout emits heartbeats and terminates both the root and child JVM fixture.
- Semgrep receives the same short per-run OS-temp directory through `TEMP`/`TMP`/`TMPDIR`, and that directory is removed after exit.
- A non-zero root exit terminates an observed surviving child before control returns to workspace cleanup.
- Semgrep exit 1 succeeds; other exits, empty output, and malformed JSON fail closed.
- Timed-out managed scans emit `SCAN_TIMEOUT`, skip report/snapshot persistence, and clean the workspace.

### Memory and database

- Migration is idempotent and preserves finding state.
- Exact fingerprint outranks metadata and repositories remain isolated.
- Only one recoverable run exists per repository context.
- FTS can build when extension is available.
- Conversation survives service recreation.
- Tool request/results remain paired.
- Secrets are redacted and content capped.
- Context compaction preserves complete turns.
- Clear retains durable history while forget deletes it.

### Agent loop

- one-turn final response;
- tool then complete;
- full remediation cycle;
- maximum iteration failure;
- empty-response abort;
- tool-error self-correction;
- unknown-tool handling;
- progress event structure;
- conversation persistence;
- fallback prompt without a skill.

## Frontend automated coverage

- Fresh scan opens the Audit Report tab and loads its authenticated PDF artifact.
- Cached and fresh result normalization match.
- Missing terminal scan event is a protocol error.
- Terminal scan errors preserve the backend code and safe message.
- SSE whitespace, CRLF, comments, chunking, multiline data, EOF flush, and malformed JSON.
- Stable High/Medium/Low/Info finding ordering without source-array mutation.
- Multi-term metadata search, combined severity/status filtering, dynamic lifecycle choices, reset/no-result recovery, and unchanged finding selection.
- Drawer polls webhook status and shows PR state.
- Preview must load before publish action.
- Remediation controller clears stale state for a new scan.
- Decision binds run, finding, and digest and records PR state.
- Old memory restore response is ignored.
- Scan panel progress/error display.
- Scan reducer preserves last good report, commits terminal data atomically, and ignores stale events.
- Scan timing uses wall-clock timestamps, remains correct after a two-hour throttling gap, and is not reset by heartbeats.
- PDF Blob loading and iframe rendering.
- PDF and Markdown download actions, server filenames, and repository/branch export query encoding.

## Test design patterns

- Controller/services receive interfaces (`SourceControlProvider`, `PullRequestProvider`) to allow fakes.
- `createAuditApi()` accepts a fetch implementation.
- `App` accepts an API object.
- Time-consuming external systems should be mocked in unit tests.
- JGit integration tests use temporary repositories/remotes.
- Symlink tests account for platforms without symlink permission.
- FTS test tolerates unavailable extension where appropriate.

## Manual acceptance checklist

### Authentication

- Sign-in succeeds with matching browser state.
- Sign-in succeeds within the state lifetime when the server timezone is non-UTC.
- Callback in another browser fails.
- Expired session returns 401.
- Mutation without CSRF returns 403.
- Sign-out prevents further protected access.

### Repository and scan

- Only installed repositories appear.
- Revoked read access blocks branch/scan/report.
- Default branch is preferred.
- Cache loads for unchanged head.
- Force rescan bypasses cache.
- Changed head triggers fresh scan.
- Progress and final report agree.

### Responsive interface

- Authentication, scan setup, dashboard, findings, report, remediation drawer, and chat remain usable at 320px, 768px, 1024px, and wide desktop viewports.
- Workspace tabs remain reachable by horizontal scrolling on narrow screens.
- Finding rows preserve severity, identity, location, status, and selection at mobile widths.
- The finding drawer and chat do not overflow the mobile viewport; Escape closes each open overlay.
- Keyboard focus remains visible for links, buttons, inputs, and selects.
- Reduced-motion preference suppresses non-essential transitions and animation.
- Repository selection, scan submission, filters, finding selection, diff preview, structured decision, retry, recovery, forget, PDF/Markdown report download, chat submission, and sign-out still invoke their established handlers.

### Reporting

- Generated bytes begin with a valid PDF signature and can be reopened and text-extracted with PDFBox.
- PDF text includes the enterprise title, repository identity, and finding evidence.
- Markdown includes the executive summary, severity counts, complete finding details, and fenced source code.
- Export orchestration revalidates repository access, emits the correct content type, normalizes repository/branch filenames, supports PDF and Markdown, and rejects unsupported formats.
- The controller returns an attachment with the generated filename, explicit media type and length, `private, no-store`, and `nosniff` headers.
- Manual acceptance verifies PDF viewing plus PDF/Markdown downloads at mobile and desktop widths. Browser PDF viewers differ, so also test the download fallback in each supported browser.

### Remediation

- A second run for same active finding is blocked.
- No branch exists before publication step.
- Tool path traversal and symlinks fail.
- Failed verification skips publication.
- Preview matches workspace diff and evidence.
- Stale preview or changed base blocks publication.
- Rejection creates no remote state.
- Publication creates exactly one traceable PR.
- Retry after simulated push/PR failure avoids duplicate commit/PR.

### Finalization and memory

- Open PR shows `PR_OPEN`.
- Merge webhook marks `FIXED` and creates approved memory.
- Close-without-merge returns finding to `DETECTED`.
- Duplicate webhook is harmless.
- Restart marks active work interrupted.
- Unchanged interrupted workspace resumes.
- Changed workspace conflicts.
- Forget operations affect only their intended scope.

## Release gate

Before release:

1. Backend tests pass.
2. Frontend tests, lint, and build pass.
3. Static frontend is rebuilt if source changed.
4. No secrets or local DB/log files are staged.
5. API/schema/config/security docs are updated.
6. Migration tested against a copy of an older DB.
7. GitHub sandbox installation completes scan→PR→merge lifecycle.
8. Failure/retry and restart recovery are exercised.
9. Production config uses HTTPS, managed root, secret manager, and worker sandbox.
10. `SPRING_PROFILES_ACTIVE` selects the intended deployment profile, and no ignored populated profile file is staged or packaged as a required secret source.

## Known coverage gaps

- Full Semgrep binary integration and large real-repository performance remain environment-dependent. The default suite uses deterministic fixtures; the opt-in installed-binary smoke test covers the managed short-temp invocation with one minimal local rule and JavaScript file.
- OAuth HTTP/API pagination and rate-limit behavior lack end-to-end coverage.
- CORS and secure-cookie auto-detection deserve focused tests.
- Retention scheduler edge cases beyond authentication-state/session timezone handling need tests.
- Full production LLM API/Semgrep/build integration is environment-dependent.
- Frontend recovery UI does not have exhaustive tests for every run status.
- Accessibility, large diff/report performance, and browser matrix are not automated.
