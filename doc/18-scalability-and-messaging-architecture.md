# Scalability and messaging architecture

## Overview

This document specifies the architectural roadmap for scaling AuditAgent from a single-node, virtual-thread-bound application to a distributed, horizontally scalable platform. It establishes the design for asynchronous task processing via open-source **RabbitMQ**, consolidates persistence and real-time delivery around self-hosted **Supabase** (PostgreSQL, Storage, Realtime), and introduces a unified runtime deployment model (`auditagent.mode=all|web|worker`).

```text
Browser (React 19)
  | HTTP / REST (202 Accepted) & WebSockets
  v
Web Ingress Nodes (auditagent.mode=web | all)
  | validates ownership, records job state
  +--> Supabase PostgreSQL (Job state: QUEUED)
  +--> RabbitMQ Direct Exchange (auditagent.direct)
         |
         +--> auditagent.scan.queue (prefetch: 2)
         |      |
         |      v
         |    Scan Workers (Semgrep CLI / AST Parsing)
         |
         +--> auditagent.remediation.queue (prefetch: 1)
                |
                v
              Remediation Workers (LangGraph4j Multi-Agent Loops)
                +--> Supabase PostgreSQL (Checkpoints, Findings, FTS Memories)
                +--> Supabase Storage (Diffs, SARIF JSON, PDF Reports)
                +--> Supabase Realtime (CDC WebSockets to React UI)
```

---

## 1. Architectural objectives and motivation

### Bottlenecks addressed

1. **Unmanaged virtual-thread compute contention:** Scanning and 16-stage remediation workflows previously ran directly inside JVM virtual threads (`Thread.startVirtualThread(...)`). Multiple concurrent scans saturated CPU and memory, risking operating system OOM kills.
2. **Crash recovery and backpressure:** In-flight virtual threads did not survive process restarts. RabbitMQ introduces durable message queues, manual acknowledgments (`ack`/`nack`), consumer prefetch limits, and Dead Letter Queues (`auditagent.dlq`).
3. **Database consolidation (DuckDB retirement):** Embedded DuckDB (`audit_reports.duckdb`) held exclusive single-process file locks, blocking multi-instance deployments. Consolidating all memories and search into Supabase PostgreSQL (via native `tsvector`, GIN indexes, and `pg_trgm`) enables multi-pod horizontal scaling.
4. **Real-time client synchronization:** Node-local Reactor SSE sinks (`Sinks.Many<String>`) failed across multi-pod clusters. Supabase Realtime (Change Data Capture over PostgreSQL WAL) distributes progress events to frontend clients over WebSockets regardless of which worker node processes the task.
5. **Relational database bloat:** Large unified diffs (up to 200,000 characters), raw SARIF outputs, and generated PDF binaries are offloaded from database columns to Supabase Storage (`auditagent-artifacts`).

---

## 2. Unified deployment mode (`auditagent.mode`)

AuditAgent supports three deployment profiles within a single codebase:

| Mode | Active components | Typical environment | Resource profile |
|---|---|---|---|
| `all` *(default)* | Web API, REST controllers, and Worker listeners | Local development, single-container Docker Compose | 2–4 CPU cores, 4–8 GB RAM |
| `web` | REST endpoints, authentication, session filter, task producer | Kubernetes Ingress, stateless API gateway | 1–2 CPU cores, 2 GB RAM (autoscaled on HTTP traffic) |
| `worker` | RabbitMQ consumers, Semgrep scanner, LangGraph4j loops | Background compute fleet, dedicated worker pools | 4–8 CPU cores, 8–16 GB RAM (autoscaled on queue depth) |

Configuration is driven by the environment variable `AUDITAGENT_MODE`:
```yaml
auditagent:
  mode: ${AUDITAGENT_MODE:all} # all | web | worker
```

---

## 3. RabbitMQ messaging architecture

### Topology

* **Exchange:** `auditagent.direct` (`DirectExchange`, durable)
* **Queues:**
  - `auditagent.scan.queue` (routing key: `task.scan`, durable, `x-max-priority: 10`, `x-dead-letter-exchange: auditagent.dlx`)
  - `auditagent.remediation.queue` (routing key: `task.remediation`, durable, `x-max-priority: 10`, `x-dead-letter-exchange: auditagent.dlx`)
  - `auditagent.dlq` (Dead Letter Queue, durable)
* **Dead Letter Exchange:** `auditagent.dlx` (`FanoutExchange`)

