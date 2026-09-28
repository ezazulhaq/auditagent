# AI and remediation

## Two AI paths

AuditAgent deliberately uses two model integrations:

| Path                | Library                       | Purpose                                                                    |
|---------------------|-------------------------------|----------------------------------------------------------------------------|
| Conversational chat | Spring AI `ChatModel`         | Explain findings and emit scan/fix navigation tags.                        |
| Remediation agent   | LangChain4j `OpenAiChatModel` | Multi-turn tool calling with durable checkpoints and verification.         |

Both use the configured OpenAI-compatible model API, for example OpenRouter or Gemini.

### LLM provider compatibility and interception

When connecting to Gemini via its OpenAI-compatible endpoint, Gemini requires a proprietary `thought_signature` to be preserved across multi-turn tool calling. Because standard OpenAI SDKs (like `langchain4j-open-ai`) drop unknown fields like `thought_signature` during JSON deserialization, AuditAgent uses a custom OkHttp interceptor (`GeminiThoughtSignatureInterceptor`) to bridge this gap.

The interceptor operates transparently at the HTTP level:
1. It extracts `thought_signature` from `extra_content.google.thought_signature` on all incoming tool-call responses and caches it in memory.
2. On the next outgoing request, it re-injects the cached signature back into the exact same `extra_content` JSON structure before it hits the network.

This ensures full compatibility with Gemini's strict tool-calling requirements without requiring a fork of the LangChain4j OpenAI client.

## Conversational agent

The backend:

1. persists the user message;
2. loads recent general conversation;
3. loads current findings from the latest persisted report;
4. builds system instructions and active-finding context;
5. calls the LLM provider;
6. persists the assistant answer;
7. returns it to the browser.

The model can emit:

- `[TRIGGER_SCAN]`
- `[TRIGGER_FIX:VULN-XXXXXX]`

The frontend independently supports exact local commands before calling the model. Tags start workflows only; there is no approval tag.

## Remediation playbook

The remediation workflow uses a 16-stage Star Topology orchestrated by a Supervisor node. A mandatory PLANNING stage runs first to generate a structured remediation plan that guides subsequent agent routing. Prompts are dynamically loaded from `.auditagent/skills/stage-*/SKILL.md` (e.g., `stage-planning`, `stage-discovery`, `stage-security`, `stage-data-flow`). The service injects context including:

- managed workspace path;
- exact finding ID;
- path rules;
- maximum iterations and phase guidance;
- target finding evidence;
- dynamically injected global intelligence (proven fixes and anti-patterns) and up to eight approved repository memories.

The multi-agent workflow coordinates context gathering, exact patching, compilation, rescan, tests, rollback on failure, and structured verification.

## Dynamic knowledge injection and self-training

AuditAgent implements a self-training architecture that accumulates cross-repository remediation intelligence:

### Dynamic knowledge injection

When preparing the remediation prompt (`RepositoryMemoryService.retrieveForPrompt`), the system queries `global_remediation_patterns` by Semgrep rule ID before searching repository-specific memories.

The prompt dynamically receives:
- `### Global Security Intelligence for [rule_id]`:
  - `**ANTI-PATTERNS (What NOT to do):**` — approaches that failed in previous attempts.
  - `**PROVEN FIXES:**` — abstract remediation strategies proven to succeed.
- `### Previously approved repository remediations:` — repository-specific approved patterns.

Dynamic knowledge injection respects the `auditagent.memory.enabled` configuration guard and avoids emojis in section headers for model compatibility.

### Failure reflection

When the remediation agent reaches its maximum iteration budget (`maxIterations`) without producing a verified fix:
1. `LlmService.reflectOnFailure` is triggered asynchronously on a Java virtual thread (fire-and-forget, non-blocking).
2. The last 5 iterations of the failed trajectory are serialized to JSON and capped at 8,000 characters.
3. The LLM (`chatModel`) is prompted to generate a concise anti-pattern warning (max 500 characters) explaining what *not* to do for that specific Semgrep rule ID, without including proprietary code, variable names, or file paths.
4. The output is redacted and capped via `MemoryRedactor.redactAndCap(output, 2000)`.
5. The warning is transactionally appended as a `negative_pattern` to `global_remediation_patterns` via `DatabaseService.saveGlobalPatternTx`.

