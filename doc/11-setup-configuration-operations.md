# Setup, configuration, and operations

## Prerequisites

- Java 21.
- Node.js compatible with the checked frontend lockfile (Node 18+ is a practical minimum; Dev Container config provides Node.js 24 and Python 3.12 alongside JDK 21).
- Maven wrapper support; Maven need not be installed separately.
- Semgrep CLI on `PATH`.
- API key with permission to invoke the configured OpenAI-compatible LLM model.
- A GitHub App and OAuth configuration.
- Git available indirectly through JGit; the backend does not shell out to Git.
- Network access to GitHub, the LLM provider API, dependency repositories, and optionally DuckDB extension/CDN services.

Apache PDFBox is resolved through Maven and is packaged with the backend. Enterprise report generation needs no browser rendering service, office suite, headless Chromium, reporting CDN, or additional report configuration. PDFs are produced in application memory, so platform memory limits should allow for the largest expected persisted finding set and concurrent report requests. On first PDF use, PDFBox may inspect installed fonts and stores only reusable font metadata below `<java.io.tmpdir>/auditagent-pdfbox-font-cache`; the service identity must be able to write to its normal JVM temporary directory. No report content is written there.

## Clone and install

```bash
git clone <repository-url>
cd auditagent
cd frontend
npm ci
cd ..
```

Use `npm ci` for reproducible installation from `package-lock.json`.

## Spring configuration profiles

The tracked `src/main/resources/application.yaml` contains only common settings and selects the `local` profile by
default. `src/main/resources/application-example.yaml` is a safe copy template with empty credential defaults; Spring
does not load that example file as the local profile automatically.

For local development, copy the template and populate the copy through environment variables or local-only values:

PowerShell:

```powershell
Copy-Item src/main/resources/application-example.yaml src/main/resources/application-local.yaml
```

Git Bash, Linux, or macOS:

```bash
cp src/main/resources/application-example.yaml src/main/resources/application-local.yaml
```

`application-local.yaml`, `application-dev.yaml`, and `application-prod.yaml` are ignored by Git. Do not force-add
them. The ignored files are convenience overlays, not a substitute for a deployment secret manager.

Set `SPRING_PROFILES_ACTIVE=dev` or `SPRING_PROFILES_ACTIVE=prod` to override the default profile, and provide the
matching ignored/mounted profile file or environment variables. A production artifact must not depend on a developer's
local profile file being present. Keep shared, non-secret defaults in the base file or tracked example; keep actual
AWS and GitHub credentials in environment/secret management.

Enable the versioned documentation pre-commit hook after cloning:

PowerShell:

```powershell
.\scripts\install-git-hooks.ps1
```

Git Bash, Linux, or macOS:

```bash
./scripts/install-git-hooks.sh
```

The hook blocks staged implementation/configuration/rule changes that have no staged documentation update. It does not generate documentation or determine which guides are affected.

## GitHub App setup

Create a GitHub App with:

- callback URL: `<public-backend>/api/auth/github/callback`;
- webhook URL: `<public-backend>/api/webhooks/github`;
- webhook secret;
- expiring user authorization tokens enabled;
- repository permissions:
  - Metadata: read;
  - Contents: read and write;
  - Pull requests: read and write;
- subscribed event: Pull request.

Install the App on the intended repositories. The frontend can show `GITHUB_APP_INSTALLATION_URL` when no repository is available.

The user OAuth flow must accept PKCE parameters used by the backend.

### Choose the production endpoints

Prefer one public HTTPS origin so browser cookies and relative API requests remain same-origin. For an application
served at `https://audit.example.com`, configure:

| Purpose | Value |
|---|---|
| Application/frontend origin | `https://audit.example.com` |
| OAuth callback | `https://audit.example.com/api/auth/github/callback` |
| GitHub webhook | `https://audit.example.com/api/webhooks/github` |
| App installation link | `https://github.com/apps/<app-slug>/installations/new` |

The reverse proxy must route `/api/**` to Spring Boot, preserve the exact webhook request body and the
`X-Hub-Signature-256`, `X-GitHub-Delivery`, and `X-GitHub-Event` headers, and must not put interactive proxy
authentication in front of the webhook. `AUDITAGENT_FRONTEND_URL` is an exact CORS origin; configure only the
scheme and authority, with no route or trailing slash.

### Generate the server-owned secrets

Create independent, high-entropy values for token encryption and webhook authentication. On Linux:

