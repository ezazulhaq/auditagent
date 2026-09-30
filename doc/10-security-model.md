# Security model

## Security objectives

AuditAgent must:

- authenticate a real user;
- prevent one user from reading or acting on another user's thread/run;
- limit repository access to current GitHub authorization;
- protect server and GitHub credentials;
- keep untrusted repository work inside managed locations;
- prevent a model or chat message from authorizing Git writes;
- bind automated PR publication to exact code and evidence;
- avoid duplicate or stale publication;
- preserve a reviewable audit trail without retaining secrets.

## Authentication controls

- OAuth state has 32 random bytes and is stored only as SHA-256.
- PKCE verifier has 64 random bytes, is AES-GCM encrypted, and uses S256 challenge.
- Browser-state cookie is `HttpOnly`, `SameSite=Lax`, scoped to `/api/auth/github`, and ten minutes.
- State is atomically consumed once and checked for expiry.
- Session token has 48 random bytes; DB stores only its hash.
- OAuth-state and session expiries are generated in UTC and compared against a UTC-normalized PostgreSQL clock, so host timezone does not shorten or extend their security lifetime.
- CSRF token has 32 random bytes and is returned only for the authenticated browser session.
- Comparisons of state and CSRF use constant-time `MessageDigest.isEqual`.
- Session cookie is `HttpOnly`, `SameSite=Lax`, path `/`, and `Secure` for HTTPS or explicit configuration.

## Authorization controls

- Server user ID owns threads and publications.
- Thread ownership is checked before chat, scan, resume, restore, and forget.
- Run ownership is resolved through thread owner plus publication user.
- Finding must belong to the selected scan report.
- Repository reads are revalidated with the user token.
- Publication revalidates user push and App write permissions.
- Run ID and vulnerability ID must match.
- Base branch and file state must remain unchanged.
- Deleting scan history revalidates current user repository read access before bulk removal.

## Secret handling

- OAuth access/refresh tokens use AES-GCM authenticated encryption with a random IV.
- The encryption key is derived by SHA-256 from `AUDITAGENT_TOKEN_ENCRYPTION_KEY`.
- Installation tokens remain in memory.
- Tokens are not placed in clone URLs; JGit credentials provider supplies them.
- App private key can be supplied as PEM text, escaped newlines, or a server-side file path. PKCS#1 is wrapped for PKCS#8 parsing.
- The tracked profile example contains empty secret defaults. Populated `application-local.yaml`, `application-dev.yaml`, and `application-prod.yaml` files are ignored; production should still inject secrets from its secret manager rather than depend on a repository-adjacent file.
- Process environments remove provider credentials, generic token/secret/password/key variables, and common injection variables.
- AOP logging records no argument values.
- Persisted/model-visible prose is redacted and capped.

## CSRF and CORS

- All protected non-`GET`/`HEAD` requests require `X-CSRF-Token`.
- The frontend stores CSRF only in JavaScript module memory.
- CORS allows only configured frontend origin and credentials.
- Allowed methods do not include `PUT`/`PATCH` because the application does not expose them.
- The GitHub webhook is session-exempt but HMAC-authenticated.

## Webhook controls

- `sha256=` HMAC over the exact raw body.
- Constant-time signature comparison.
- Delivery ID primary key prevents replay.
- Failed processing releases the ID so GitHub can retry.
- Only closed pull-request events affect state.
- Repository ID and PR number bind the event to a stored publication.

## Filesystem controls

- Managed workspace keys are sanitized and resolved below a configured root.
- Workspace root itself is never accepted as a workspace.
- Existing workspace keys cannot be silently overwritten.
- Exact clone SHA detects branch races.
- Repository root and target paths must be real non-symlink directories/paths.
- Traversal and symlink components are rejected for model tools and changed files.
- Internal `.auditagent` artifacts are excluded from diffs, commits, and changed-file hashes.
- Backups are kept below the repository's configured backup directory.
- Cleanup validates the target is a child of the managed root before recursive deletion.

## Process controls

`ManagedProcessEnvironment` removes:

- `AWS_*`, `GITHUB_*`, GitLab/Bitbucket variables;
- variables containing token, secret, password, credential, private key, API key;
- key suffixes;
- shell/runtime injection such as `BASH_ENV`, `NODE_OPTIONS`, Java/Maven/Gradle options, askpass, SSH command, preload variables.

It relocates:

- `HOME`/`USERPROFILE`;
- Gradle home;
- XDG config/cache;
- Maven local repository through command arguments.

