# Skill catalog

`AuditAgent` dynamically discovers folders under `.auditagent/skills`. A skill folder name may contain lowercase letters, numbers, and hyphens, and must contain `SKILL.md`. Skills are rediscovered for each lookup or `/api/skills` listing.

## `stage-*` (the 16 code review stages)

Status: `ACTIVE`.

These 16 skills (`stage-planning`, `stage-discovery`, `stage-security`, `stage-memory-review`, `stage-data-flow`, etc.) are actively loaded by `SkillManagerService` and injected dynamically into the 16-stage `MultiAgentRemediationGraph`. The `stage-planning` skill is mandatory and always runs first to generate a structured remediation plan. Modifying these `SKILL.md` files changes the system instructions for the respective code review agents in real-time.

## `patch-engineer`

Status: legacy/historical.

Formerly loaded by `LlmService.buildAgenticSystemPrompt()` for a rigid 3-stage loop. Replaced by the 16-stage workflow described above.

## `db-manager`

Status: discoverable historical/declarative playbook; not invoked by the current Java workflow.

The file describes three conceptual tool names:

- `db-manager`;
- `generate-duckdb-report`;
- `fetch-vuln-from-duckdb`.

No corresponding tools are registered in `LlmService.buildToolSpecifications()`. Current scanning, report persistence, and finding lookup are implemented directly by `ScanOrchestratorService`, `ScannerService`, `ReporterService`, and `DatabaseService`.

The file contains multiple frontmatter-style sections. `SkillManagerService` uses the final `---`-separated body as instructions and the first description it finds as the exposed description; it does not parse multiple independently callable skills from one file.

## `github-pr-manager`

Status: discoverable historical/declarative playbook; not invoked by the current Java workflow.

The playbook refers to `github-pr-manager` and a `human-gateway` approval prerequisite. Neither is a registered remediation tool. Current GitHub publication is implemented by:

- structured `APPROVE_AND_CREATE_PR`;
- run/finding/digest validation;
- current user/App permission checks;
- `PullRequestLifecycleService`;
- `GitHubProvider` and `GitHubApiClient`.

Do not connect this historical playbook directly to the model as an alternate publication path. Doing so would bypass the current approval boundary unless all structured controls were reimplemented.

## Adding or changing a skill

1. Add `.auditagent/skills/<lowercase-name>/SKILL.md`.
2. Put one frontmatter block at the top with `name` and `description`.
3. Put the instruction body after the closing `---`.
4. Register any actual tool schemas and implementations explicitly; a skill file alone does not create tools.
5. Decide which Java service loads the skill.
6. For mutating capabilities, preserve server authorization, durable idempotency, evidence, recovery, and structured approval.
7. Add skill parsing, model-loop, and security tests.
8. Update this catalog and the feature catalog.