```bash
openssl rand -base64 48  # AUDITAGENT_TOKEN_ENCRYPTION_KEY
openssl rand -hex 32     # GITHUB_APP_WEBHOOK_SECRET
```

PowerShell equivalent:

```powershell
$encryptionBytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($encryptionBytes)
[Convert]::ToBase64String($encryptionBytes)

$webhookBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($webhookBytes)
[Convert]::ToHexString($webhookBytes).ToLowerInvariant()
```

Generate these directly in the deployment secret manager when possible. Never reuse one value for both purposes.
`TokenCipher` derives the AES key from `AUDITAGENT_TOKEN_ENCRYPTION_KEY`; losing or changing it makes existing OAuth
token ciphertext unreadable. Back up that key separately but consistently with the PostgreSQL database.

### Register the GitHub App

In the owning user or organization, open **Settings → Developer settings → GitHub Apps → New GitHub App** and:

1. Set the homepage to the public frontend origin.
2. Set the callback URL to the exact `/api/auth/github/callback` URL above.
3. Leave expiring user authorization tokens enabled so OAuth supplies refresh tokens.
4. Enable webhooks, set the exact `/api/webhooks/github` URL, paste `GITHUB_APP_WEBHOOK_SECRET`, and keep SSL
   verification enabled.
5. Grant only Metadata read, Contents read/write, and Pull requests read/write.
6. Subscribe to the Pull request event.
7. Select the appropriate visibility: account-only for a private internal deployment, or a broader option only when
   other accounts must install it.

Do not grant Actions, Administration, Secrets, or other permissions unless a separately reviewed feature requires
them. If permissions are changed after installation, each installation owner must approve the new permissions.

### Collect the App credentials and private key

After registration:

1. Record the App ID, Client ID, and App slug. App ID and Client ID are distinct.
2. Generate a client secret and store it immediately as `GITHUB_APP_CLIENT_SECRET`.
3. Generate a GitHub App RSA private key and transfer the downloaded PEM over a secure channel.
4. Prefer mounting the PEM as a read-only file and setting `GITHUB_APP_PRIVATE_KEY` to its path. The implementation
   also accepts literal/escaped PEM and handles both PKCS#1 and PKCS#8, but a file avoids multiline environment
   handling and accidental disclosure.

Linux file example:

```bash
sudo install -d -o auditagent -g auditagent -m 0700 /etc/auditagent/secrets
sudo install -o auditagent -g auditagent -m 0400 \
  auditagent.private-key.pem \
  /etc/auditagent/secrets/github-app.private-key.pem
```

### Install the App

Open **Install App**, select the intended user or organization, and choose **Only select repositories** unless all
repositories are deliberately in scope. Organization policy may require an owner to approve the installation.
Installation and user OAuth authorization are separate: login can succeed while the repository list is empty if the
App is not installed on that account/repository or an organization/SAML authorization is still pending.

## Required GitHub/server environment

| Variable | Required | Purpose |
|---|---:|---|
| `GITHUB_APP_ID` | yes | Issuer for App JWT. |
| `GITHUB_APP_CLIENT_ID` | yes | OAuth client. |
| `GITHUB_APP_CLIENT_SECRET` | yes | OAuth exchange/refresh. |
| `GITHUB_APP_PRIVATE_KEY` | yes | PEM content, escaped PEM, or secure server file path. |
| `GITHUB_APP_WEBHOOK_SECRET` | yes | Webhook HMAC. |
| `AUDITAGENT_TOKEN_ENCRYPTION_KEY` | yes | High-entropy token encryption material. |
| `GITHUB_APP_CALLBACK_URL` | production | OAuth callback. Defaults to local backend. |
| `AUDITAGENT_FRONTEND_URL` | production | Redirect and exact CORS origin. |
| `GITHUB_APP_INSTALLATION_URL` | recommended | User installation/configuration link. |
| `AUDITAGENT_WORKSPACE_ROOT` | recommended | Dedicated managed-clone root. |
| `AUDITAGENT_SECURE_COOKIES` | recommended | Force secure cookies; HTTPS URLs also do so automatically. |
| `AUDITAGENT_SESSION_HOURS` | optional | Session lifetime; default 24. |
| `AUDITAGENT_SCANNER_TIMEOUT_SECONDS` | optional | Semgrep wall-clock deadline; default 900 seconds. |
| `AUDITAGENT_SCANNER_HEARTBEAT_SECONDS` | optional | Scan liveness SSE interval; default 15 seconds. |
| `GITHUB_API_VERSION` | optional | API header; default `2026-03-10`. |
| `GITHUB_API_URL` | optional | GitHub API base, useful for tests/enterprise compatibility. |
| `GITHUB_AUTHORIZE_URL` | optional | OAuth authorization endpoint. |
| `GITHUB_TOKEN_URL` | optional | OAuth token endpoint. |

