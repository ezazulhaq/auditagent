# AuditAgent 🛡️🤖

AuditAgent is an intelligent security auditing and compliance tool powered by Generative AI. It scans codebases for vulnerabilities using standard security tools and provides an autonomous **Agentic Remediation** loop to investigate, fix, and verify security issues without manual developer intervention.

## 🌟 Key Features

*   **Automated Security Scanning:** Integrates with external security scanning engines (like Semgrep) to identify vulnerabilities.
*   **Chat & Triage Interface:** An interactive Chat Agent that helps developers understand vulnerabilities and explore potential fixes.
*   **Autonomous Remediation Agent:** A powerful ReAct (Reasoning and Acting) loop that can:
    *   Read files and search the codebase to understand context.
    *   Apply patches directly to the code.
    *   Compile the project to ensure no build breaks.
    *   Run unit tests to catch regressions.
    *   Rescan files to verify the vulnerability is resolved.
*   **Approval Workflow:** Developers remain in control. The agent proposes a fix and applies it, but it can be easily rolled back if rejected.
*   **Persistent Findings:** Vulnerabilities and scan metadata are cached locally using DuckDB for fast subsequent access.

For more details on how the AI Agents work, please see [AGENTS.md](./AGENTS.md).

## 🏗️ Project Architecture & Tech Stack

AuditAgent is a full-stack application leveraging a modern, reactive architecture built around AI agent patterns.

### Architecture Overview
1. **Frontend UI (React + Vite):** Provides a conversational interface where developers can trigger scans, review findings, chat with the AI about specific vulnerabilities, and approve/reject agent-proposed fixes. Uses Server-Sent Events (SSE) to receive real-time streaming updates from the backend agents.
2. **Backend API (Spring WebFlux):** Handles incoming requests reactively. It orchestrates the scanning process, manages the DuckDB cache, and streams the AI agent's thought process and actions back to the client.
3. **AI Orchestration (Spring AI + OpenAI SDK):** The backend uses Spring AI to communicate with an OpenAI-compatible API. It sets up the ReAct loop, intercepts tool requests, executes them against the local filesystem, and returns the tool output back to the LLM.
4. **Security Engine (Semgrep):** A local CLI integration used to perform the actual static analysis of the codebase.

### Tech Stack
*   **Backend:** Java 21, Spring Boot (WebFlux), Spring AI, OpenAI SDK, DuckDB (JDBC).
*   **Frontend:** React 19, Vite, TailwindCSS 4, Lucide React.

## 📂 Folder Structure

```text
auditagent/
├── frontend/               # React/Vite Frontend Application
│   ├── public/             # Static assets
│   ├── src/                # React source code
│   │   ├── assets/         # Images, fonts, etc.
│   │   ├── App.jsx         # Main React component UI & SSE client
│   │   ├── index.css       # Tailwind entry point
│   │   └── main.jsx        # App entry point
│   ├── package.json        # Node dependencies
│   └── vite.config.js      # Vite configuration
├── rules/                  # Custom security scanning rules
├── src/                    # Spring Boot Backend Source Code
│   ├── main/
│   │   ├── java/com/cb/auditagent/
│   │   │   ├── aspect/     # AOP aspects for logging and monitoring
│   │   │   ├── config/     # Spring, AI, and Agent configurations
│   │   │   ├── controller/ # REST and SSE Controllers (AuditController)
│   │   │   ├── domain/     # Data models (Vulnerability, Report, etc.)
│   │   │   ├── dto/        # Data Transfer Objects
│   │   │   └── service/    # Core business logic & AI Agents (LlmService, OrchestratorService)
│   │   └── resources/
│   │       ├── application.yaml  # Spring properties
│   │       └── static/           # Static files
│   └── test/               # Backend tests
├── .mvn/                   # Maven Wrapper configuration
├── AGENTS.md               # Detailed documentation on the AI Agents workflow
├── HELP.md                 # Spring Boot generated help file
├── pom.xml                 # Maven dependencies and build configuration
├── README.md               # Project overview and setup instructions
└── run-dev.sh              # Helper script to run both backend and frontend locally
```

