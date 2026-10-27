# AuditAgent Documentation Topic & Symbol Map

Use this lookup matrix to quickly route queries and code investigation to the exact documents in `doc/`.

---

## 1. Quick Router by Topic

| Topic / Domain | Primary Document | Secondary Reference |
| :--- | :--- | :--- |
| **High-level concept, value & lifecycle** | [01-product-and-business-guide.md](../../../../doc/01-product-and-business-guide.md) | [03-feature-catalog.md](../../../../doc/03-feature-catalog.md) |
| **End-user UI walkthrough & timelines** | [02-user-guide.md](../../../../doc/02-user-guide.md) | [06-frontend-guide.md](../../../../doc/06-frontend-guide.md) |
| **Feature index & component traceability** | [03-feature-catalog.md](../../../../doc/03-feature-catalog.md) | [15-source-inventory-and-glossary.md](../../../../doc/15-source-inventory-and-glossary.md) |
| **System architecture & trust boundaries** | [04-architecture.md](../../../../doc/04-architecture.md) | [10-security-model.md](../../../../doc/10-security-model.md) |
| **Backend controllers & services** | [05-backend-guide.md](../../../../doc/05-backend-guide.md) | [07-api-reference.md](../../../../doc/07-api-reference.md) |
| **Frontend React components & state** | [06-frontend-guide.md](../../../../doc/06-frontend-guide.md) | [02-user-guide.md](../../../../doc/02-user-guide.md) |
| **REST APIs, SSE streaming & payloads** | [07-api-reference.md](../../../../doc/07-api-reference.md) | [05-backend-guide.md](../../../../doc/05-backend-guide.md) |
| **Database schemas, migrations & FTS** | [08-data-and-persistence.md](../../../../doc/08-data-and-persistence.md) | [05-backend-guide.md](../../../../doc/05-backend-guide.md) |
| **AI remediation & 16-stage workflow** | [09-ai-and-remediation.md](../../../../doc/09-ai-and-remediation.md) | [17-langgraph4j-orchestration.md](../../../../doc/17-langgraph4j-orchestration.md) |
| **Security, OAuth, PKCE & Token Cipher** | [10-security-model.md](../../../../doc/10-security-model.md) | [07-api-reference.md](../../../../doc/07-api-reference.md) |
| **Environment variables & operation setup** | [11-setup-configuration-operations.md](../../../../doc/11-setup-configuration-operations.md) | [04-architecture.md](../../../../doc/04-architecture.md) |
| **Automated testing & quality assurance** | [12-testing-and-quality.md](../../../../doc/12-testing-and-quality.md) | [14-change-guide.md](../../../../doc/14-change-guide.md) |
| **Error diagnosis & troubleshooting** | [13-troubleshooting.md](../../../../doc/13-troubleshooting.md) | [11-setup-configuration-operations.md](../../../../doc/11-setup-configuration-operations.md) |
| **How to make code & schema changes** | [14-change-guide.md](../../../../doc/14-change-guide.md) | [15-source-inventory-and-glossary.md](../../../../doc/15-source-inventory-and-glossary.md) |
| **File paths, vocabulary & statuses** | [15-source-inventory-and-glossary.md](../../../../doc/15-source-inventory-and-glossary.md) | [03-feature-catalog.md](../../../../doc/03-feature-catalog.md) |
| **Runtime review stage skills** | [16-skill-catalog.md](../../../../doc/16-skill-catalog.md) | [09-ai-and-remediation.md](../../../../doc/09-ai-and-remediation.md) |
| **LangGraph4j state machine orchestration** | [17-langgraph4j-orchestration.md](../../../../doc/17-langgraph4j-orchestration.md) | [09-ai-and-remediation.md](../../../../doc/09-ai-and-remediation.md) |

---

## 2. Code Symbols & Class Mappings