Do not expose these to Vite or the browser.

### Complete production environment example

```dotenv
GITHUB_APP_ID=123456
GITHUB_APP_CLIENT_ID=Iv1.example
GITHUB_APP_CLIENT_SECRET=<injected-client-secret>
GITHUB_APP_PRIVATE_KEY=/etc/auditagent/secrets/github-app.private-key.pem
GITHUB_APP_WEBHOOK_SECRET=<injected-webhook-secret>
AUDITAGENT_TOKEN_ENCRYPTION_KEY=<injected-encryption-key>

GITHUB_APP_CALLBACK_URL=https://audit.example.com/api/auth/github/callback
AUDITAGENT_FRONTEND_URL=https://audit.example.com
GITHUB_APP_INSTALLATION_URL=https://github.com/apps/your-auditagent-app/installations/new

AUDITAGENT_WORKSPACE_ROOT=/var/lib/auditagent/workspaces
# PostgreSQL configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-host:5432/auditagent
SPRING_DATASOURCE_USERNAME=auditagent
SPRING_DATASOURCE_PASSWORD=strongpassword

# DuckDB FTS configuration
AUDITAGENT_DB_PATH=/var/lib/auditagent/data/audit_reports.duckdb
AUDITAGENT_SECURE_COOKIES=true
AUDITAGENT_SESSION_HOURS=24
GITHUB_API_VERSION=2026-03-10
```

The three endpoint overrides below normally remain at their GitHub.com defaults and are mainly useful for tests or a
deliberately supported GitHub Enterprise deployment:

```dotenv
GITHUB_API_URL=https://api.github.com
GITHUB_AUTHORIZE_URL=https://github.com/login/oauth/authorize
GITHUB_TOKEN_URL=https://github.com/login/oauth/access_token
```

Do not prefix server values with `VITE_`, bake them into a container image, commit them in a manifest, or print them
in deployment logs.

### Example systemd secret injection

Create a protected environment file and edit it without placing secret values in shell history:

```bash
sudo install -d -o root -g root -m 0700 /etc/auditagent
sudo install -o root -g root -m 0600 /dev/null /etc/auditagent/auditagent.env
sudoedit /etc/auditagent/auditagent.env
```

Reference it from the service:

```ini
[Service]
User=auditagent
Group=auditagent
EnvironmentFile=/etc/auditagent/auditagent.env
WorkingDirectory=/opt/auditagent
ExecStart=/usr/bin/java -jar /opt/auditagent/auditagent-1.0.0.jar
Restart=on-failure
RestartSec=5
PrivateTmp=true
NoNewPrivileges=true
ReadWritePaths=/var/lib/auditagent/workspaces /var/lib/auditagent/data
```

Then run `systemctl daemon-reload`, restart the service, and inspect only secret-safe status/log output. A deployment
secret manager is preferable to a static environment file.

### Container and Kubernetes secret injection

- Inject App ID, client ID/secret, webhook secret, and encryption key from the orchestrator secret store.
- Mount the private key as a read-only file and set `GITHUB_APP_PRIVATE_KEY` to the mounted path.
- Mount durable storage for PostgreSQL, DuckDB, and separate disposable/capacity-limited storage for workspaces.
- For Kubernetes, use `secretKeyRef`/`envFrom` or an external-secret operator; do not commit a plaintext or merely
  base64-encoded Secret manifest.
- Remember that privileged container administrators can inspect process environments; enforce platform access
  controls as well as application controls.

## AWS/model environment

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_AI_OPENAI_API_KEY` | empty | OpenAI-compatible API key for both Spring AI and LangChain4j. |
| `SPRING_AI_OPENAI_BASE_URL` | empty | Base URL for the OpenAI-compatible API. |
| `PROVIDER_MODEL` | empty | Model name (e.g. `anthropic/claude-3.5-sonnet`) for both chat/remediation. |

The LangChain4j client uses the same Spring Boot properties. Provide valid API keys through secrets management rather than plain text.

## Database and workspace environment

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/auditagent` | PostgreSQL connection URL. |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | PostgreSQL username. |
| `SPRING_DATASOURCE_PASSWORD` | | PostgreSQL password. |
| `AUDITAGENT_DB_PATH` | `audit_reports.duckdb` | DuckDB FTS file. |
| `AUDITAGENT_WORKSPACE_ROOT` | OS temp `/auditagent-workspaces` | Managed isolated clones. |

