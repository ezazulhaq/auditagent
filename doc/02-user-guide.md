# User guide

## Before you begin

You need:

- a configured AuditAgent server;
- access to a GitHub account;
- the AuditAgent GitHub App installed on at least one repository;
- read access to scan and chat;
- GitHub push permission, plus App `Contents: write` and `Pull requests: write`, to publish a remediation.

## 1. Sign in

Open AuditAgent. The sign-in page explains that repository work is performed through GitHub and pull requests.

- If the server is configured, choose **Continue with GitHub**.
- GitHub asks you to authorize the application.
- AuditAgent returns you to the frontend with a secure `HttpOnly` session cookie.
- If the page says GitHub App authentication is not configured, an administrator must complete the server setup.

The browser receives the user's display information and a CSRF token, but never GitHub access, refresh, or installation tokens.

## 2. Choose a repository and branch

The responsive **Scan configuration** panel lists repositories visible through your GitHub App installations. It appears as a collapsible left rail on desktop (which can be expanded or collapsed to maximize workspace area) and a full-width setup section above the workspace on narrow screens.

1. Select a repository.
2. AuditAgent loads up to the first 100 branches from GitHub.
3. The default branch is selected when it is available; otherwise the first returned branch is used.
4. Choose a different branch if required.

If no repository appears:

- use the installation link shown in the panel, if configured; or
- ask an administrator to install or update the GitHub App.

Changing the repository clears the current report and remediation UI. Changing the repository or branch restores the stable conversation and latest report for that exact selection.

## 3. Run a security scan

The only scanner option currently shown is Semgrep.

- Leave **Force fresh rescan** off to reuse a report when the current GitHub branch head is exactly the same commit as the latest snapshot.
- Turn it on to create a fresh managed clone and scan even when the commit is unchanged.
- Choose **Run Scan**.

The panel shows wall-clock elapsed time, progress percentage, and these stages. The timer remains accurate after browser throttling, sleep, or returning from a background tab. During the Semgrep stage, liveness updates arrive periodically without resetting elapsed time.

1. Initialize Scan Engine.
2. Analyze Project Structure.
3. Load Compliance Rules.
4. Run Security Analysis.
5. Generate Audit Report.

The scan can be cancelled by changing repository state or leaving the component, but the backend process may already have reached a durable point. A successful result opens the **Audit Report** tab. A scan that exceeds the configured server deadline stops Semgrep and its child processes, shows the backend failure in the panel, preserves the last valid report, and does not save an empty replacement report.

## 4. Read the results

### Dashboard

The dashboard shows:

- total findings;
- high findings;
- medium findings;
- files scanned;
- scanner name;
- scan duration;
- lines analyzed;
- repository label;
- a severity-distribution chart;
- an action-oriented risk-posture summary.

### Findings

The Findings tab shows every persisted finding. You can:

- review findings in risk-priority order: `HIGH`, `MEDIUM`, `LOW`, then `INFO`;
- search with one or more terms across the ID, rule, type, file path, description, language, severity, and lifecycle status;
- use severity buttons with per-severity counts;
- filter by any lifecycle status present in the current audit, with a count for each status;
- reset all filters or clear a search directly;
- open a finding for details.

Search terms are combined, so every entered term must match some part of the same finding. Filters work together, the visible result count updates immediately, and an empty filtered result provides a **Clear filters** recovery action. Sorting and filtering are local view operations: they do not change the persisted report, finding lifecycle, or remediation actions.

### Audit report

The Audit Report tab loads an authenticated, repository-scoped PDF generated from the latest persisted scan. It includes:

- a management-ready title and confidentiality classification;
- repository, branch, and scanned commit context;
- an executive risk-posture summary;
- high, medium, low, and informational totals;
- scan scope and performance details;
- detailed findings with location, rule, status, description, and a bounded code preview;
- page headers and footers suitable for formal circulation.

Use **Open PDF** to open the current PDF in a separate browser tab. Use **Download PDF** for the same presentation-ready document, or **Download Markdown** for a portable text report containing the full finding details and code. Long code snippets are shortened only in the PDF for legible pagination; the Markdown export retains the full stored snippet. If PDF loading fails, the tab shows the backend error and a retry action without deleting the last valid scan result.

### Documentation

The Documentation tab allows you to read AuditAgent's official guides and references without leaving the application.

- Select **Documentation** from the workspace tabs.
- The side panel updates to list all available documentation files.
- Selecting a document renders its beautifully formatted markdown content in the main viewing area.