| Java Class / Interface | Responsibility | Detailed In |
| :--- | :--- | :--- |
| `AuditController` | Root controller for scan, chat, analyze, runs | `doc/05-backend-guide.md`, `doc/07-api-reference.md` |
| `AuthController` | GitHub OAuth login, callback, session discovery, logout | `doc/05-backend-guide.md`, `doc/07-api-reference.md`, `doc/10-security-model.md` |
| `GitHubWebhookController` | GitHub webhook receiver (`pull_request`, ping) | `doc/05-backend-guide.md`, `doc/07-api-reference.md` |
| `SkillController` | Lists dynamically discovered skills (`GET /api/skills`) | `doc/05-backend-guide.md`, `doc/16-skill-catalog.md` |
| `OrchestratorService` | Scan execution, virtual thread coordination, SSE sink management | `doc/05-backend-guide.md`, `doc/04-architecture.md` |
| `ScannerService` | Semgrep CLI invocation, SARIF parsing, findings mapping | `doc/05-backend-guide.md`, `doc/03-feature-catalog.md` |
| `LlmService` | Spring AI chat model integration, chat agent, system prompts | `doc/05-backend-guide.md`, `doc/09-ai-and-remediation.md` |
| `MultiAgentRemediationGraph` | LangGraph4j 16-stage state machine for autonomous fix | `doc/17-langgraph4j-orchestration.md`, `doc/09-ai-and-remediation.md` |
| `SupervisorNode` | Supervisor router node in the 16-stage LangGraph4j star topology | `doc/17-langgraph4j-orchestration.md`, `doc/09-ai-and-remediation.md` |
| `WorkerAgentGraphFactory` | Factory producing specialized stage workers | `doc/17-langgraph4j-orchestration.md`, `doc/05-backend-guide.md` |
| `SkillManagerService` | Dynamic skill discovery from `.auditagent/skills/stage-*/SKILL.md` | `doc/05-backend-guide.md`, `doc/16-skill-catalog.md` |
| `PullRequestLifecycleService` | Branch creation, PR submission, merge webhook processing | `doc/05-backend-guide.md`, `doc/07-api-reference.md` |
| `DatabaseService` | DuckDB/PostgreSQL persistence, migrations, lexical FTS index | `doc/08-data-and-persistence.md`, `doc/05-backend-guide.md` |
| `TokenCipher` | AES-GCM-256 encryption/decryption for OAuth tokens | `doc/10-security-model.md`, `doc/05-backend-guide.md` |
| `GitHubAuthService` | OAuth token exchange, refresh, user permission verification | `doc/05-backend-guide.md`, `doc/10-security-model.md` |
| `FilePatchService` | Safe file modification with backup & rollback | `doc/05-backend-guide.md`, `doc/10-security-model.md` |

---

## 3. Database Tables & Data Entities

| Table / Entity | Purpose | Documentation |
| :--- | :--- | :--- |
| `reports` | Scan reports by repo path, Markdown/HTML, findings JSON | `doc/08-data-and-persistence.md` |
| `vulnerabilities` | Vulnerability findings, fingerprints, code snippets, status | `doc/08-data-and-persistence.md`, `doc/15-source-inventory-and-glossary.md` |
| `user_sessions` | Authenticated session hashes, user IDs, CSRF tokens, expiry | `doc/08-data-and-persistence.md`, `doc/10-security-model.md` |
| `memory_threads` | User-owned durable conversation threads | `doc/08-data-and-persistence.md` |
| `memory_messages` | Chat history records (persisted before model calls) | `doc/08-data-and-persistence.md` |
| `agent_runs` | Durable remediation run records with status and hashes | `doc/08-data-and-persistence.md`, `doc/15-source-inventory-and-glossary.md` |
| `repository_memories` | Approved fix patterns indexed by DuckDB FTS/BM25 | `doc/08-data-and-persistence.md`, `doc/09-ai-and-remediation.md` |
| `schema_version` | Ordered transactional migration tracking | `doc/08-data-and-persistence.md` |

---

## 4. REST Endpoints & SSE Channels