Place the DB on durable storage. Place workspaces on disposable, capacity-limited storage. Do not point the workspace root at the repository, user home, filesystem root, or shared source tree.

### Provision a Linux workspace

Use a dedicated service identity and narrowly writable directories:

```bash
sudo useradd --system --home-dir /var/lib/auditagent --shell /usr/sbin/nologin auditagent
sudo install -d -o auditagent -g auditagent -m 0700 /var/lib/auditagent/workspaces
sudo install -d -o auditagent -g auditagent -m 0700 /var/lib/auditagent/data
```

Skip `useradd` if the deployment already provisions the identity. Apply disk quotas and monitor orphaned workspaces.
Run repository builds in a container/VM or equivalent OS sandbox with CPU, memory, process, disk, timeout, and egress
limits. A `noexec` mount can break repository wrappers such as `mvnw` and `gradlew`; choose the mount policy according
to the supported build contract. Java path validation and process-environment scrubbing are defense-in-depth, not a
complete boundary against malicious build scripts.

## Application settings

### Scanner

| Property | Default | Meaning |
|---|---:|---|
| `auditagent.scanner.timeout-seconds` | 900 | Maximum wall-clock duration for one Semgrep process. |
| `auditagent.scanner.heartbeat-seconds` | 15 | Interval between Semgrep liveness progress events. |
| `auditagent.scanner.rule-timeout-seconds` | 2 | Maximum seconds Semgrep will spend evaluating a single rule on a single file. |

Semgrep also requires a short, writable JVM OS-temp root (`java.io.tmpdir`), especially on Windows. AuditAgent creates one random `aa-*` child per scan and removes it after Semgrep and its observed RPC descendants stop. Do not redirect the JVM OS-temp root into the deeply nested managed scan workspace; Windows Semgrep can fail native RPC `socketpair` creation there. No repository path or credential is used in the temp name.

### Agent

| Property | Default | Meaning |
|---|---:|---|
| `auditagent.agent.max-iterations` | 15 | Model/tool rounds. |
| `auditagent.llm.provider` | openai-compatible | Configures the LLM provider (e.g. OpenRouter/Gemini or direct OpenAI). |
| `auditagent.llm.openai.base-url` | empty | API Base URL for OpenAI-compatible endpoints. |
| `auditagent.llm.openai.api-key` | empty | API key for the OpenAI-compatible endpoint. |
| `auditagent.llm.openai.model-name` | empty | Model name (e.g. `anthropic/claude-3.5-sonnet`). |
| `auditagent.llm.openai.supervisor-model-name` | `typesafe/jev-router` | System 1 model used exclusively for Supervisor routing (e.g. TypeSafe JEV). |
| `compile-timeout-seconds` | 120 | Build and target-rescan process timeout. |
| `test-timeout-seconds` | 180 | Test timeout. |
| `backup-dir` | `.auditagent/backups` | Workspace-relative backups. |
| `max-file-read-lines` | 200 | Read tool cap. |
| `max-retries` | 3 | Reserved/configured retry allowance; current loop is iteration-driven. |
| `llm-call-timeout-seconds` | 120 | One model call. |
| `max-tool-result-chars` | 4000 | Tool result stored in model history. |
| `max-history-messages` | 40 | Configuration exists; durable token-budget selection is the current effective window. |
| `max-consecutive-empty-responses` | 3 | Abort threshold. |
| `llm-temperature` | 0.2 | LangChain model temperature. |
| `llm-max-output-tokens` | 4096 | LangChain output cap. |
| `sandbox-enabled` | false | Execute agent shell commands inside a Docker container. |
| `sandbox-image` | auditagent-sandbox:latest | Docker image for sandbox execution. |
| `sandbox-memory-limit` | 2g | Memory limit for sandbox containers. |
| `sandbox-cpu-limit` | 2.0 | CPU limit for sandbox containers. |

### Memory