```text
                        +----------------------------+
                        | auditagent.direct Exchange |
                        +----------------------------+
                          /                        \
           routing key:  /                          \  routing key:
            'task.scan' /                            \ 'task.remediation'
                       v                              v
        +---------------------------+    +----------------------------------+
        |   auditagent.scan.queue   |    |   auditagent.remediation.queue   |
        |       (prefetch: 2)       |    |          (prefetch: 1)           |
        +---------------------------+    +----------------------------------+
                      |                                   |
                      v                                   v
             [Scan Task Consumer]               [Remediation Task Consumer]
                      |                                   |
                      +------- on failure (max retries) --+
                                          |
                                          v
                               +---------------------+
                               | auditagent.dlx      |
                               +---------------------+
                                          |
                                          v
                               +---------------------+
                               | auditagent.dlq      |
                               +---------------------+
```

### Backpressure and concurrency controls

To prevent worker host starvation:
* `auditagent.scan.queue` enforces `prefetchCount = 2`. Each worker pod executes a maximum of two Semgrep AST scans concurrently.
* `auditagent.remediation.queue` enforces `prefetchCount = 1`. Each worker executes a single 16-stage LangGraph4j remediation loop at a time to prevent LLM rate-limit saturation and compiler lock contention.

### Message payloads

Tasks are serialized with `Jackson2JsonMessageConverter`:

#### `ScanTaskPayload`
```json
{
  "jobId": "scan-job-uuid",
  "userId": "usr_12345",
  "threadId": "thread_abc",
  "repositoryId": 482910,
  "branch": "main",
  "baseSha": "e4f8b92a...",
  "scannerName": "semgrep",
  "forceRescan": false,
  "createdAt": "2026-11-09T16:00:00Z"
}
```

#### `RemediationTaskPayload`
```json
{
  "jobId": "remed-job-uuid",
  "userId": "usr_12345",
  "threadId": "thread_abc",
  "repositoryId": 482910,
  "branch": "main",
  "vulnerabilityId": "VULN-000042",
  "issueNumber": null,
  "createdAt": "2026-11-09T16:00:00Z"
}
```

---

## 4. Supabase persistence & search consolidation

### DuckDB retirement & PostgreSQL FTS migration

Embedded DuckDB is fully removed. The tables `repository_memories` and `global_remediation_patterns` are migrated to Supabase PostgreSQL:

```sql
-- Repository Memories with Full-Text Search
CREATE TABLE IF NOT EXISTS repository_memories (
    memory_id VARCHAR(64) PRIMARY KEY,
    repo_path VARCHAR(255) NOT NULL,
    finding_fingerprint VARCHAR(64),
    rule_id VARCHAR(255),
    category VARCHAR(100),
    vulnerability_type VARCHAR(100),
    language VARCHAR(50),
    framework VARCHAR(50),
    file_pattern VARCHAR(255),
    title VARCHAR(255),
    summary TEXT,
    root_cause TEXT,
    remediation_pattern TEXT,
    search_text TEXT,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    source_run_id VARCHAR(64) NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT TRUE,
    usage_count INTEGER NOT NULL DEFAULT 0,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(title, '') || ' ' || coalesce(summary, '') || ' ' || coalesce(root_cause, '') || ' ' || coalesce(remediation_pattern, ''))
    ) STORED,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_repo_memories_search ON repository_memories USING GIN(search_vector);
CREATE INDEX IF NOT EXISTS idx_repo_memories_fingerprint ON repository_memories(finding_fingerprint);
CREATE INDEX IF NOT EXISTS idx_repo_memories_rule ON repository_memories(rule_id);
```

### Retrieval algorithm

1. Exact finding fingerprint match (`finding_fingerprint = :fingerprint`).
2. Exact rule ID match (`rule_id = :ruleId`).
3. Lexical FTS match with BM25-style ranking:
   ```sql
   SELECT *, ts_rank_cd(search_vector, plainto_tsquery('english', :terms)) AS rank
   FROM repository_memories
   WHERE search_vector @@ plainto_tsquery('english', :terms)
     AND approved = true
   ORDER BY rank DESC, confidence DESC, usage_count DESC
   LIMIT 10;
   ```
4. Optional future semantic expansion: Vector similarity ranking via `pgvector` (`cosine_distance(embedding, :queryEmbedding)`).

---

## 5. Artifact storage & real-time synchronization