| HTTP Route | Method | Type | Description | Document |
| :--- | :--- | :--- | :--- | :--- |
| `/api/auth/session` | GET | JSON | Current session info and CSRF token | `doc/07-api-reference.md` |
| `/api/auth/github/login` | GET | Redirect | Initiates OAuth flow with PKCE | `doc/07-api-reference.md` |
| `/api/auth/github/callback` | GET | Redirect | Exchanges OAuth code for tokens | `doc/07-api-reference.md` |
| `/api/auth/logout` | POST | JSON | Revokes local session | `doc/07-api-reference.md` |
| `/api/github/repositories` | GET | JSON | Lists repositories accessible to user | `doc/07-api-reference.md` |
| `/api/github/repositories/{id}/branches` | GET | JSON | Lists branches for a repository | `doc/07-api-reference.md` |
| `/api/scan` | POST | SSE | Scans repository and streams progress/results | `doc/07-api-reference.md` |
| `/api/scan/upload` | POST | SSE | Scans uploaded zip file archive | `doc/07-api-reference.md` |
| `/api/chat` | POST | SSE | Chat agent dialogue and triage | `doc/07-api-reference.md` |
| `/api/analyze` | POST | SSE | Triggers 16-stage LangGraph4j remediation | `doc/07-api-reference.md` |
| `/api/webhooks/github` | POST | JSON | Receives GitHub events (PR merged/closed) | `doc/07-api-reference.md` |
| `/api/skills` | GET | JSON | Lists discovered skills in `.auditagent/skills` | `doc/07-api-reference.md` |

---

## 5. Finding Status Lifecycle

| Status | Meaning | Transitions To | Document |
| :--- | :--- | :--- | :--- |
| `DETECTED` | Finding discovered by Semgrep scanner | `ANALYZING`, `DISMISSED` | `doc/15-source-inventory-and-glossary.md` |
| `ANALYZING` | AI remediation graph is currently executing | `FIX_PROPOSED`, `DETECTED` (on failure) | `doc/15-source-inventory-and-glossary.md` |
| `FIX_PROPOSED` | Patch generated and verified locally | `PR_OPEN`, `DETECTED` (if discarded) | `doc/15-source-inventory-and-glossary.md` |
| `PR_OPEN` | Pull request opened on GitHub | `FIXED` (via webhook), `DETECTED` (closed unmerged) | `doc/15-source-inventory-and-glossary.md` |
| `FIXED` | Pull request merged into repository | Terminal state | `doc/15-source-inventory-and-glossary.md` |
| `DISMISSED` | User or rule explicitly dismissed finding | Re-activated on rescan if changed | `doc/15-source-inventory-and-glossary.md` |

---

## 6. Configuration Keys (`application.yaml`)

| Property Key | Default / Sample | Purpose | Document |
| :--- | :--- | :--- | :--- |
| `auditagent.llm.provider` | `openai-compatible` | LLM provider backend | `doc/11-setup-configuration-operations.md` |
| `auditagent.llm.openai.base-url` | `https://api.openai.com/v1` | LLM API endpoint base URL | `doc/11-setup-configuration-operations.md` |
| `auditagent.llm.openai.api-key` | `${OPENAI_API_KEY}` | API key for LLM | `doc/11-setup-configuration-operations.md` |
| `auditagent.llm.openai.model-name` | `gpt-4o` | Model name identifier | `doc/11-setup-configuration-operations.md` |
| `auditagent.github.client-id` | `${GITHUB_CLIENT_ID}` | GitHub App Client ID | `doc/11-setup-configuration-operations.md` |
| `auditagent.github.client-secret`| `${GITHUB_CLIENT_SECRET}`| GitHub App Client Secret | `doc/11-setup-configuration-operations.md` |
| `auditagent.security.encryption-key` | `${AUDITAGENT_ENCRYPTION_KEY}` | AES-256 hex key for TokenCipher | `doc/10-security-model.md`, `doc/11-setup-configuration-operations.md` |
| `auditagent.database.path` | `jdbc:postgresql://localhost:5432/auditagent` | DB connection string | `doc/08-data-and-persistence.md` |