| Property | Default | Meaning |
|---|---:|---|
| `auditagent.memory.enabled` | true | Approved-memory retrieval. Durable persistence still underlies workflows. |
| `fts-enabled` | true | DuckDB lexical FTS. |
| `retention-days` | 30 | Verbose detail retention. |
| `context-token-budget` | 12000 | Approximate prompt history. |
| `candidate-limit` | 50 | Retrieval candidates. |
| `top-k` | 8 | Memories injected. |
| `max-chat-chars` | 8000 | Persisted chat cap. |
| `max-tool-chars` | 4000 | Persisted tool cap. |
| `summarization-enabled` | true | Use LLM-based summarization for dropped context turns instead of a static notice. |
| `max-tool-output-chars` | 2000 | Maximum characters per individual tool output before aggressive truncation. |

## Run locally

### Backend and frontend hot reload

```bash
./run-dev.sh
```

- Backend: `http://localhost:8173`
- Frontend: `http://localhost:5173`
- Logs: `backend.log`, `frontend.log`

The script is Bash-oriented. On Windows, run the backend and frontend in separate PowerShell terminals.

### Backend only

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Unix:

```bash
./mvnw spring-boot:run
```

### Frontend only

```bash
cd frontend
npm run dev
```

### Production-like local run

`run.sh` builds the frontend into Spring static resources, then starts Spring Boot.

```bash
./run.sh
```

For a deployable jar:

```bash
cd frontend
npm ci
npm run build
cd ..
./mvnw clean package
java -jar target/auditagent-1.0.0.jar
```

## Validate the GitHub integration

### Configuration readiness

After startup:

```bash
curl -sS https://audit.example.com/api/auth/session
```

Before login, a configured deployment returns the equivalent of:

```json
{
  "configured": true,
  "installationUrl": "https://github.com/apps/your-auditagent-app/installations/new",
  "authenticated": false
}
```

If `configured` is false, check the App ID, client ID/secret, private key, webhook secret, and token-encryption key.

### OAuth and repository validation

1. Open `/api/auth/github/login` in a browser.
2. Confirm GitHub displays the expected App and returns to the configured callback.
3. Confirm the callback redirects to the frontend and `/api/auth/session` now reports `authenticated: true` plus a
   CSRF token.
4. Confirm the repository picker contains only installed/user-accessible repositories and branch selection works.
5. If an organization uses SAML SSO, establish the required GitHub organization session before reauthorizing.

### Publication and webhook validation

Use a disposable repository: scan a branch, remediate one finding, and observe the autonomous PR generation. Verify exactly one bot branch, one commit, and one ready-for-review PR. The finding must remain
`PR_OPEN` until merge. Merge the PR and verify the signed pull-request webhook changes it to `FIXED`.

In the GitHub App's **Advanced -> Recent deliveries** page, verify the delivery URL, `pull_request` event, and 2xx
response. Redeliver the event to confirm idempotency. A request without a valid `X-Hub-Signature-256` must be rejected.

### GitHub setup troubleshooting

| Symptom | Likely cause or action |
|---|---|
| `/api/auth/session` returns `configured: false` | One of the six required App/crypto settings is blank. |
| `incorrect_client_credentials` | Client ID/secret do not belong to the same App. |
| Login state mismatch | Callback host changed, browser rejected the state cookie, another browser initiated login, or the ten-minute state was reused/expired. A non-UTC server is supported; immediate failures should be checked against the UTC-normalized auth-expiry query and an accurate system clock. |
| `Could not authenticate GitHub App` | App ID/private key mismatch, unreadable key file, malformed PEM, or server clock skew. |
| Login works but repositories are empty | App is not installed on the repository, org access is pending, or user access was revoked. |
| Publication returns 403 | App Contents/PR permissions are missing/unapproved, or the user lacks push permission. |
| Webhook signature fails | GitHub and server secrets differ, or a proxy modified the raw request body. |
| Webhook never arrives | URL/TLS/network is invalid, webhook is disabled, or Pull request is not subscribed. |
| Workspace preparation fails | Ownership, disk capacity, unsafe root, or a stale workspace directory is blocking preparation. |
| Run becomes `CONFLICTED` | The remote base branch advanced; perform a fresh scan/remediation. |

## Operational lifecycle

### Startup checks

- GitHub App configured.
- DB path writable and backup policy valid.
- workspace root writable and outside sensitive paths.
- LLM model accessible.
- Semgrep executable available.
- JVM OS-temp root is short, writable by only the service identity, is a real directory rather than a symlink, and has enough space for Semgrep's per-run files.
- FTS availability expected; metadata fallback is acceptable if intentionally offline.
- frontend origin/callback match public HTTPS URLs.

