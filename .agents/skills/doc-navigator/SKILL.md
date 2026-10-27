---
name: doc-navigator
description: Navigate and consult the AuditAgent project documentation suite in doc/ (@doc) for context on architecture, backend/frontend components, APIs, database schemas, AI remediation workflows, security boundaries, troubleshooting, or change guides.
---

# Documentation Navigator (`@doc`)

Use this skill whenever you need context, architectural facts, API contracts, schema models, or operational runbooks from the AuditAgent documentation suite in `doc/`.

The documentation suite in `doc/` is treated as **code and ground truth**. It is maintained alongside implementation changes and reflects the active, verified state of the system.

---

## 1. Quick Navigation Router

AuditAgent documents are numbered and organized by topic. Match your question or task against the table below to select the primary guide:

| Category | Topics & Questions | Target Document |
| :--- | :--- | :--- |
| **Product & Concepts** | Business rationale, user personas, high-level lifecycle | [`doc/01-product-and-business-guide.md`](file:///home/coder/workspace/auditagent/doc/01-product-and-business-guide.md) |
| **User Experience** | UI screens, scan/triage workflow, PR timeline, status updates | [`doc/02-user-guide.md`](file:///home/coder/workspace/auditagent/doc/02-user-guide.md) |
| **Feature Catalog** | Comprehensive inventory of product & technical features | [`doc/03-feature-catalog.md`](file:///home/coder/workspace/auditagent/doc/03-feature-catalog.md) |
| **System Architecture** | Full-stack architecture, virtual threads, trust boundaries | [`doc/04-architecture.md`](file:///home/coder/workspace/auditagent/doc/04-architecture.md) |
| **Backend Implementation** | Spring Boot controllers, services, JGit clones, domain objects | [`doc/05-backend-guide.md`](file:///home/coder/workspace/auditagent/doc/05-backend-guide.md) |
| **Frontend Implementation** | React 19, Vite, Tailwind CSS 4, SSE client, state controllers | [`doc/06-frontend-guide.md`](file:///home/coder/workspace/auditagent/doc/06-frontend-guide.md) |
| **API & Protocols** | REST endpoints, SSE streams, CSRF headers, payload schemas | [`doc/07-api-reference.md`](file:///home/coder/workspace/auditagent/doc/07-api-reference.md) |
| **Data & Storage** | DuckDB/PostgreSQL tables, migrations, lexical FTS retrieval | [`doc/08-data-and-persistence.md`](file:///home/coder/workspace/auditagent/doc/08-data-and-persistence.md) |
| **AI Remediation** | Multi-agent remediation, 16 review stages, verification gates | [`doc/09-ai-and-remediation.md`](file:///home/coder/workspace/auditagent/doc/09-ai-and-remediation.md) |
| **Security & Auth** | GitHub App OAuth, PKCE, TokenCipher (AES-GCM), sandbox safety | [`doc/10-security-model.md`](file:///home/coder/workspace/auditagent/doc/10-security-model.md) |
| **Operations & Config** | Environment variables, GitHub App setup, running, Docker | [`doc/11-setup-configuration-operations.md`](file:///home/coder/workspace/auditagent/doc/11-setup-configuration-operations.md) |
| **Testing & Quality** | Backend JUnit 5 tests, frontend Vitest, test commands | [`doc/12-testing-and-quality.md`](file:///home/coder/workspace/auditagent/doc/12-testing-and-quality.md) |
| **Troubleshooting** | Diagnosing errors, scan failures, OAuth issues, DB locks | [`doc/13-troubleshooting.md`](file:///home/coder/workspace/auditagent/doc/13-troubleshooting.md) |
| **Change Recipes** | Step-by-step recipes for adding APIs, scanner rules, tools | [`doc/14-change-guide.md`](file:///home/coder/workspace/auditagent/doc/14-change-guide.md) |
| **Glossary & Inventory** | Production files, rule counts, finding status lifecycle | [`doc/15-source-inventory-and-glossary.md`](file:///home/coder/workspace/auditagent/doc/15-source-inventory-and-glossary.md) |
| **Skill Catalog** | Runtime review stage skills under `.auditagent/skills/` | [`doc/16-skill-catalog.md`](file:///home/coder/workspace/auditagent/doc/16-skill-catalog.md) |
| **LangGraph4j Orchestration**| LangGraph4j state machine, SupervisorNode, worker factory | [`doc/17-langgraph4j-orchestration.md`](file:///home/coder/workspace/auditagent/doc/17-langgraph4j-orchestration.md) |
| **Doc Index & Conventions** | Documentation hub, personas, conventions, sync rules | [`doc/README.md`](file:///home/coder/workspace/auditagent/doc/README.md) |

For an in-depth lookup index connecting specific classes, endpoints, tables, and config keys to doc lines, consult [Topic & Symbol Map](./references/topic-map.md).

---

## 2. Navigating & Searching Documentation

### Using the Bundled Search Helper

A fast documentation search utility is bundled with this skill at [`scripts/doc-search.sh`](./scripts/doc-search.sh):

```bash
# Search for occurrences across all docs
.agents/skills/doc-navigator/scripts/doc-search.sh "TokenCipher"

# Search only section headings (#, ##, ###)
.agents/skills/doc-navigator/scripts/doc-search.sh -H "remediation"

# Filter to a specific document (e.g., doc/07-api-reference.md)
.agents/skills/doc-navigator/scripts/doc-search.sh -f api "SSE"

# Count matches across documents to find the primary source
.agents/skills/doc-navigator/scripts/doc-search.sh -c "DuckDB"
```

### Direct Inspection Workflow

1. **Locate Target Section**:
   Use `grep` or `doc-search.sh -H` to pinpoint the exact heading:
   ```bash
   grep -n "^## " doc/08-data-and-persistence.md
   ```
2. **Read Surrounding Range**:
   Read only the relevant range of lines to conserve context window while extracting full detail:
   ```bash
   # Read lines 40-90 of doc/08-data-and-persistence.md
   ```
3. **Verify Against Invariants**:
   Before answering or modifying code, check [Architectural Invariants](./references/invariants-guide.md) to ensure you are not relying on obsolete local-path or chat-approval concepts.

---

## 3. Core Invariants to Keep in Mind

When interpreting documentation, strictly uphold these ground truths:

1. **Authentication Identity**: The authenticated server session is the only security identity. Browser `clientId`, `threadId`, model output, paths, and request usernames are never trusted.
2. **Repository Source**: Repositories come solely through installed GitHub Apps; shared APIs reject client-supplied filesystem paths. Clones are isolated under `AUDITAGENT_WORKSPACE_ROOT`.
3. **Token Security**: OAuth access/refresh tokens are stored as AES-GCM ciphertext via `TokenCipher`. Plaintext tokens are never logged, persisted, or returned to clients.
4. **Remediation Orchestration**: Uses a 16-stage LangGraph4j state machine with `SupervisorNode`. Dynamic stage prompts load from `.auditagent/skills/stage-*/SKILL.md`. Recursion limit is 150.
5. **PR Lifecycle**: PRs are generated autonomously upon verification. Merging a PR sends a GitHub webhook that transitions finding status to `FIXED`.
6. **Data Storage & Retrieval**: PostgreSQL/DuckDB is transactional. Memory retrieval uses lexical FTS/BM25 enriched by security concepts; there are **no vector embeddings or external vector databases**.

---

## 4. Documentation Maintenance Obligations

Under project rules (see `AGENTS.md` and `doc/14-change-guide.md`), **documentation is mandatory and part of the implementation**:

- Any change to APIs, SSE events, schema, security boundaries, configuration, dependencies, scanner rules, agent tools, prompts, or workflows **must** update the corresponding documentation in `doc/` in the same change.
- `doc/03-feature-catalog.md` must be kept synchronized with all product and implementation features.
- When finishing changes, ensure documentation links are valid and verify with `.githooks/pre-commit`.