Build/test tools receive workspace-contained `TEMP`/`TMP`/`TMPDIR`. Semgrep receives a random per-run `aa-*` directory directly beneath the real JVM OS-temp root. This narrow exception avoids a native Windows RPC `socketpair` failure on deeply nested paths. The runner rejects a symlinked/non-directory temp root, validates that the created directory is a direct child with the expected prefix, applies owner-only POSIX permissions where supported, inherits the user's private temp ACL on Windows, and validates the same boundary before recursive cleanup.

Semgrep metrics are disabled.

`ManagedProcessRunner` drains Semgrep stdout and stderr concurrently to prevent pipe backpressure deadlocks. It keeps complete JSON stdout but only the final 64 KiB of stderr; user-visible diagnostics pass through secret redaction and length caps. A monotonic deadline defaults to 15 minutes. Timeout or interruption terminates the root and observed descendant processes, escalates to forced termination after five seconds, closes process streams, and prevents report/snapshot persistence. The runner polls for descendants independently of heartbeat emission and also terminates observed survivors after any root exit before deleting the per-run temp directory or allowing workspace cleanup.

## Model containment

- Tool list is closed and schema-defined.
- Paths are relative to managed workspace.
- Tool dispatcher rejects unknown names/missing arguments.
- Exact replacement prevents a vague patch from applying to multiple locations.
- Backups and hashes support rollback/recovery.
- Tool results, not model claims, set verification.
- Publication is not a model tool.

## Publication and supply-chain controls

- Publication logic requires the current PR digest.
- Sorted maps prevent nondeterministic digest changes.
- Current base SHA is fetched immediately before publication.
- File hashes are checked again.
- Commit includes run and approving user trailers.
- Existing PR must have the exact approved head commit.
- Push statuses other than `OK`/`UP_TO_DATE` fail.
- PR remains subject to repository review and CI.

## Data protection

- Stable outcomes and hashes are retained; verbose detail expires.
- Secret redaction occurs before message/tool persistence.
- API exception detail is redacted.
- Positive memory is approved, merged, and repository-scoped.
- FTS query uses normalized bounded text and prepared statements.
- Report artifact requests require the normal authenticated session and revalidate current repository read access before loading the branch snapshot.
- PDF generation consumes persisted server-side findings and metadata rather than browser-supplied report content. It writes to memory, not a shared temporary report file.
- Export responses use safe normalized filenames, explicit content types, `nosniff`, and `private, no-store`; the frontend revokes temporary PDF Blob URLs during cleanup.

## Deployment responsibilities

The application-level controls are not enough for arbitrary untrusted build scripts. Production should:

- run workers in containers/VMs or equivalent OS isolation;
- allow writes only to the managed workspace and required service data;
- limit network egress according to build policy;
- limit CPU, memory, processes, disk, and execution time;
- use a dedicated service identity;
- store configuration in a secret manager;
- use HTTPS and secure cookies;
- protect and back up PostgreSQL and DuckDB and encryption key together;
- rotate credentials with a planned token re-encryption strategy;
- keep Semgrep, Java, Maven/Gradle, Node, and dependencies patched;
- monitor repeated conflicts, authentication failures, webhook failures, and abnormal process output.

## Security limitations and review targets

- Legacy standalone HTML remains in persisted reports and compatibility JSON payloads. The current UI does not execute it. Any future feature that renders this HTML must first address its public Chart.js dependency and apply a restrictive iframe sandbox/CSP while preserving escaping.
- Report PDFs can contain source snippets and security findings. Authorization protects retrieval, but downloaded files leave the application boundary and must follow organizational data-handling policy.
- Browser PDF embedding depends on the browser's PDF viewer. Users can still download the authenticated artifact when inline viewing is unavailable.
- GitHub list calls use `per_page=100` without pagination, so large installations/branch lists are incomplete.
- App configuration checks presence, not key strength or endpoint allowlisting.
- Database startup logs and continues on initialization failure rather than stopping the service.
- Java process environment scrubbing does not prevent all filesystem, kernel, or network behavior.
- The basic redactor cannot recognize every secret format.
- Some integration errors may become generic 500 responses.

## Security change checklist

Any change to auth, controllers, repository selection, tools, processes, memory, PR publication logic, Git, webhook, or error logging requires:

- threat-model review;
- negative ownership/CSRF/path/permission tests;
- secret-log/persistence review;
- restart/idempotency analysis;
- updates to this document and the feature catalog.

