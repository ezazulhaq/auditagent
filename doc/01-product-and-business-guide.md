# Product and business guide

## AuditAgent in one sentence

AuditAgent finds security problems in an installed GitHub repository, uses an AI agent to prepare and verify a focused fix in an isolated copy, and publishes that fix as a pull request only after a user reviews and explicitly approves it.

## The problem it solves

Static-analysis tools can find many issues, but a finding alone does not explain its business impact, supply a tested fix, preserve an audit trail, or safely move the change into normal engineering review. AuditAgent connects those steps:

1. Identify a potential vulnerability.
2. Explain and triage it in conversation.
3. Investigate relevant source code.
4. Prepare a minimal change.
5. Compile, rescan, and test the change.
6. Show the exact diff and evidence to a human.
7. Publish a traceable pull request.
8. Wait for the repository's normal review and merge process.
9. Reuse only successful, merged remediation knowledge.

## People and systems involved

| Actor                         | Responsibility                                                                                                                                    |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| Developer or security analyst | Selects a repository, runs scans, reviews findings, requests remediation, reviews the diff, and approves or rejects publication.                  |
| Repository owner              | Installs the GitHub App and grants the required repository permissions.                                                                           |
| AuditAgent backend            | Enforces identity, repository access, isolation, durable state, verification, automated PR creation, and webhooks.                                |
| AuditAgent frontend           | Presents scans, findings, evidence, chat, recovery controls, and dynamic PR state tracking.                                                       |
| Semgrep                       | Performs static source-code analysis using the bundled rule packs.                                                                                |
| OpenAI-compatible model       | Powers conversational explanations and the tool-using remediation agent.                                                                          |
| GitHub                        | Supplies identity, repository access, branch state, installation tokens, pull requests, and merge/close events.                                   |
| PostgreSQL                    | Stores scan reports, findings, users, encrypted authorization, sessions, conversations, runs, evidence, and approved memories.                    |

## Business value

- Reduces time from a security finding to a reviewable fix.
- Keeps engineers in control of repository writes.
- Produces evidence for how a fix was prepared and verified.
- Prevents a generated patch from being treated as resolved before merge.
- Recovers useful state after a server restart.
- Reuses repository-specific patterns only after real approval and merge.
- Keeps remediation within the organization's normal pull-request and CI process.

## Primary use cases

### Audit a repository branch

The user signs in, selects an installed repository and branch, and runs a scan. AuditAgent clones the exact current commit, detects languages, chooses bundled rule packs, runs Semgrep, and presents a dashboard, searchable findings, and an enterprise PDF assessment. The same repository-scoped result can be downloaded as PDF for formal distribution or Markdown for engineering workflows and version-friendly review.

### Understand a finding

The user opens a finding to see severity, type, path, line, description, and vulnerable code. The user can also ask the chat agent questions. The backend supplies active findings from its persisted report rather than trusting finding data from the browser.

### Generate and verify a remediation

The user starts analysis for one detected finding. AuditAgent creates a durable run and isolated clone, then lets the remediation model read files, search code, patch, compile, rescan, test, and roll back. A run is reviewable only when a patch exists and required verification succeeds or records an explicit supported skip.

### Autonomous pull request creation

AuditAgent does not stop and wait for human approval after a patch is successfully verified. It fully automates the pull request generation. The user reviews the repository, base branch, pinned commit, planned branch, changed files, verification evidence, and unified diff within the generated GitHub PR. The backend securely manages the branch creation, commit, push, and PR. The backend webhook controller then syncs the live GitHub merge state directly to the AuditAgent frontend in real-time.

### Recover after interruption

Runs are durable. On startup, active or publishing work becomes interrupted. Unapproved remediation may be resumed only if persisted file hashes still match. Approved publication failures use a dedicated retry path that reuses stored checkpoints and searches for an existing pull request.

### Learn from successful work

A finding becomes `FIXED` only after GitHub confirms the pull request was merged. At that point, AuditAgent stores approved repository-scoped remediation memory. Rejected, failed, merely approved, open, or closed-unmerged work does not become positive memory.

### Forget stored context

The user can forget a conversation and unpublished run detail, or separately forget approved repository/branch memory. These operations are owner-checked and trigger the appropriate cleanup or full-text index refresh.

## End-to-end business lifecycle

```text
Sign in
  -> select installed repository and branch
  -> scan pinned commit
  -> inspect/report/chat
  -> start one finding's remediation
  -> prepare isolated change
  -> build + target rescan + tests
  -> review bound evidence and diff
  -> reject OR approve publication
  -> create/reuse bot commit and pull request
  -> GitHub review and CI
  -> merge: FIXED + reusable memory
     close without merge: DETECTED again
```

## Important product rules

- The browser never supplies a local filesystem repository path.
- The authenticated server session, not a browser ID or username string, is the user identity.
- One repository/branch/finding fingerprint may have only one active or recoverable managed run.
- A cached scan is used only when its base commit still equals the current branch head.
- A remediation cannot start without a scan snapshot for that repository and branch.
- A finding already in a remediation lifecycle cannot silently start another lifecycle.
- Chat commands may start a scan or open remediation, but cannot approve repository writes.
- Approval must match the current run, vulnerability, diff, hashes, and verification evidence.
- A pull request is created ready for review, not silently merged.
- `PR_OPEN` is not `FIXED`.
- Positive memory is repository/branch scoped and approved-only.

## What AuditAgent does not do

- It does not accept arbitrary shared-API local paths.
- It does not use embeddings or vector search.
- It does not guarantee that static analysis finds every vulnerability.
- It does not replace code review, repository CI, security testing, or deployment controls.
- It does not merge pull requests.
- It does not provide an operating-system sandbox by itself. Production workers still require container or OS isolation for untrusted build scripts.
- It does not currently scan C#, Go, Ruby, PHP, or Rust through dedicated bundled language directories, although result parsing can label those file extensions.
- Its agent build/test automation currently recognizes Maven and Gradle projects.

## Success measures

Useful operating measures include:

- scans completed and scan duration;
- findings by severity, rule, repository, and branch;
- remediation completion and verification rates;
- approval, rejection, and publication-failure rates;
- time from detection to pull request and merge;
- reopened or closed-unmerged pull requests;
- memory retrieval availability and reuse count;
- stale-base, stale-approval, and workspace-conflict frequency.

The current UI exposes scan counts, duration, lines, files, and finding states. Broader organizational metrics would require an additional reporting feature.