### PR diff learning

When a pull request created by AuditAgent is merged, the GitHub webhook handler (`PullRequestLifecycleService.handlePullRequestEvent`) triggers asynchronous learning:
1. A virtual thread fetches the PR diff via `GitHubApiClient.getPullRequestDiff` using the `Accept: application/vnd.github.v3.diff` header and `User-Agent: AuditAgent`.
2. The diff is capped to 10,000 characters.
3. `LlmService.summarizeDiffForGlobalPattern` prompts the LLM (`chatModel`) to extract a concise, abstract positive remediation pattern (max 500 characters) focusing on the security mechanism.
4. The output is redacted and capped via `MemoryRedactor.redactAndCap(output, 2000)`.
5. The pattern is transactionally appended as a `positive_pattern` to `global_remediation_patterns` via `DatabaseService.saveGlobalPatternTx`.

Both diff fetching and summarization execute asynchronously on virtual threads, ensuring webhook responses are never blocked.

## Tools

### `read_file`

Inputs: relative path, optional 1-based start/end.

- Both zero means from the beginning to configured maximum lines.
- Full reads default to at most 200 lines.
- Returns numbered text.
- Rejects nonexistent or non-regular files.

### `search_codebase`

Inputs: literal query and optional simple filename glob.

- Searches regular non-symlink files.
- Excludes `.git`, `node_modules`, `target`, `build`, `.auditagent`.
- Glob conversion supports `*` and `?`.
- Case-sensitive literal content match.
- Maximum 30 results.

### `apply_patch`

Inputs: file, exact original text, replacement.

- Requires exactly one occurrence.
- Creates backup first; backup failure prevents patch.
- Writes UTF-8 text.
- Returns before hash, after hash, and backup path for durable change evidence.

### `compile_project`

- Maven: `mvn -Dmaven.repo.local=<workspace cache> compile -q`.
- Gradle: `gradle compileJava`.
- Other: explicit `BUILD SKIPPED`.
- Default timeout: 120 seconds.
- Exit code is primary success signal.

### `rescan_file`

- Runs Semgrep dynamically using only the rule packs applicable to the target file's detected language, avoiding evaluation of irrelevant rules.
- Uses configured rule timeouts (via `ScannerConfig`) to prevent catastrophic backtracking on complex files.
- Metrics and version check are disabled.
- Exit 0 or 1 is accepted.
- Parses JSON and returns total remaining findings in that file.
- Verification currently requires exactly zero findings in the entire rescanned file, not merely disappearance of the original fingerprint.

### `run_linter` & `run_static_analysis`

- Runs Semgrep across the entire repository to check for linting or static analysis issues.
- Dynamically detects the languages used in the repository and selectively loads only the applicable rule packs.
- Uses configured rule timeouts (via `ScannerConfig`) to prevent hanging on catastrophic rules.

### `run_tests`

- Maven with a filter: `mvn test -pl . -Dtest=<glob>`.
- Maven all: contained local repository plus `test -q`.
- Gradle: `gradle test`.
- Other: explicit `TEST SKIPPED`.
- Default timeout: 180 seconds.

### `rollback_file`

Restores the latest timestamped backup for the relative file.

## Agent graph (LangGraph4j)

The former `while` loop has been replaced by a state-machine implementation using `langgraph4j` (`RemediationGraph`). We have further implemented a **Multi-Agent Architecture** (`MultiAgentRemediationGraph`) that decomposes the remediation task into specialized agents:

1. **SupervisorAgent**: Evaluates current state and dynamically delegates to the appropriate specialized sub-agent. Routes to the mandatory PLANNING stage first if no remediation plan exists.
2. **16 Specialized Worker Agents**: Includes agents like Planning, Discovery, Syntax, Baseline, Memory Review, Security Review, Data-Flow, Root-Cause Analysis, etc.

These specialized subgraphs are orchestrated by the parent `MultiAgentRemediationGraph` using a shared `MultiAgentState`. The parent routes control (e.g. if verification fails, the supervisor agent evaluates the state and routes back to the relevant code-modification agent). The PLANNING stage's output (`remediationPlan`) is included in the Supervisor's system prompt to guide routing order.

