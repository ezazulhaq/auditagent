# Scalability, Stability, and Maintainability Roadmap (Phases 1–6)

## Overview

This document specifies the complete, multi-phase architectural roadmap for scaling and hardening **AuditAgent** from a single-node, virtual-thread-bound application to a distributed, horizontally scalable, and maintainable enterprise platform.

```mermaid
flowchart LR
    P1["Phase 1: Concurrency & Real-Time Stability<br/><i>(Implemented in v2.7.0)</i>"] --> P2["Phase 2: Database & Storage Hardening<br/><i>(Implemented in v2.8.0)</i>"]
    P2 --> P3["Phase 3: AI Framework Consolidation<br/><i>(Implemented in v2.9.0)</i>"]
    P3 --> P4["Phase 4: Multi-Agent Star Topology<br/><i>(Implemented in v2.11.0)</i>"]
    P4 --> P5["Phase 5: Workspace & Sandbox Decoupling<br/><i>(Implemented in v2.13.0)</i>"]
    P5 --> P6["Phase 6: Frontend State & Protocol Unification<br/><i>(Implemented in v2.14.0)</i>"]
```

### Roadmap Summary

| Phase | Core Objective | Key Architectural Interventions | Status |
| :--- | :--- | :--- | :--- |
| **Phase 1** | **Concurrency & Real-Time Stability** | RabbitMQ `auditagent.events` fanout exchange, striped `ReentrantLock` concurrency, `enqueueAndStreamScan` backpressure, semaphore-bounded triage, LangGraph4j checkpoint scoping. | **Implemented (v2.7.0)** |
| **Phase 2** | **Database & Storage Hardening** | Stored `tsvector` with GIN indexing, bulk finding upserts (eliminating N+1 queries), relational JSON bloat cleanup, real S3/Supabase Storage REST upload client. | **Implemented (v2.8.0)** |
| **Phase 3** | **AI Framework Consolidation** | Standardize exclusively on LangChain4j (deprecating Spring AI duplication), decompose monolithic `LlmService.java` (1,313 lines), unify client caching pools. | **Implemented (v2.9.0)** |
| **Phase 4** | **Multi-Agent Star Topology Hardening** | Structured JSON schema routing for `SupervisorNode`, milestone context window compression, decoupled supervisor iteration budgets from retry limits. | **Implemented (v2.11.0)** |
| **Phase 5** | **Workspace & Sandbox Decoupling** | Stateless workspace reconstruction via patch bundles in object storage, true containerized sandbox execution for untrusted repository builds (`mvn`, `gradle`). | **Implemented (v2.13.0)** |
| **Phase 6** | **Frontend State & Protocol Unification** | Centralize 12 fragmented `useState` maps into `useRemediationReducer`, expose repository SSE event stream (`GET /api/repositories/{id}/events`), eliminate PR polling in favor of real-time server push. | **Implemented (v2.14.0)** |

---

## Phase 1: Concurrency and Real-Time Stability *(Implemented in v2.7.0)*

### 1.1 Problem Statement & Objectives
- **Broken Multi-Node Event Streaming:** In-memory `jobSinks` maps caused remote worker progress events to be invisible to web ingress pods serving client Server-Sent Events (SSE) connections.
- **Carrier Thread Pinning:** Method-level and block-level `synchronized` keywords around blocking disk/network I/O in [GitHubAuthService.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/GitHubAuthService.java) and [CloneWorkspaceNode.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/graph/workflow/CloneWorkspaceNode.java) pinned Java 21 OS carrier threads.
- **Scan Queue Bypassing:** Primary scan endpoint `POST /api/scan` bypassed RabbitMQ, running CPU-heavy Semgrep AST parsing directly on web pods without backpressure.
- **Unbounded Parallel Triage:** [FindingTriageService.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/FindingTriageService.java) launched unbounded virtual threads, triggering `HTTP 429 Too Many Requests` cascades during large scans.
- **Checkpoint Collisions:** Sub-agent executions defaulted to `"default"` `threadId`, causing multi-tenant checkpoint overwrites in PostgreSQL table `graph_checkpoints`.

### 1.2 Architecture & Flow
```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser (React SSE)
    participant Web as Web Ingress Pod
    participant RMQ as RabbitMQ Broker
    participant Worker as Worker Pod
    participant DB as PostgreSQL

    Client->>Web: POST /api/scan or /api/analyze
    Web->>RMQ: Enqueue task (Direct Exchange, prefetch applied)
    Web-->>Client: 200 OK (text/event-stream)
    RMQ->>Worker: Consume Task
    loop Execution Steps
        Worker->>DB: Persist Checkpoints & Findings
        Worker->>RMQ: Publish JobEvent to 'auditagent.events' (Fanout)
        RMQ->>Web: Ephemeral Queue delivers event to Web Pods
        Web->>Client: SSE data: {"type": "progress", ...}
    end
    Worker->>RMQ: Publish Terminal Event (complete / error)
    Web->>Client: SSE complete event + sink.tryEmitComplete()
```

