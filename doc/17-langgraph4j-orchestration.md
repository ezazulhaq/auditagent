# LangGraph4j orchestration

This document describes the orchestration layer for AI agent workflows within AuditAgent. We have migrated from a native `while`-loop execution model to a state machine paradigm using `LangGraph4j`.

## Architecture overview

`LangGraph4j` allows us to express complex, multi-step LLM operations as Directed Cyclic Graphs.

The graph executes in two concentric loops:
1. **Remediation workflow (outer)**: `RemediationWorkflowGraph` manages the macro-state of a vulnerability. It prepares the workspace (`clone_workspace`), triggers the inner agent loop, pauses for human approval (`approval_ready`), and conditionally handles publishing (`publish`) or rejecting (`reject`).
2. **Remediation agent (inner)**: `RemediationGraph` manages the micro-state of an LLM turn. It iterates between the LLM (`model_call`), executing tasks (`tool_execution`), preventing loops (`stuck_detection`), and verifying completion (`verification_gate`).

## Persistence & recovery

The `DuckDbCheckpointSaver` intercepts every graph transition and stores the snapshot as JSON in DuckDB (`graph_checkpoints`).

This guarantees that:
- Server restarts or process crashes do not lose the conversation or patch progress.
- Human-in-the-loop workflows (like the `approval_ready` interrupt) can cleanly suspend execution, freeing up virtual threads, and securely resume only upon authenticated user input (`decide` API).

## Multi-agent topology & unified workflow (phases 3 and 4)

The single agent graph was evolved into a specialized multi-agent graph:
- `MultiAgentRemediationGraph`: Acts as the Supervisor orchestrator, evaluating current state and dynamically delegating to the appropriate specialized sub-agent.
- **16-stage agentic code review workflow**: The supervisor has access to 16 distinct worker agents. A mandatory PLANNING stage runs first to generate a structured remediation plan, followed by specialized stages (Discovery, Security Review, Data-Flow, Root-Cause Analysis, etc.). Each stage is executed as an isolated sub-graph built via `WorkerAgentGraphFactory`. Graph definitions utilize increased `.recursionLimit(150)` overrides to gracefully accommodate the multi-hop worker flows.
- The agents communicate via shared `agentOutputs` in the `MultiAgentState`, ensuring the supervisor maintains global context when routing. The `planGenerated` and `remediationPlan` state fields track whether the mandatory planning phase has completed.
- **Context management**: LLM-based summarization compresses dropped conversation turns when the context budget is exceeded (`MemoryConfig.summarizationEnabled`). Tool outputs are aggressively truncated before persistence (`MemoryConfig.maxToolOutputChars`).
- **Docker sandbox**: When enabled (`AgentConfig.sandboxEnabled`), shell commands are wrapped in `docker run` with `--network=none`, `--read-only`, and configurable resource limits.
- **Dynamic prompts**: Agent system prompts are dynamically loaded via `SkillManagerService` reading markdown files exported under `.auditagent/skills/stage-*/SKILL.md`.

Additionally, `AuditWorkflowGraph` provides a unified graph over scan, chat, and remediation processes, and streams real-time state change events (`graph_step`) to the frontend's Graph Timeline UI using Server-Sent Events (SSE). Token streaming is also enabled for live chat interactions.