Context management uses two compression strategies: (1) LLM-based summarization of dropped conversation turns when the context window is exceeded (`MemoryConfig.summarizationEnabled`), and (2) aggressive tool output truncation before persistence (`MemoryConfig.maxToolOutputChars`). When sandbox mode is enabled (`AgentConfig.sandboxEnabled`), all agent shell commands (compile, test, lint) are executed inside an isolated Docker container with no network access and configurable resource limits.

Graph execution checkpoints are persisted in PostgreSQL (`DatabaseCheckpointSaver (formerly DuckDbCheckpointSaver)`) after each node, enabling flawless resumption of interrupted or paused workflows. The outer layer (`RemediationWorkflowGraph`) encompasses the entire remediation lifecycle including cloning, agent execution, automated verification, and autonomous publishing. It defaults to using the Multi-Agent approach but retains backward compatibility with the single-agent setup via the `auditagent.agent.use-multi-agent` flag.

Finally, the entire user journey is orchestrated by the `AuditWorkflowGraph`, which unifies scan, chat, remediation, and publication into a single graph timeline with token streaming and node-level progress events.

## Resilience behavior

- Model timeout: cancel the future, persist a concise retry instruction, continue if budget remains.
- Empty response: retry; abort after three consecutive empty responses.
- Tool error: return a safe error to the model so it can self-correct.
- Unknown tool: return an error rather than execute arbitrary behavior.
- Same tool repeatedly: inject a progress nudge after repeated consecutive use.
- Too much exploration: after two-thirds of iterations without a patch, tell the model to patch.
- Maximum iterations: fail without creating a PR.

## Durable idempotency

Each tool step hashes:

```text
tool name + newline + raw JSON arguments
```

For mutating `apply_patch` and `rollback_file`, a previously successful identical step in the same run is not repeated. Other read/verification tools may repeat because their result can change.

## Verification policy

A run is verified only when all are true:

- a patch was successfully applied;
- compilation passed or recorded an explicit unsupported-build skip;
- target file rescan returned zero vulnerabilities;
- tests were attempted and passed or recorded an explicit unsupported-runner skip;
- maximum iterations were not reached.

The model's prose cannot set these booleans. They come from tool result prefixes and are stored separately as evidence.

## Publication digest

The server serializes a canonical sorted structure containing:

- run ID;
- finding ID;
- repository ID;
- base branch;
- base SHA;
- planned branch;
- sorted changed-file hashes;
- sorted verification evidence.

SHA-256 of this JSON is the publication digest. It binds the PR creation explicitly to the generated content and evidence.

## Publication details

After publication logic is triggered:

- base branch must still be at the scanned SHA;
- user and App writes must still be allowed;
- workspace hashes/digest must still match;
- post-commit workspace must be clean and HEAD must match stored commit.

Commit:

```text
fix(security): remediate VULN-...

AuditAgent-Run: <runId>
Approved-by: @<login>
```

Author/committer: `AuditAgent[bot]`.

Pull request:

- title identifies finding type and ID;
- body contains summary, finding, rule, severity, approver, and run;
- non-draft and maintainer-modifiable;
- explicitly says normal review and CI are still required.

## Recovery

At restart, `ACTIVE`/`PUBLISHING` runs become interrupted.

- Unapproved resume validates every applied after-hash, restores prior verification booleans, and continues at stored iteration.
- Approved interruption is sent to publication retry instead of the model.
- Publication retry reuses stored commit and searches all PR states by head/base to avoid duplicates.
- Hash mismatch becomes conflict; mutating tools are not replayed automatically.

## Known implementation nuances

- `maxRetries` exists in configuration but the current loop primarily uses the global iteration budget; there is no separate retry-count loop.
- `GENERATING_FIX` and `VERIFYING` finding states exist but current managed orchestration mostly exposes `ANALYZING`, then autonomous `PR_OPEN` or `PATCH_FAILED`.
- The skill says tests are optional, but the code's verification gate requires a test attempt or explicit skip.
- Target rescan requires zero findings of any kind in the file, which can be stricter than "original finding disappeared."
- `LangChain` model temperature/output settings are in metadata defaults even though not explicitly present under `auditagent.agent` in `application.yaml`.
