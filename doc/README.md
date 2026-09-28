# AuditAgent documentation

This directory is the maintained documentation set for AuditAgent. It explains the product in plain language, then progressively adds the technical detail needed to operate, troubleshoot, test, or change it.

The implementation in `src/`, `frontend/src/`, `rules/`, and `.auditagent/skills/` is the source of truth. Older descriptions of local repository paths, browser-generated identities, chat-based approval, or direct file application are historical; the current shared application uses GitHub App authentication, managed clones, durable run state, structured approval, and pull requests.

## Choose your starting point

| Reader                                           | Start here                                                                    | Then read                                                        |
|--------------------------------------------------|-------------------------------------------------------------------------------|------------------------------------------------------------------|
| Business owner, auditor, or non-technical reader | [Product and business guide](01-product-and-business-guide.md)                | [Feature catalog](03-feature-catalog.md)                         |
| End user                                         | [User guide](02-user-guide.md)                                                | [Troubleshooting](13-troubleshooting.md)                         |
| New developer                                    | [Architecture](04-architecture.md)                                            | [Backend](05-backend-guide.md), [Frontend](06-frontend-guide.md) |
| API integrator                                   | [API reference](07-api-reference.md)                                          | [Security model](10-security-model.md)                           |
| Database or memory maintainer                    | [Data and persistence](08-data-and-persistence.md)                            | [AI and remediation](09-ai-and-remediation.md)                   |
| DevOps or platform engineer                      | [Setup, configuration, and operations](11-setup-configuration-operations.md) | [Security model](10-security-model.md)                           |
| Test engineer                                    | [Testing and quality](12-testing-and-quality.md)                              | [Change guide](14-change-guide.md)                               |

## Complete document set

1. [Product and business guide](01-product-and-business-guide.md) — why the product exists, value, actors, use cases, boundaries, and lifecycle.
2. [User guide](02-user-guide.md) — every visible user journey and screen behavior.
3. [Feature catalog](03-feature-catalog.md) — a traceable inventory of product and implementation features.
4. [Architecture](04-architecture.md) — components, data flows, trust boundaries, concurrency, and repository layout.
5. [Backend guide](05-backend-guide.md) — controllers, services, domain objects, and detailed responsibilities.
6. [Frontend guide](06-frontend-guide.md) — React structure, state controllers, UI behavior, and browser-side protocols.
7. [API reference](07-api-reference.md) — endpoints, request/response fields, authentication, CSRF, SSE events, and errors.
8. [Data and persistence](08-data-and-persistence.md) — PostgreSQL schema, migrations, identity, retention, retrieval, and recovery.
9. [AI and remediation](09-ai-and-remediation.md) — chat agent, tool-calling loop, tools, verification gates, approval, and publication.
10. [Security model](10-security-model.md) — controls, threats, secrets, filesystem safety, GitHub authorization, and deployment caveats.
11. [Setup, configuration, and operations](11-setup-configuration-operations.md) — prerequisites, environment variables, GitHub App setup, builds, startup, and maintenance.
12. [Testing and quality](12-testing-and-quality.md) — automated coverage, commands, test doubles, and release checks.
13. [Troubleshooting](13-troubleshooting.md) — symptoms, likely causes, evidence, and safe remedies.
14. [Change guide](14-change-guide.md) — where and how to implement future changes safely.
15. [Source inventory and glossary](15-source-inventory-and-glossary.md) — production-file map, rule inventory, state vocabulary, and terms.
16. [Skill catalog](16-skill-catalog.md) — active and historical skill playbooks, parser behavior, and runtime status.
17. [LangGraph4j orchestration](17-langgraph4j-orchestration.md) — dedicated state machine orchestration guide for agentic loops and workflows.

## Documentation conventions

- "Repository" means a GitHub repository made visible through an installed GitHub App.
- "Workspace" means a server-managed isolated clone under `AUDITAGENT_WORKSPACE_ROOT`.
- "Thread" means durable conversation state owned by one authenticated server user.
- "Run" means one durable remediation attempt for one finding.
- "Approval" means the structured `APPROVE_AND_CREATE_PR` API action with the current approval digest. Chat text never approves a write.
- `FIXED` means the remediation pull request was merged, not merely generated, verified, approved, or opened.
- Secrets in examples are placeholders. Never place real credentials in documentation, source control, logs, API payloads, or browser storage.

## Keeping these documents current

Every change should update the relevant document and the [feature catalog](03-feature-catalog.md). API, schema, security-boundary, run-state, configuration, and UI changes usually affect more than one document; use the checklist in [Change guide](14-change-guide.md).