### Runtime monitoring

Monitor:

- application startup/migration logs;
- authentication and permission failures;
- scan start, heartbeat, duration, timeout, and Semgrep non-0/1 exits;
- model timeouts and empty responses;
- build/test timeouts;
- `CONFLICTED` and `PUBLISH_FAILED` runs;
- webhook signature/replay/processing failures;
- workspace cleanup and disk use;
- stale `aa-*` entries beneath the service identity's OS-temp root, which indicate abnormal scanner/temp cleanup;
- DB file growth and retention cleanup;
- FTS `last_result`.

When recovering an instance running the older sequential-stream scanner, stop the backend before terminating its Semgrep child tree. Killing only Semgrep can let the old backend save a false empty success. After the backend has stopped, terminate the remaining explicit Semgrep descendants, clean only the affected managed `scan-<UUID>` workspace through the workspace-root safety checks, deploy/restart, and run a fresh scan.



**Custom Metrics:**
- `ai.tokens.consumed` (Counter): Tracks total model tokens consumed, tagged by `model` dynamically resolved from configuration (e.g., `claude-3.5-sonnet` instead of static framework identifiers).
- `findings.resolved` (Counter): Tracks successful resolution events based on merged PRs, tagged by vulnerability `type`.

Actuator is present, but database health is disabled in configuration. Add deployment-specific readiness checks rather than assuming process liveness means functional readiness.

### Backup and recovery

Back up:

- PostgreSQL database backup;
- DuckDB file;
- `AUDITAGENT_TOKEN_ENCRYPTION_KEY` in a separate secret manager;
- GitHub App private key and configuration.

The PostgreSQL database and encryption key must be recoverable together to refresh user authorizations. Managed workspaces are temporary; interrupted unpublished runs may require the matching workspace, so avoid deleting the root while runs are recoverable.

### Workspace cleanup

Normal scan workspaces are removed after completion. Remediation workspaces remain only while review/retry/recovery needs them. Periodically identify orphan directories only after reconciling against active `run_publications`. Do not bulk-delete the configured root without that check.

Each scan also creates a short `aa-*` directory directly under `java.io.tmpdir`. Normal completion, scan failure, timeout, and interruption all attempt to terminate observed Semgrep descendants before deleting that exact validated directory. If a crash leaves one behind, first stop or identify the owning backend/descendant process; then remove only the explicit stale `aa-*` child after confirming it belongs to an inactive scan. Never bulk-delete the OS-temp root.

### Token and key rotation

- GitHub App private key: create an additional key, deploy it, validate installation-token generation, then revoke the
  old key. Never revoke the only working key before deployment verification.
- Client secret: generate a replacement, deploy it, validate a new OAuth login and refresh, then revoke the old one.
- Webhook secret: coordinate the GitHub and server update in a maintenance window. The current implementation accepts
  only one webhook secret, so there is no dual-secret overlap.
- Token encryption key: current code has no multi-key re-encryption workflow. Rotating it without migrating ciphertext
  invalidates stored OAuth authorizations; implement versioned re-encryption or deliberately require all users to
  reauthenticate. Preserve unrelated audit/history records.
- Installation tokens: no manual rotation is needed; they are requested just in time, remain memory-only, and GitHub
  expires them after one hour.
- User access tokens: keep expiration enabled; the backend refreshes them while the stored refresh token remains valid.

## Dependency and build notes

- LangGraph4j (`1.9.0-beta1`) provides the graph-based orchestration and requires Java 17+.
- `langchain4j-open-ai` is included to support OpenRouter, Gemini, and other OpenAI-compatible API proxies.
- `async-generator-5.0.0` provides async streaming compatibility for the graph engine.
- Spring Boot 4 and Spring AI release candidate require deliberate Jackson/AWS alignment.
- JUnit BOM is pinned to JUnit 6 for Spring Framework 7 compatibility.
- Frontend build empties the static output directory.
- Generated static assets are intentionally tracked/allowed by `.gitignore`.
- DuckDB FTS may attempt extension installation, requiring network/filesystem support the first time.

## Production hardening

- HTTPS only.
- dedicated runtime user;
- container/VM sandbox for repository processes;
- read-only application image;
- narrowly writable DB and workspace mounts;
- egress policy;
- resource quotas;
- centralized secret-safe logs;
- reverse-proxy SSE timeouts;
- DB backup and restore drills;
- GitHub webhook delivery monitoring;
- dependency and Semgrep update process.
