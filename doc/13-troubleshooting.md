# Troubleshooting

## Sign-in button is disabled

**Cause:** required GitHub App or encryption configuration is blank.

**Check:** `/api/auth/session` returns `configured: false`; verify App ID, client ID/secret, private key, webhook secret, and token-encryption key.

**Remedy:** configure server secrets and restart. Do not put them in frontend variables.

## GitHub callback says state is invalid, expired, or from another browser

**Cause:** OAuth took over ten minutes, cookie was blocked, callback opened in a different browser, state was reused, or public callback/cookie settings are wrong.

**Check:** callback URL, HTTPS, SameSite behavior, browser cookies, and clock accuracy. The server may use a non-UTC timezone: current authentication persistence stores UTC expiry values and normalizes the DuckDB comparison clock to UTC.

**Remedy:** restart the backend after deploying the current code, then start sign-in again in the same browser. A prior state and authorization code are one-time values and cannot be reused. Do not weaken state validation or change the host timezone as a workaround.

## Authenticated page receives 401

**Cause:** session cookie missing, expired, invalid, or not sent through proxy/origin.

**Check:** cookie path `/`, credentials included, frontend origin, HTTPS Secure behavior, `user_sessions` expiry.

**Remedy:** sign in again or fix reverse-proxy cookie/origin configuration.

## Mutation receives 403 "CSRF token is invalid"

**Cause:** frontend did not first load session, token is stale, or custom client omitted header.

**Check:** `/auth/session` response and `X-CSRF-Token`.

**Remedy:** rediscover session and send its current token with every protected non-GET/HEAD call.

## No repositories appear

**Cause:** App not installed for the user, installation has no selected repositories, OAuth user lacks access, or GitHub pagination limit hides entries.

**Check:** installation URL and GitHub App installation settings; server GitHub API response.

**Remedy:** install/configure the App. Large installations may require implementing pagination beyond 100.

## Branch list fails

**Cause:** stored repository metadata is stale or current user access was revoked.

**Check:** reload repositories, then retry; inspect safe GitHub API status.

**Remedy:** reselect repository or restore GitHub permission. Do not bypass revalidation.

## Scan returns no findings and zero files unexpectedly

**Cause:** Semgrep missing, empty output, unsupported/error exit, no readable clone, or all paths excluded.

**Check:** backend logs for Semgrep command/exit; run `semgrep --version`; verify rule directory and workspace.

**Remedy:** install Semgrep, fix `PATH`/rules, or adjust intentional exclusions. Empty output, malformed JSON, and unsupported exit codes now fail without replacing the last valid report.

## Semgrep exits 2 with RPC "Expected a number, got ''"

**Cause:** on Windows, Semgrep's native RPC subprocess can fail `socketpair` creation when `TEMP`/`TMP`/`TMPDIR` point to a deeply nested scan-workspace path. The RPC parser then receives missing output and reports "Expected a number"; repeated messages do not by themselves mean a bundled YAML rule is malformed.

**Check:** inspect Semgrep's detailed log for `Unix_error` and `socketpair`, compare the effective `TEMP` path length, and validate the same rule set with a short user-owned temp directory. Also check for an RPC descendant that survived the root process and retained workspace files.

**Remedy:** deploy the managed runner that assigns one random short `aa-*` OS-temp child per scan, terminates observed descendants on every root exit, and deletes the temp child afterward. Keep `java.io.tmpdir` short, private, writable, and outside the nested managed scan clone. The scan remains a terminal `SCAN_FAILED`, persists no partial report or snapshot, and preserves the last valid UI report.

## Scan remains at Run Security Analysis

**Cause:** on an older deployment, sequential stdout/stderr reads can deadlock when one Semgrep pipe fills. On the current implementation, investigate a genuinely long scan, unavailable process termination, or a deadline configured too high.

**Check:** confirm 15-second `run_semgrep` heartbeat events, compare the UI wall-clock timer with backend start time, inspect the Semgrep process tree, and verify `auditagent.scanner.timeout-seconds` (default 900).