## 5. Inspect a finding

Select a finding to open the review drawer. It slides in from the right on larger screens and uses the full viewport on mobile. It shows:

- severity and ID;
- type;
- file and line;
- language;
- description;
- vulnerable code snippet;
- status-dependent actions.

For a `DETECTED` finding, choose **Analyze in isolated workspace**.

## 6. Ask the security agent

Open **Ask AuditAgent** in the lower-right corner. The assistant opens as a floating panel on larger screens and a full-width bottom sheet on mobile.

You can:

- type `scan` to start a scan immediately;
- type `fix VULN-ABC123` or just `VULN-ABC123` to start that exact active finding;
- ask plain-language questions about the current findings;
- ask the agent to scan or fix a described issue.

The model may return `[TRIGGER_SCAN]` or `[TRIGGER_FIX:VULN-ABC123]`, which the frontend converts into the corresponding workflow.

Important: text such as "yes," "approve," or a model-generated tag cannot publish code. Publication happens autonomously via the backend after verification passes.

The chat shows unread message counts when closed and automatically scrolls to new messages. General chat messages are durable and are restored for the selected repository and branch. Press **Escape** to close an open chat panel or finding drawer.

## 7. Let the remediation agent work

When analysis starts:

1. AuditAgent verifies that the finding belongs to the latest selected scan.
2. It creates a durable run and publication record before cloning.
3. It clones the exact scanned commit into an isolated managed workspace.
4. The AI agent gathers context, patches, compiles, rescans, and tests.
5. Progress and tool summaries stream into chat.

No GitHub branch exists yet. If verification fails, the finding becomes `PATCH_FAILED`, the workspace is cleaned, and no PR is created.

## 8. Review a verified fix

When the AI agent succeeds, the finding becomes `AWAITING_APPROVAL` (or transitions automatically to `PR_OPEN` depending on autonomy settings).

If manual approval is enabled, you can review the proposed diff in the finding drawer.

- **Approve**: Click "Approve & create PR" to merge the fix into a new branch and open a PR.
- **Reject**: Click "Reject" to discard the fix. You can optionally provide a reason for the rejection (e.g., "Breaks formatting" or "Uses deprecated API"). This reason is securely stored in DuckDB and used as negative context by the Memory Review agent on subsequent attempts.

## 9. Webhook synchronization

Immediately before writing, AuditAgent rechecks:

- run ownership;
- run/finding binding;
- current publication digest;
- user push permission;
- GitHub App write permissions;
- current base branch SHA;
- workspace file hashes and cleanliness;
- previous publication checkpoints.

It then creates or reuses:

- branch `auditagent/fix-<finding-id>-<run-prefix>`;
- a bot commit with an `AuditAgent-Run` trailer and approving user;
- a non-draft pull request with remediation and verification context.

The drawer links to the open pull request. The finding becomes `PR_OPEN`, not `FIXED`.

## 10. Wait for merge or closure

GitHub sends a signed pull-request webhook when the PR closes.

- Merged: the finding becomes `FIXED`, the run becomes `PR_MERGED`, and approved repository memory is created.
- Closed without merge: the finding returns to `DETECTED`, and the run becomes `PR_CLOSED`.

When restoring a still-open run, AuditAgent also asks GitHub for the current PR state as a reconciliation fallback.

## 11. Recover interrupted work

The chat can show a recovered run.

- `INTERRUPTED` before publication: choose **Resume**. AuditAgent first checks every persisted changed-file hash.
- `PUBLISH_FAILED`: choose **Retry publishing**. The retry uses stored commit/push/PR checkpoints and searches for an existing PR.
- Other unpublished recoverable states: choose **Discard** when offered.
- A changed workspace becomes `CONFLICTED` and is not overwritten automatically.
- Once publication or a remote push exists, discard is blocked; retry or manage the GitHub branch/PR.

## 12. Forget data

The chat header has two separate controls:

- **Forget conversation** removes the selected thread and its run detail after safely discarding unpublished recoverable work. A confirmation dialog is shown.
- **Forget repository remediation memory** removes approved memory for the selected repository and branch and rebuilds the search index. It does not delete the GitHub repository or pull requests.

## 13. Sign out

Choose the sign-out icon in the header. AuditAgent invalidates the server session and expires the browser cookie. This does not revoke GitHub authorization at GitHub; that is managed through GitHub account/app settings.