## 🚀 Getting Started

### Prerequisites
*   **Java 21**
*   **Node.js** (v18+)
*   **Maven** (optional, wrapper provided)
*   **Semgrep** (must be installed on your machine for the scanner to work)
*   **OpenAI API Key** or an OpenRouter key if using an alternative OpenAI-compatible provider.

### Setup

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd auditagent
   ```

2. **Configure Environment Variables:**
   Ensure you have access to an OpenAI-compatible model and set up your API credentials:
   ```bash
   export OPENAI_API_KEY="your_api_key"
   # Optional: If using OpenRouter or another proxy
   export OPENAI_BASE_URL="https://openrouter.ai/api/v1"
   export PROVIDER_MODEL="anthropic/claude-3.5-sonnet"
   ```

3. **Install Frontend Dependencies:**
   ```bash
   cd frontend
   npm install
   cd ..
   ```

### Running the Application (Developer Mode)

The easiest way to start both the Spring Boot backend and the Vite frontend simultaneously with hot-reloading is to use the provided dev script:

```bash
./run-dev.sh
```

*   **Backend API** will run on `http://localhost:8173`
*   **Frontend UI** will run on `http://localhost:5173`

*(Note: Logs are output to `backend.log` and `frontend.log` in the root directory)*

### Running Manually

**Backend:**
```bash
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm run dev
```

### GitHub App setup

AuditAgent now operates only on GitHub App installations. Local repository paths are not accepted by the shared API.
Create a GitHub App with a web callback and webhook, enable expiring user tokens, and grant only these repository
permissions:

- Metadata: read
- Contents: read and write
- Pull requests: read and write

Subscribe the webhook to pull-request events. Configure these server-side values through environment variables or your
secret manager; never expose them to the browser:

```bash
GITHUB_APP_ID=
GITHUB_APP_CLIENT_ID=
GITHUB_APP_CLIENT_SECRET=
GITHUB_APP_PRIVATE_KEY=
GITHUB_APP_WEBHOOK_SECRET=
AUDITAGENT_TOKEN_ENCRYPTION_KEY=
GITHUB_APP_CALLBACK_URL=https://audit.example.com/api/auth/github/callback
GITHUB_APP_INSTALLATION_URL=https://github.com/apps/your-auditagent-app/installations/new
AUDITAGENT_FRONTEND_URL=https://audit.example.com
AUDITAGENT_WORKSPACE_ROOT=/var/lib/auditagent/workspaces
AUDITAGENT_SECURE_COOKIES=true
```

The runtime flow is: select an installed repository and branch, scan its pinned commit, remediate in an isolated
managed clone, review the bound diff and verification evidence, and explicitly choose **Approve & create PR**. Only
that structured action can create the bot branch, commit, push, and ready-for-review pull request. A finding remains
`PR_OPEN` until a signed GitHub webhook (or restoration reconciliation) confirms merge; only then does it become
`FIXED` and eligible for positive repository memory.

Repository build and scan processes receive a scrubbed environment with their home, caches, and temporary files
redirected beneath the managed clone. In production, run remediation workers in a container or equivalent operating-
system sandbox that permits writes only to `AUDITAGENT_WORKSPACE_ROOT` and applies the deployment's required network
egress policy; Java path checks alone are not an operating-system security boundary for untrusted build scripts.

## ⚙️ Configuration

Key agent configurations can be modified in `src/main/resources/application.yaml`:

```yaml
auditagent:
  agent:
    max-iterations: 15
    compile-timeout-seconds: 120
    test-timeout-seconds: 180
    backup-dir: .auditagent/backups
    max-file-read-lines: 200
    max-retries: 3
```

## 🛡️ License

This project is licensed under the MIT License - see the LICENSE file for details.