**Remedy:** upgrade to the concurrent managed runner. The current scanner drains both pipes, times out the entire process tree, emits `SCAN_TIMEOUT`, skips persistence, and retains the last good report. For an already hung old instance, stop the backend first, then terminate the Semgrep tree and safely remove only its managed scan workspace before restart.

## Scan uses an old report

**Expected cache rule:** cache is used only when latest snapshot SHA equals current branch head and force rescan is false.

**Check:** UI force-rescan flag, reported `baseSha`, GitHub branch head.

**Remedy:** enable force rescan. If SHAs differ but cache appears, inspect snapshot/report-key data.

## Scan fails after branch changed

**Cause:** branch advanced between head lookup and clone.

**Check:** `STALE_BASE` in logs/message.

**Remedy:** rerun scan. Exact-SHA rejection is a safety control.

## Chat says conversation does not belong to user

**Cause:** stale thread ID from another account/repository selection.

**Remedy:** restore/create the thread for the current authenticated user and selection. Never accept the old ID by weakening ownership.

## Chat has no findings

**Cause:** no scan snapshot/report for selected repository/branch.

**Remedy:** run a scan. Browser-supplied finding lists are intentionally ignored.

## Model returns empty response or times out

**Cause:** LLM API access, throttling, credentials, model refusal, network, or timeout.

**Check:** region/model access, AWS identity, backend safe logs, call timeout.

**Remedy:** correct access, retry, or tune timeout. Three consecutive empty responses abort by default.

## Gemini returns HTTP 400 "missing a thought_signature"

**Cause:** Gemini's OpenAI-compatible endpoint strictly requires a proprietary `thought_signature` on every tool call in a multi-turn conversation. If the signature is dropped during serialization by LangChain4j, the next API call fails.

**Check:** backend logs for the `GeminiThoughtSignatureInterceptor` warning `Could not find thought_signature in tool_call!`, or an HTTP 400 response containing `Function call is missing a thought_signature in functionCall parts`.

**Remedy:** ensure the `GeminiThoughtSignatureInterceptor` is correctly registered in the OkHttp builder. The interceptor extracts the signature from `extra_content.google.thought_signature` and re-injects it into the exact same structure on the next outgoing request.

## Multi-agent graph throws "Maximum number of iterations (25) reached!"

**Cause:** LangGraph4j has a built-in recursion limit (default 25 supersteps). The 15-stage multi-agent Star Topology involves back-and-forth delegation between the `supervisor` and the worker stages, which easily exceeds 25 transitions.

**Check:** backend logs for `java.lang.IllegalStateException: Maximum number of iterations (25) reached!` during agent execution.

**Remedy:** configure `.recursionLimit(150)` (or an appropriately high number) on the `CompileConfig.Builder` when compiling the state graph. Note that this property is named `recursionLimit`, not `maxIterations`.

## Remediation cannot start

Common causes:

- no scan exists;
- finding does not belong to latest selected report;
- finding is not `DETECTED`/`PATCH_FAILED`;
- another recoverable run has the same fingerprint;
- repository access was revoked.

Use the latest scan and restore/discard the existing run as appropriate.

## Patch cannot find original code

**Cause:** model snippet is not an exact unique match or file changed.

**Expected behavior:** tool returns an error and applies nothing.

**Remedy:** agent should reread and use a more specific block. Avoid line-number fallback in new managed tool behavior.

## Build or tests are skipped

**Cause:** no root `pom.xml` or `build.gradle`.

**Behavior:** explicit `BUILD SKIPPED`/`TEST SKIPPED` can satisfy the current verification policy.

**Remedy:** add support for the repository's build system if skips are not acceptable. Update tool, prompt, policy, tests, and docs together.

## Target rescan still fails

**Cause:** any Semgrep finding remains in the patched file, even one unrelated to the target.

**Check:** raw rescan count and file.