### Supabase Storage (Blob offloading)

Heavy artifacts are stored in bucket `auditagent-artifacts`:
* Raw Semgrep SARIF JSON: `scans/{scanId}/results.sarif`
* Unified Git Diffs (> 10 KB): `runs/{runId}/patch.diff`
* Generated PDF Reports: `reports/{reportKey}.pdf`

The database retains only the artifact storage key and metadata. The frontend accesses artifacts via signed, time-limited URLs generated by `SupabaseStorageService`.

### Supabase Realtime & SSE Stream Relay (Lifecycle progress streaming)

Worker nodes record all lifecycle events directly into PostgreSQL `job_event_log`:

| Column | Type | Description |
|---|---|---|
| `event_id` | `BIGSERIAL PRIMARY KEY` | Sequential event identifier |
| `job_id` | `VARCHAR(64)` | Associated scan or remediation job identifier (e.g. `remed-UUID` or `scan-UUID`) |
| `run_id` | `VARCHAR(64)` | Associated agent run identifier (optional) |
| `event_type` | `VARCHAR(50)` | `queued`, `workspace_prepared`, `graph_step`, `approval_ready`, `analysis_complete`, `publish_progress`, `pr_created`, `complete`, `error` |
| `payload_json` | `JSONB` / `TEXT` | Structured event data serialized as JSON |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | Event creation timestamp (UTC) |

#### Queue-to-SSE Event Bridge (`POST /api/analyze`)

When findings analysis is requested via `POST /api/analyze`:
1. `RemediationWorkflowService.analyze(...)` invokes `enqueueAndStreamAnalysis(...)` when messaging components (`TaskProducerService`, `EventBroadcastService`) are present (falling back to direct in-process execution in standalone test environments).
2. `EventBroadcastService.listenEvents(jobId)` registers an in-memory replay sink (`Sinks.many().replay().all()`) and returns an SSE `Flux<String>` emitting raw JSON payloads with 15-second keepalive pulses (`{"type":"keepalive"}`). Spring WebFlux's `ServerSentEventHttpMessageWriter` automatically wraps each payload into standard SSE framing (`data: <json>\n\n`), preventing duplicate framing issues.
3. `queued` event (containing both `vulnId` and `vulnerabilityId`) is broadcast and persisted to `job_event_log`.
4. `TaskProducerService.enqueueRemediation(payload, 10)` sends the task to RabbitMQ exchange `auditagent.direct` with routing key `task.remediation`.
5. `RemediationTaskConsumer` (QoS prefetch 1) receives the task and calls `executeRemediationTask(payload)`. On execution failure, `RemediationTaskConsumer` throws `AmqpRejectAndDontRequeueException`, rejecting the poison message (`requeue = false`) and routing it to dead-letter queue `auditagent.dlq` via `auditagent.dlx` instead of creating an infinite redelivery loop.
6. User identity is resolved via `database.findUserById(task.userId())` so commit authorship, reviewer state, and GitHub identity reflect the authenticated user's real GitHub login and details rather than fallback UUID placeholders.
7. Workspace preparation (`CloneWorkspaceNode`) queries active managed runs across all non-terminal statuses (`ACTIVE`, `INTERRUPTED`, `AWAITING_APPROVAL`, `CONFLICTED`, `PUBLISHING`, `PUBLISH_FAILED`, `PR_OPEN`). Any existing uncompleted or conflicted runs are marked `DISCARDED`, associated workspaces are cleaned up, and findings awaiting approval or retry transition safely to `DETECTED`.
8. As each node of the LangGraph4j multi-agent remediation graph executes, `EventBroadcastService.broadcastEvent(...)` persists the step to PostgreSQL `job_event_log` AND pushes the JSON payload to the active client stream. Every event includes explicit `vulnId` correlation.
9. Reaching `approval_ready` emits `approval_ready` followed by `analysis_complete` (or `complete` upon PR creation, or classified `error`), which completes the SSE Flux stream and automatically unregisters the sink.
10. The frontend remediation controller (`useRemediationController.js`) handles `queued`, `graph_step`, `approval_ready`, `analysis_complete`, `status`, and `error` events: setting runs to `FAILED` and refreshing reports upon error or rejection so finding drawers and timelines never remain stuck in `ACTIVE`. The drawer (`FindingDrawer.jsx`) enables retrying analysis for `PATCH_FAILED` and `FAILED` runs.
11. The frontend SSE parser (`frontend/src/api/sse.js`) provides defensive trimming of duplicate `data:` prefixes to ensure seamless state hydration in all proxy and container environments.