### 1.3 Implemented Components
1. **RabbitMQ Event Topology**: Declared durable `auditagent.events` fanout exchange in [RabbitMqConfig.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/config/RabbitMqConfig.java) and ephemeral, auto-delete `AnonymousQueue` on web pods.
2. **Striped `ReentrantLock` Striping**: Replaced global `synchronized` locks with identifier-keyed locks (`userId` in `GitHubAuthService` and `repoId:branch:fingerprint` in `CloneWorkspaceNode`), eliminating carrier thread pinning.
3. **Queue-Backed Scans**: Added `enqueueAndStreamScan` in [ScanOrchestratorService.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/ScanOrchestratorService.java) and updated [AuditController.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/controller/AuditController.java) with virtual-thread local fallback.
4. **Bounded Triage Concurrency**: Wrapped triage calls in a `Semaphore` (default: 8 permits) with exponential backoff on HTTP 429 errors.
5. **Scoped Checkpoint Keys**: In [MultiAgentRemediationGraph.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/graph/MultiAgentRemediationGraph.java), sub-agent graph executions pass `String.format("%s:%s:%s", threadId, runId, stage.name())`.

### 1.4 Implementation and Verification Checklist (Phase 1)
- [x] **RabbitMQ Event Topology**: Declared `auditagent.events` exchange and ephemeral queue listener in `RabbitMqConfig.java` and `EventBroadcastService.java`.
- [x] **Lock Refactoring**: Replaced `synchronized` in `GitHubAuthService.java` and `CloneWorkspaceNode.java` with identifier-scoped `ReentrantLock` striping.
- [x] **Scan Orchestration**: Implemented `enqueueAndStreamScan` in `ScanOrchestratorService.java` and updated `AuditController.java`.
- [x] **Triage Throttling**: Added `Semaphore` concurrency bounding and 429 backoff retry in `FindingTriageService.java`.
- [x] **Checkpoint Scoping**: Passed scoped `threadId` in `MultiAgentRemediationGraph.java`.
- [x] **Automated Testing**:
  - `GitHubAuthServiceConcurrencyTest`: Verified concurrent access token calls do not block across distinct users.
  - `CloneWorkspaceNodeConcurrencyTest`: Verified parallel remediation startups for distinct findings execute concurrently.
  - `EventBroadcastServiceRabbitMqTest`: Verified cross-pod event publishing routes to local sinks.
  - `FindingTriageConcurrencyTest`: Verified semaphore constraints active HTTP calls under load.
  - Full test suite: 170 tests run, 0 failures, 0 errors, 2 skipped (`BUILD SUCCESS`).
- [x] **Documentation Updates**: Synchronized `doc/03-feature-catalog.md`, `doc/04-architecture.md`, `doc/05-backend-guide.md`, and `doc/18-scalability-and-messaging-architecture.md`.

---

## Phase 2: Database Performance Hardening & Storage Offloading *(Implemented in v2.8.0)*