**Remedy:** inspect findings. If policy should require only target fingerprint disappearance, implement fingerprint-aware rescan rather than weakening result parsing informally.

## Verification completed but no PR appears

**Cause:** one required boolean/evidence is missing, max iterations reached, no changed file exists, or preview generation failed.

**Check:** `agent_runs`, `verification_results`, `run_changes`, workspace status.

**Remedy:** resume eligible interruption, fix the tool/policy issue, or run a fresh remediation.

## Publication preview is stale

**Cause:** changed file hash or verification evidence differs from the reviewed digest.

**Remedy:** reload the current preview and review again. If the run is already conflicted, discard/start fresh as allowed.

## Publication says base branch changed

**Cause:** GitHub branch advanced since scan.

**Remedy:** run a fresh scan and remediation on the new commit. AuditAgent intentionally does not rebase an approved patch automatically.

## Publication permission revoked

**Cause:** user lost push or App lost Contents/Pull requests write permission.

**Remedy:** restore required permission and use retry only if the run is approved/recoverable. Never reuse an old permission check.

## Publication failed after commit or push

**Cause:** transient GitHub/API failure, push rejection, or PR creation failure.

**Remedy:** choose **Retry publishing**. Checkpoints reuse the commit and existing-PR lookup. Do not approve again or manually repeat API calls unless reconciling carefully.

## Pull request merged but UI still says open

**Cause:** webhook missing/invalid/delayed.

**Check:** GitHub delivery logs, HMAC secret, delivery table, application logs.

**Remedy:** restore the thread; reconciliation queries GitHub for an open run. Fix webhook delivery for normal operation.

## Pull request closed without merge and finding returns

This is expected. `FIXED` requires merge; closed-unmerged returns to `DETECTED`.

## Resume says workspace changed

**Cause:** after-hash mismatch, missing changed file, manual workspace modification, or cleanup.

**Behavior:** run becomes `CONFLICTED`; no automatic overwrite.

**Remedy:** inspect audit data and start a fresh remediation if safe.

## Discard is blocked

**Cause:** push, branch, or PR already exists.

**Remedy:** retry publication or manage remote state on GitHub. Discard is intentionally limited to unpublished local work.

## FTS unavailable

**Cause:** DuckDB extension install (for FTS)/load/rebuild failed, often due network or binary compatibility.

**Behavior:** structured metadata retrieval remains active; `ftsAvailable` is false.

**Check:** `memory_fts_state.last_result`.

**Remedy:** enable extension support and rebuild. Do not block scanning/remediation solely for FTS.

## PostgreSQL connection failed or initialization failed

**Cause:** multiple writers/processes, invalid path/permission, corrupted file, version issue.

**Check:** startup logs and filesystem access.

**Remedy:** run one application writer per DB, restore a backup if needed, and validate migrations on a copy. Current startup logs initialization failure rather than necessarily stopping.

## Workspace disk grows

**Cause:** active/recoverable runs, failed cleanup, or orphaned server restart state.

**Remedy:** reconcile directories against `run_publications` before cleanup. Never recursively delete the workspace root without exact validation.

## PDF report does not open in the Audit Report tab

**Cause:** the browser has no inline PDF viewer, the authenticated export request failed, repository access was revoked, or a proxy blocked the `application/pdf` response.

**Check:** inspect `GET /api/reports/export?...&format=pdf`, its HTTP status/content type, and the safe backend error. Confirm the selected repository/branch still has a scan snapshot and the user still has read access.

**Remedy:** use **Download PDF** when only inline viewing is unsupported. For authorization or missing-report failures, restore access or rerun the scan. For generation failures, inspect server logs for `Could not generate the PDF report`, verify PDFBox is packaged, and check application memory pressure.

## Markdown report downloads but is empty or stale

**Cause:** the selected branch has no current persisted report, or an old scan snapshot is being restored.

**Remedy:** confirm the selected repository and branch, then run a forced fresh scan. A failed scan intentionally preserves the last valid report instead of replacing it with empty data.