#### Verifying Findings Analysis in PostgreSQL `job_event_log`

To inspect logged lifecycle events for finding analysis jobs in PostgreSQL:
```sql
-- View all remediation events ordered by creation
SELECT event_id, job_id, run_id, event_type, created_at, payload_json
FROM job_event_log
WHERE job_id LIKE 'remed-%'
ORDER BY created_at ASC;

-- Summary of event counts by event type
SELECT event_type, COUNT(*) AS event_count
FROM job_event_log
GROUP BY event_type
ORDER BY event_count DESC;

-- Inspect the latest remediation job
SELECT event_id, event_type, payload_json
FROM job_event_log
WHERE job_id = (SELECT job_id FROM job_event_log WHERE job_id LIKE 'remed-%' ORDER BY created_at DESC LIMIT 1)
ORDER BY event_id ASC;
```

Supabase Realtime also listens to PostgreSQL logical replication on `job_event_log` and broadcasts updates over WebSockets:
```javascript
const channel = supabase
  .channel(`job-${jobId}`)
  .on('postgres_changes', {
    event: 'INSERT',
    schema: 'public',
    table: 'job_event_log',
    filter: `job_id=eq.${jobId}`
  }, payload => {
    handleJobEvent(payload.new);
  })
  .subscribe();
```

---

## 6. Docker Compose deployment topology

The existing `docker-compose.yml` and `docker-compose-dev.yml` are extended with the `rabbitmq` service:

```yaml
services:
  rabbitmq:
    image: rabbitmq:3-management-alpine
    container_name: auditagent-rabbitmq
    restart: unless-stopped
    ports:
      - "5672:5672"   # AMQP protocol
      - "15672:15672" # Management Web UI
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-auditagent}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-auditagent}
    volumes:
      - auditagent_rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD-SHELL", "rabbitmq-diagnostics -q check_port_connectivity || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - auditagent-net

  auditagent:
    # ...
    environment:
      AUDITAGENT_MODE: ${AUDITAGENT_MODE:-all}
      RABBITMQ_HOST: ${RABBITMQ_HOST:-rabbitmq}
      RABBITMQ_PORT: ${RABBITMQ_PORT:-5672}
      RABBITMQ_USER: ${RABBITMQ_USER:-auditagent}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-auditagent}
      SPRING_RABBITMQ_HOST: ${RABBITMQ_HOST:-rabbitmq}
      SPRING_RABBITMQ_PORT: ${RABBITMQ_PORT:-5672}
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER:-auditagent}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-auditagent}
    depends_on:
      rabbitmq:
        condition: service_healthy

volumes:
  auditagent_rabbitmq_data:
    name: auditagent_rabbitmq_data
```

### Container Network Host Resolution

When deploying via Docker Compose or PaaS orchestrators (like Dokploy) on Linux:
- `auditagent` connects to the `rabbitmq` service using container network DNS (`rabbitmq:5672`). Set `RABBITMQ_HOST=rabbitmq` in `.env` (the fallback in `docker-compose.yml` and `docker-compose-dev.yml` also defaults to `rabbitmq`).
- The healthcheck uses `rabbitmq-diagnostics -q check_port_connectivity` to guarantee that the AMQP listener on port 5672 is open and accepting socket connections before dependent services start.

---

## 7. Migration and execution phases

1. **Phase 1: Database consolidation:** Add `RepositoryMemoryPostgresRepository`, migrate memories from DuckDB, update `RemediationDataService`, and remove `duckdb_jdbc` dependency.
2. **Phase 2: Messaging foundation:** Add `spring-boot-starter-amqp`, configure `RabbitMqConfig`, payloads, and dead-letter exchanges.
3. **Phase 3: Worker integration:** Implement `ScanTaskConsumer` and `RemediationTaskConsumer`, adapt `ScanOrchestratorService` and `RemediationWorkflowService` to enqueue tasks and return `202 Accepted`.
4. **Phase 4: Realtime and storage:** Implement `SupabaseStorageService` and `JobEventLogEntity`, connect React client to Supabase Realtime channel.
5. **Phase 5: Verification and documentation:** Run end-to-end regression tests, verify worker crash recovery, and enforce `.githooks/pre-commit` compliance.