### 2.1 Problem Statement & Objectives
1. **Unindexed On-The-Fly FTS**: In [PostgresKnowledgeBaseService.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/PostgresKnowledgeBaseService.java#L118-L122), queries compute `to_tsvector('english', ...)` dynamically on text strings inside the `WHERE` clause for every row. As approved memories accumulate, every search incurs a high-CPU sequential table scan.
2. **N+1 Finding Persistence**: In [ScanDataService.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/data/ScanDataService.java#L150-L181), saving a scan iterates over findings sequentially, executing an individual `SELECT` (`findFirstByRepoPathAndFindingFingerprint`) and individual `save()` per finding (2,000+ DB queries for a 1,000-finding scan).
3. **Relational Table Bloat**: Finding details are duplicated: stored once as relational rows in `vulnerabilities` and once stringified inside `reports.findings_json`.
4. **Stubbed Storage Service**: [SupabaseStorageService.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/storage/SupabaseStorageService.java#L68) currently logs a comment (`// Future HTTP client call...`) and falls back to writing files to the local node filesystem, defeating multi-pod artifact access.

### 2.2 Technical Design

#### 1. Generated `search_vector` Column with GIN Index
Update PostgreSQL schema for `repository_memories` to store pre-computed lexemes:
```sql
ALTER TABLE repository_memories 
ADD COLUMN IF NOT EXISTS search_vector tsvector 
GENERATED ALWAYS AS (
    to_tsvector('english', 
        coalesce(title, '') || ' ' || 
        coalesce(summary, '') || ' ' || 
        coalesce(root_cause, '') || ' ' || 
        coalesce(remediation_pattern, '') || ' ' || 
        coalesce(search_text, '')
    )
) STORED;

CREATE INDEX IF NOT EXISTS idx_repo_memories_search_vector 
ON repository_memories USING GIN(search_vector);
```
In `PostgresKnowledgeBaseService.java`, simplify candidate retrieval to:
```sql
SELECT memory_id, repo_path, finding_fingerprint, rule_id, category, ...
FROM repository_memories 
WHERE repo_path = ? AND approved = ? AND search_vector @@ plainto_tsquery('english', ?)
ORDER BY ts_rank_cd(search_vector, plainto_tsquery('english', ?)) DESC 
LIMIT ?;
```

#### 2. Native PostgreSQL Batch Finding Upsert
Replace iterative `save()` in `ScanDataService.java` with Spring `JdbcTemplate.batchUpdate` or native PostgreSQL bulk upsert:
```sql
INSERT INTO vulnerabilities (id, repo_path, file_path, line_number, severity, vuln_type, description, code_snippet, language, proposed_fix, status, rule_id, finding_fingerprint, triage_actionable, triage_confidence, triage_classification, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (id) DO UPDATE SET
    severity = EXCLUDED.severity,
    status = EXCLUDED.status,
    proposed_fix = EXCLUDED.proposed_fix,
    triage_actionable = EXCLUDED.triage_actionable,
    triage_confidence = EXCLUDED.triage_confidence,
    triage_classification = EXCLUDED.triage_classification;
```

#### 3. Real Supabase / S3 Storage Upload Client
Implement reactive HTTP uploads via Spring `WebClient` in `SupabaseStorageService.java`:
```java
public String uploadBinaryArtifact(String objectPath, byte[] data) {
    if (isRemoteStorageConfigured()) {
        webClient.post()
            .uri("/storage/v1/object/{bucket}/{path}", bucketName, objectPath)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(data)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(15));
        return "storage://" + bucketName + "/" + objectPath;
    }
    return saveToLocalFallback(objectPath, data);
}
```

### 2.3 Configuration Additions
```yaml
auditagent:
  database:
    batch-size: ${AUDITAGENT_DB_BATCH_SIZE:100}
  supabase:
    storage:
      enabled: ${AUDITAGENT_STORAGE_REMOTE_ENABLED:true}
      timeout-seconds: 30
```

### 2.4 Implementation and Verification Checklist (Phase 2)
- [x] **Database Migration**: Added `search_vector tsvector GENERATED ALWAYS AS ... STORED` column and GIN index to `repository_memories` via `PostgresSchemaMigrationService.java`.
- [x] **FTS Query Refactoring**: Updated `PostgresKnowledgeBaseService.java` to query pre-indexed `search_vector` column with `ts_rank_cd(search_vector, ...)` instead of calculating `to_tsvector` dynamically in the `WHERE` clause, with graceful fallback.
- [x] **Bulk Upsert Ingestion & Finding Reconciliation**: Implemented native batch upsert in `ScanDataService.java` using `JdbcTemplate.batchUpdate` with PostgreSQL `ON CONFLICT (id) DO UPDATE SET ...` (and H2 `MERGE INTO` compatibility for test execution) to eliminate N+1 round trips during scan ingestion, along with automatic pruning of stale or resolved findings omitted from rescans.
- [x] **JSON Bloat Elimination**: Deprecated duplicated `reports.findings_json` column (persisted as `null`); load findings lazily/transparently from normalized `vulnerabilities` table with legacy fallback.
- [x] **Supabase Storage REST Client**: Implemented real HTTP upload and download methods in `SupabaseStorageService.java` using Spring `WebClient.Builder`, supporting unified diffs, SARIF dumps, and text/binary artifacts with bearer token auth and local filesystem fallback.
- [x] **Automated Testing**:
  - `PostgresKnowledgeBaseFtsPerformanceTest`: Verifies FTS candidate ranking, exact fingerprint matching priority, negative memory retrieval, and idempotent schema migration.
  - `ScanDataServiceBatchUpsertTest`: Verifies 1,000 findings persist cleanly, `reports.findings_json` is null, findings load transparently from normalized table, and status/fixes are preserved across rescans.
  - `SupabaseStorageClientTest`: WireMock standalone test verifying REST payload, bearer authorization headers, HTTP 500 fallback, and disabled remote storage fallback to local disk.

---

## Phase 3: AI Framework Consolidation & Pipeline Decomposition *(Implemented in v2.9.0)*

### 3.1 Problem Statement & Objectives
1. **Framework Fragmentation**: The application imports Spring AI (`ChatModel`), LangChain4j (`ChatModel`), official OpenAI Java SDK (`OpenAIClient`), and raw Spring `WebClient` simultaneously.
2. **Dual Caching Pools**: [UserLlmClientFactory.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/UserLlmClientFactory.java) maintains two parallel caches: `agentModelCache` and `springChatModelCache`.
3. **Monolithic Service Violating SRP**: [LlmService.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/LlmService.java) (1,313 lines) bundles chat endpoints, tool specifications, execution dispatching, token auditing, run checkpoints, and reflection summaries.

### 3.2 Technical Design

#### 1. Standardization on LangChain4j
- Migrate all chat conversations ([`LlmService.chat()`](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/LlmService.java#L1030)) from Spring AI to LangChain4j `ChatModel`.
- Remove `spring-ai-openai-spring-boot-starter` from `pom.xml`, removing redundant dependency trees and eliminating split configuration keys.

#### 2. Decomposition of `LlmService.java`
Extract focused domain services:
```text
LlmService.java (1,313 lines)
    ├── ChatConversationService.java       (Handles chat intent matching, user dialogue)
    ├── RemediationToolRegistry.java       (LangChain4j tool specifications, schema, invocation)
    ├── LlmTokenAuditService.java          (Token recording, usage quotas, metrics)
    └── MemoryReflectionService.java       (Diff summarization, global positive/negative patterns)
```

```mermaid
flowchart TD
    subgraph Decomposed Architecture
        C[ChatConversationService] -->|uses| M[UserLlmClientFactory]
        R[RemediationToolRegistry] -->|uses| T[AgentToolService]
        A[LlmTokenAuditService] -->|persists to| DB[(PostgreSQL)]
        F[MemoryReflectionService] -->|generates| GP[Global Patterns]
    end
```

### 3.3 Configuration Consolidation
Consolidate all LLM configuration strictly under `auditagent.llm.*`:
```yaml
auditagent:
  llm:
    provider: openai-compatible
    base-url: ${OPENAI_BASE_URL:https://openrouter.ai/api/v1}
    api-key: ${OPENAI_API_KEY}
    model-name: ${PROVIDER_MODEL:z-ai/glm-5.3-flash}
    supervisor-model-name: ${SUPERVISOR_MODEL_NAME:z-ai/glm-5.3-flash}
```

### 3.4 Implementation and Verification Checklist (Phase 3)
- [x] **LangChain4j Chat Migration**: Migrated `LlmService.chat()` and `chatStream()` from Spring AI to LangChain4j `ChatModel` and `StreamingChatModel`.
- [x] **Dependency Pruning**: Removed `spring-ai-starter-model-openai`, `spring-ai-markdown-document-reader`, and `spring-ai-bom` from `pom.xml`, with zero Spring AI dependencies remaining on classpath.
- [x] **Service Decomposition**:
  - Extracted `ChatConversationService.java` for interactive chat, tag parsing (`[TRIGGER_FIX]`, `[TRIGGER_SCAN]`), and GitHub issue matching.
  - Extracted `RemediationToolRegistry.java` for tool specifications, permission filtering, and execution dispatching.
  - Extracted `MemoryReflectionService.java` for failure/success pattern diff summaries.
  - Extracted `LlmTokenAuditService.java` for token metrics recording and database persistence.
- [x] **Unified Client Factory**: Refactored `UserLlmClientFactory.java` to maintain a single model cache (`Map<String, dev.langchain4j.model.chat.ChatModel>`), removing Spring AI models and caches.
- [x] **Automated Testing**:
  - `ChatConversationServiceTest`: Verified tag detection, prompt formatting, and streaming using LangChain4j test doubles.
  - `RemediationToolRegistryTest`: Verified strict tool validation and error serialization.
  - `LlmTokenAuditServiceTest`: Verified token metrics recording and database persistence.
  - Full test suite: 194 tests run, 0 failures, 0 errors, 2 skipped (`BUILD SUCCESS`) with zero Spring AI classes on classpath.

---

## Phase 4: Multi-Agent Star Topology & Supervisor Hardening *(Implemented in v2.11.0)*

### 4.1 Problem Statement & Objectives
1. **Fragile String Matching**: [SupervisorNode.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/graph/node/SupervisorNode.java#L166-L177) expects the LLM to reply with a raw enum name (`"Reply ONLY with the exact name..."`). Markdown, punctuation, or lowercase text causes fallback to `FAIL`.
2. **Quadratic Token Context Expansion**: `SupervisorNode` concatenates uncompressed outputs from all previous stages into `contextStr` on every hop, inflating prompt tokens quadratically.
3. **Execution Limit Conflict**: `auditagent.agent.max-iterations: 5` terminates supervisor execution with `FAIL` on iteration 5, making it impossible to complete a 17-stage workflow.

### 4.2 Technical Design

#### 1. Structured JSON Output for Supervisor Routing
Enforce structured JSON output with enum schema:
```json
{
  "nextAgent": "DISCOVERY",
  "reasoning": "Baseline tests passed; inspecting affected repository files for root cause."
}
```
In `SupervisorNode.java`:
```java
ChatRequest request = ChatRequest.builder()
        .messages(systemMessage, userMessage)
        .responseFormat(ResponseFormat.JSON)
        .build();

SupervisorDecision decision = objectMapper.readValue(
        response.aiMessage().text(), SupervisorDecision.class);
```

#### 2. Milestone-Based Context Summarization
Instead of raw tool and stage dumps, each worker agent persists a compact `StageMilestone`:
```java
public record StageMilestone(
    AgentStage stage, 
    boolean success, 
    String summary, 
    List<String> touchedFiles
) {}
```
The supervisor receives only structured milestones (capped at 150 tokens per stage) rather than entire execution transcripts.

#### 3. Decoupling Hop Limits from Failure Limits
Split `maxIterations` into two independent configuration properties:
- `max-supervisor-hops: 25` (allows navigating through all required review stages)
- `max-stage-retries: 3` (terminates only if a single worker stage fails repeatedly)

```mermaid
stateDiagram-v2
    [*] --> Supervisor
    Supervisor --> WorkerStage: nextAgent = STAGE
    WorkerStage --> Supervisor: StageMilestone (compact)
    Supervisor --> CompleteNode: nextAgent = COMPLETE
    Supervisor --> AbortNode: hops > maxSupervisorHops OR retries > maxStageRetries
    CompleteNode --> [*]
    AbortNode --> [*]
```

### 4.3 Configuration Additions
```yaml
auditagent:
  agent:
    max-supervisor-hops: ${AUDITAGENT_SUPERVISOR_MAX_HOPS:25}
    max-stage-retries: ${AUDITAGENT_STAGE_MAX_RETRIES:3}
    structured-supervisor: true
```

### 4.4 Implementation and Verification Checklist (Phase 4)
- [x] **Structured Routing**: Refactored `SupervisorNode.java` to use structured JSON output with `SupervisorDecision` DTO and LangChain4j `ResponseFormat.JSON`.
- [x] **Enum Resilience**: Added resilient `normalizeAgentEnum` fuzzy parser (stripping markdown backticks, asterisks, punctuation, quotes, hyphens, spaces, and case-insensitivity) with fallback extraction before defaulting to `FAIL`.
- [x] **Milestone Summarization**: Updated `WorkerAgentGraphFactory.java` and `MultiAgentRemediationGraph.java` so completed stages append structured `StageMilestone` records (capped at 150 tokens) to state rather than raw string blobs.
- [x] **Decoupled Hop Limits**: Introduced `max-supervisor-hops` (default: 25) independent of `max-stage-retries` (default: 3) in `AgentConfig.java` and `SupervisorNode.java`, allowing 10+ consecutive stages to execute seamlessly.
- [x] **Automated Testing**:
  - `SupervisorNodeStructuredTest`: Verified model JSON response deserialization, markdown code-block unwrapping, field aliases (`next_agent`, `agent`, `rationale`), fuzzy normalization, and disallowance of routing back to `PLANNING`.
  - `SupervisorContextCompressionTest`: Verified supervisor prompt tokens remain $O(N)$ with compact milestones rather than $O(N^2)$ quadratic explosion, with per-stage summary capped at 150 tokens.
  - `MultiAgentExecutionLimitTest`: Verified graph safely executes 12 consecutive review stages without hitting premature iteration aborts, and validates that reaching `maxSupervisorHops` or `maxStageRetries` aborts to `FAIL`.
  - Full test suite: 214 tests run, 0 failures, 0 errors, 2 skipped (`BUILD SUCCESS`).

---

## Phase 5: Workspace Storage Decoupling & Host Sandbox Hardening *(Implemented in v2.13.0)*

### 5.1 Problem Statement & Objectives
1. **Node-Local Workspace Dependency**: [PublicationNode.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/graph/workflow/PublicationNode.java#L80) reads `publication.workspacePath()` directly from the local filesystem. In a multi-pod cluster, if approval happens on a pod different from the worker, the directory does not exist.
2. **Host OS Execution of Untrusted Builds**: Default `auditagent.agent.sandbox-enabled: false` runs `mvn test`, `gradle test`, and build scripts directly on the host JVM container OS with host privileges.

### 5.2 Technical Design

#### 1. Stateless Patch Bundles in Object Storage
- When remediation passes validation on a worker pod, serialize the patch bundle (`git diff` and changed file list) and upload it to Supabase Storage / PostgreSQL `run_changes`.
- Clean up the local clone immediately on the worker node.
- When the user approves the PR (`POST /api/runs/{runId}/decision`), `PublicationNode` downloads the patch bundle, clones a clean workspace at `baseSha`, applies the patch, commits, and pushes to GitHub.

```mermaid
sequenceDiagram
    autonumber
    participant Worker as Worker Pod (Node A)
    participant Storage as Supabase Storage / DB
    participant Web as Web Pod (Node B)
    participant GH as GitHub API

    Worker->>Worker: Validate patch & run tests
    Worker->>Storage: Upload PatchBundle (diff, metadata, baseSha)
    Worker->>Worker: Delete local workspace directory
    
    Note over Web: User approves PR (hours or days later)
    Web->>Storage: Download PatchBundle
    Web->>Web: Shallow clone exact baseSha
    Web->>Web: Apply patch bundle & commit
    Web->>GH: Push branch & create Pull Request
    Web->>Web: Delete ephemeral workspace
```

#### 2. Ephemeral Containerized Sandbox Execution
When `auditagent.agent.sandbox-enabled: true`:
- Replace native `ProcessBuilder` with an ephemeral container runner (Docker-in-Docker or Kubernetes ephemeral Pod runner).
- Mount workspace read-only except for target build directories.
- Apply strict resource quotas:
  - CPU Limit: 2.0 cores
  - Memory Limit: 2 GB
  - Network: Restricted (offline builds or internal proxy only)
  - Dropped Linux capabilities (`CAP_NET_RAW`, `CAP_SYS_ADMIN`).

### 5.3 Configuration Additions
```yaml
auditagent:
  agent:
    sandbox-enabled: ${AUDITAGENT_SANDBOX_ENABLED:true}
    sandbox-image: ${AUDITAGENT_SANDBOX_IMAGE:auditagent-sandbox:2.7.0}
    sandbox-memory-limit: ${AUDITAGENT_SANDBOX_MEMORY:2g}
    sandbox-cpu-limit: ${AUDITAGENT_SANDBOX_CPU:2.0}
    stateless-workspaces: ${AUDITAGENT_STATELESS_WORKSPACES:true}
```

### 5.4 Implementation and Verification Checklist (Phase 5)
- [x] **Patch Bundle Persistence**: Implement `PatchBundleService` to serialize, upload, and download verified unified diffs and file change manifests.
- [x] **Stateless Publication**: Refactor `PublicationNode.java` to reconstruct workspaces from patch bundles at publish time, removing reliance on `run_publications.workspace_path` persistence across nodes.
- [x] **Workspace Lifecycle Cleanup**: Ensure scan and remediation workspaces are deleted immediately upon task completion or failure.
- [x] **Containerized Process Runner**: Implement `ContainerizedProcessRunner.java` implementing `ManagedProcessRunner` with Docker/container CLI abstraction when sandbox is enabled.
- [x] **Automated Testing**:
  - `StatelessPublicationTest`: Remediate on Node A double, publish on Node B double using only remote patch bundle.
  - `ContainerizedSandboxSecurityTest`: Verify environment variables, host filesystem boundaries, and resource limits inside sandbox containers.
  - Full test suite: 230 tests run, 0 failures, 0 errors, 2 skipped (`BUILD SUCCESS`).

---

## Phase 6: Frontend State Decomposition & Protocol Unification *(Implemented in v2.14.0)*

### 6.1 Architectural Context & Post-Phase 5 Evolution

Since the original Phase 6 outline was drafted, AuditAgent has undergone major architectural advances that fundamentally expand the scope and requirements of frontend state and real-time event streaming:

1. **Multi-Agent Star Topology with 16 Review Stages (Phase 4, v2.11.0)**:
   - Remediation workflows transitioned to a 16-stage LangGraph4j state machine (`SupervisorNode` + `WorkerAgentGraphFactory`).
   - The execution engine streams granular `graph_step` events (`node`, `status`, `time`) to the frontend for live visualization in `GraphTimeline.jsx` and `RemediationTimeline.jsx`.
   - In the legacy frontend, `graphStepsMap` was grafted on as another ad-hoc dictionary in `useRemediationController.js`, worsening map fragmentation and state divergence.

2. **Vercel UI SDK Primitives & Agent-Readiness Integration (v2.12.0)**:
   - The UI modernized its interactive layer using `@ui-sdk/react` (`CustomSelect`, `Modal`, `ConfirmDialog`, `WorkspaceTabs`, `Switch`) and `@layer ui-sdk;` DevSecOps token bindings.
   - Introduced `frontend/src/utils/agentBridge.js` connecting `createAgent` and `registerTools` for WebMCP and AI assistant actuation.
   - Frontend state must now expose clean, deterministic snapshot structures that autonomous agents can query (`agent.snapshot()`) and actuate (`agent.act()`) without triggering UI layout thrashing or cascading re-renders.

3. **Stateless Patch Bundles & Containerized Sandboxes (Phase 5, v2.13.0)**:
   - Remediations produce `PatchBundle` records stored in Supabase Storage and `run_changes`.
   - Approval previews fetch verified diffs and approval digests (`approvalDigest`) via `/api/runs/{runId}/approval-preview`.
   - Ephemeral workspaces are reconstructed on-demand during publication, creating asynchronous state transitions (`PUBLISHING`, `PUBLISHED`, `PUBLISH_FAILED`, `PR_OPEN`).

4. **Notifications Subsystem (v2.10.0)**:
   - The frontend integrates server-persisted notifications (`/api/notifications`) and custom events (`notification-added`).
   - Currently, `useRemediationController.js` interleaves 10 raw `notify()` calls across various asynchronous callback branches, mixing side-effect delivery with state mutation.

5. **Batch Remediation Analysis**:
   - `analyzeBatch` allows running remediation across multiple vulnerabilities concurrently, managing batch progress (`batchState`) and per-vulnerability SSE routing.
   - The current hook manually iterates over arrays of IDs and mutates up to 6 different maps imperatively.

6. **The Real-Time Transport Reality**:
   - The original roadmap assumed frontend could simply "remove setInterval in favor of SSE terminal event handling".
   - In actual implementation, `POST /api/runs/{runId}/decision` SSE streams close immediately after `pr_created`.
   - When the PR is merged on GitHub minutes or days later, the GitHub webhook hits `/api/webhooks/github` -> `PullRequestLifecycleService.handlePullRequestEvent()`, updating DuckDB/PostgreSQL.
   - However, because the backend lacked a persistent repository push stream, `useRemediationController.js` was forced to introduce a 5-second polling loop (`setInterval(..., 5000)`) querying `/api/reports` and `/api/memory/threads`.
   - To genuinely eliminate polling, Phase 6 must bridge both ends: implement a server-side repository event stream (`GET /api/repositories/{id}/events`) and connect the frontend reducer directly to server push.

---

### 6.2 Problem Statement & Current Bottlenecks

1. **State Map Fragmentation in `useRemediationController.js` (688 lines)**:
   - State is scattered across 12 distinct `useState` maps keyed by `vulnId`:
     `runs`, `publications`, `previews`, `isAnalyzingMap`, `isPublishingMap`, `isLoadingPreviewMap`, `isLoadingRunMap`, `hasCheckedRunMap`, `graphStepsMap`, `batchState`, `selectedFinding`, plus 3 mutable refs (`abortRefs`, `batchAbortRef`, `loadingRunVulnsRef`).
   - When SSE events arrive (`graph_step`, `workspace_prepared`, `approval_ready`, `pr_created`), up to 7 separate `setState` calls are invoked in sequence.
   - This causes cascading re-renders across the drawer, findings table, and timeline, as well as temporary state tears where `runs[id]` reflects a new status but `previews[id]` or `graphStepsMap[id]` lag behind.

2. **Duplicated Imperative Normalization**:
   - Snake_case vs camelCase field reconciliation (`vulnerability_id` -> `vulnerabilityId`, `run_id` -> `runId`) is manually duplicated across `hydrate`, `getActiveRunForVulnerability` fallback, and `loadApprovalPreview`.

3. **HTTP Polling Storms & Latency Window**:
   - Whenever any run has status `PR_OPEN`, `useRemediationController.js` polls every 5 seconds via `setInterval`.
   - In multi-tab or multi-user environments, this creates continuous $N \times (1 / 5\text{s})$ request traffic against web ingress pods, and adds up to 5,000ms latency before a merged PR is reflected in the UI.

4. **Scattered Side Effects & Notification Coupling**:
   - 10 distinct callbacks directly invoke `notify('success' | 'error', ...)` and `toast.*`, tightly coupling presentation side-effects to network event handlers.

---

### 6.3 Technical Design (Revised)

#### 1. Centralized Domain Reducer Architecture (`useRemediationReducer`)

Consolidate all 12 fragmented state maps into a single normalized entity state tree:

```typescript
interface RemediationEntity {
  vulnId: string;
  status: 'DETECTED' | 'QUEUED' | 'ACTIVE' | 'AWAITING_APPROVAL' | 'PUBLISHING' | 'PUBLISHED' | 'PUBLISH_FAILED' | 'PR_OPEN' | 'FIXED' | 'IGNORED' | 'FAILED';
  run: AgentRunRecord | null;
  publication: RunPublicationRecord | null;
  preview: ApprovalPreview | null;
  graphSteps: GraphStep[];
  isAnalyzing: boolean;
  isPublishing: boolean;
  isLoadingPreview: boolean;
  isLoadingRun: boolean;
  hasCheckedRun: boolean;
  error: string | null;
}

interface RemediationState {
  selectedFindingId: string | null;
  entities: Record<string, RemediationEntity>;
  batch: {
    active: boolean;
    total: number;
    completed: number;
    failed: number;
    vulnIds: string[];
  } | null;
}
```

```javascript
// Pure reducer action types
export const RemediationActionTypes = {
  SELECT_FINDING: 'SELECT_FINDING',
  DESELECT_FINDING: 'DESELECT_FINDING',
  SCAN_RESET: 'SCAN_RESET',
  HYDRATE: 'HYDRATE',
  ANALYSIS_QUEUED: 'ANALYSIS_QUEUED',
  WORKSPACE_PREPARED: 'WORKSPACE_PREPARED',
  GRAPH_STEP_RECEIVED: 'GRAPH_STEP_RECEIVED',
  APPROVAL_READY: 'APPROVAL_READY',
  ANALYSIS_COMPLETE: 'ANALYSIS_COMPLETE',
  ANALYSIS_FAILED: 'ANALYSIS_FAILED',
  DECISION_STARTED: 'DECISION_STARTED',
  DECISION_FAILED: 'DECISION_FAILED',
  PR_CREATED: 'PR_CREATED',
  RUN_LOADED: 'RUN_LOADED',
  PREVIEW_LOADED: 'PREVIEW_LOADED',
  BATCH_STARTED: 'BATCH_STARTED',
  BATCH_PROGRESS: 'BATCH_PROGRESS',
  BATCH_COMPLETE: 'BATCH_COMPLETE',
  BATCH_CANCELLED: 'BATCH_CANCELLED',
  PR_STATUS_UPDATED: 'PR_STATUS_UPDATED',
  SYNC_FINDINGS: 'SYNC_FINDINGS',
};
```

#### 2. Centralized Entity Normalizer (`remediationNormalizer.js`)

Extract a dedicated normalization module obeying SRP:
- Takes raw backend payloads (`activeRuns`, `activeRun`, `publications`, `previews`, `activeFinding`, `findings`) and produces clean, camelCase entity records.
- Guarantees consistent mapping of `vulnerabilityId`, `runId`, `commitSha`, and `approvalDigest` in one place.

#### 3. Real-Time Repository SSE Event Stream (`GET /api/repositories/{repositoryId}/events`)

To eliminate the 5-second polling loop permanently:
1. **Backend Event Emitter**:
   - In [PullRequestLifecycleService.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/service/PullRequestLifecycleService.java), when GitHub webhooks report a PR merged or closed, publish a `pr_lifecycle` event to RabbitMQ `auditagent.events`:
     ```java
     eventBroadcastService.broadcastEvent(
         "repo-" + repositoryId,
         publication.runId(),
         "pr_lifecycle",
         Map.of(
             "type", "pr_lifecycle",
             "repositoryId", repositoryId,
             "branch", publication.baseBranch(),
             "pullRequestNumber", prNumber,
             "vulnId", vulnerability.getId(),
             "vulnerabilityStatus", vulnerability.getStatus().name(),
             "pullRequestState", merged ? "MERGED" : "CLOSED",
             "merged", merged
         )
     );
     ```
2. **Repository Event Stream Endpoint**:
   - In [AuditController.java](file:///home/coder/workspace/auditagent/src/main/java/com/cb/auditagent/controller/AuditController.java), add `GET /api/repositories/{repositoryId}/events?branch={branch}`:
     ```java
     @GetMapping(value = "/repositories/{repositoryId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
     public Flux<String> streamRepositoryEvents(
             @PathVariable long repositoryId,
             @RequestParam(required = false) String branch,
             Authentication authentication) {
         AuthenticatedUser user = requireUser(authentication);
         repositoryAccess.requireRepositoryAccess(user, repositoryId);
         return eventBroadcastService.listenRepositoryEvents(repositoryId, branch);
     }
     ```
3. **Frontend Real-Time Listener**:
   - When a repository and branch are selected in `AuditContext`, open an SSE stream to `/api/repositories/{repositoryId}/events?branch={branch}`.
   - When a `pr_lifecycle` event arrives with `merged: true`:
     - Dispatch `PR_STATUS_UPDATED` directly to `remediationReducer` (updating the entity to `FIXED` instantly with zero polling).
     - Automatically refresh report findings lazily without background interval churn.
4. **Resilient Adaptive Fallback**:
   - Completely eliminate the fixed 5-second `setInterval` loop.
   - If the SSE connection drops or remote transport fails, fallback to an adaptive backoff poll (15s -> 30s -> 60s) that immediately pauses when `document.visibilityState === 'hidden'` and resets as soon as SSE reconnects.

#### 4. Architecture Data Flow & Sequences

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer on GitHub
    participant GH as GitHub Webhook
    participant Ingress as Web Ingress Pod
    participant RMQ as RabbitMQ (auditagent.events)
    participant Client as React Client (SSE Stream)
    participant Reducer as useRemediationReducer

    Dev->>GH: Merges PR #42 on GitHub
    GH->>Ingress: POST /api/webhooks/github (action: closed, merged: true)
    Ingress->>Ingress: PullRequestLifecycleService updates DB (status: FIXED)
    Ingress->>RMQ: broadcastEvent("repo-123", "pr_lifecycle", merged=true)
    RMQ->>Ingress: Fanout delivers event to Ingress Pods
    Ingress->>Client: SSE Push: {"type":"pr_lifecycle","vulnId":"VULN-42","status":"FIXED"}
    Client->>Reducer: dispatch({ type: 'PR_STATUS_UPDATED', vulnId: 'VULN-42', status: 'FIXED' })
    Reducer-->>Client: Instant React UI Update (Timeline -> FIXED, Drawer reflects resolution)
```

```mermaid
flowchart TD
    subgraph Server Pod
        RMQ["RabbitMQ auditagent.events"] --> EB["EventBroadcastService"]
        EB --> ES["GET /api/repositories/{id}/events (SSE)"]
        AS["POST /analyze SSE Stream"]
        DS["POST /runs/{id}/decision SSE Stream"]
    end

    subgraph React Client State
        ES -.->|pr_lifecycle, scan_complete| R["useRemediationReducer"]
        AS -.->|graph_step, approval_ready| R
        DS -.->|pr_created, publish_failed| R
        R --> Selector["Memoized Selectors (activeRun, graphSteps, isAnalyzing)"]
        Selector --> FD["FindingDrawer (ui-sdk Dialog)"]
        Selector --> GT["GraphTimeline (16 Stages)"]
        Selector --> RT["RemediationTimeline"]
        Selector --> AB["agentBridge (WebMCP Autonomous Agent)"]
    end
```

#### 5. Integration with ui-sdk Primitives & Agent Bridge

- **Zero-Thrash Selectors**: Expose memoized derived selectors (`selectActiveEntity`, `selectAwaitingApproval`, `selectActiveRun`, `selectGraphSteps`) so that updates to one vulnerability in a batch do not re-render unaffected components.
- **WebMCP Tool Registration in `agentBridge.js`**:
  - Expose `getRemediationState`: Returns active finding analysis, graph steps, and approval preview for LLM tooling.
  - Expose `triggerRemediationDecision`: Dispatches approval/rejection with human confirmation guard (`step.confirm`).

---

### 6.4 Implementation and Verification Checklist (Phase 6)

- [x] **Data Normalizer**: Implement `frontend/src/features/remediation/remediationNormalizer.js` with comprehensive camelCase/snake_case mapping and entity creation.
- [x] **Domain Reducer**: Implement `frontend/src/features/remediation/useRemediationReducer.js` handling all action types, entity updates, and batch states.
- [x] **Backend Event Emitter**: In `PullRequestLifecycleService.java`, broadcast `pr_lifecycle` events to RabbitMQ upon PR merge/closure.
- [x] **Repository Event Endpoint**: In `EventBroadcastService.java` and `AuditController.java`, implement `listenRepositoryEvents` and `GET /api/repositories/{repositoryId}/events`.
- [x] **Hook Modernization**: Refactor `useRemediationController.js` to use `useRemediationReducer`, connect to the repository SSE stream, and remove the legacy 5-second `setInterval` polling loop.
- [x] **Contract Normalization**: Update `frontend/src/types.js` with TypeScript/JSDoc types for `RemediationEntity`, `GraphStep`, and repository SSE payloads.
- [x] **Agent Bridge Alignment**: Register remediation state query and action tools in `frontend/src/utils/agentBridge.js`.
- [x] **Automated Testing Suite**:
  - `remediationNormalizer.test.js`: Unit tests for entity creation, snake_case normalization, and diff preview preservation.
  - `useRemediationReducer.test.js`: Comprehensive tests for all state transitions, graph steps, batch remediation, and errors.
  - `useRemediationController.test.jsx`: Verify hook integration, event handling, decision flow, and zero-polling behavior.
  - `FindingDrawer.test.jsx`: Component tests verifying reactive updates to 16-stage graph steps and PR status.
  - `RepositoryEventsControllerTest.java`: Backend test verifying repository SSE stream receives and pushes `pr_lifecycle` events.
  - Full suite verification: Backend `./mvnw test` (232 tests passed, 0 failures) and frontend `npm --prefix frontend test:run` (143 tests passed across 30 test files).

---

## Complete Multi-Phase Verification & Execution Checklist

| Phase | Milestone | Primary Verification Gate | Status |
| :--- | :--- | :--- | :--- |
| **Phase 1** | **Concurrency & Real-Time Stability** | RabbitMQ cross-pod event SSE test + non-blocking virtual thread lock tests. | **[x] Implemented (v2.7.0)** |
| **Phase 2** | **Database & Storage Hardening** | GIN `search_vector` FTS ranking + bulk batch upsert test ($<500\text{ms}$) + WireMock storage test. | **[x] Implemented (v2.8.0)** |
| **Phase 3** | **AI Framework Consolidation** | Complete removal of Spring AI dependencies + decomposed `LlmService` unit tests. | **[x] Implemented (v2.9.0)** |
| **Phase 4** | **Multi-Agent Star Topology** | Structured JSON schema supervisor test + token budget verification across 10+ hops. | **[x] Implemented (v2.11.0)** |
| **Phase 5** | **Workspace & Sandbox Decoupling** | Stateless patch bundle PR publication test + container resource limit enforcement. | **[x] Implemented (v2.13.0)** |
| **Phase 6** | **Frontend State & Protocol Unification** | Reducer state transitions + zero-polling repository SSE event stream verification. | **[x] Implemented (v2.14.0)** |
