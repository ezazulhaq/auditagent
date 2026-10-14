# ==============================================================================
# AuditAgent Production Multi-Stage Dockerfile
# Equipped with all built-in security auditing and remediation tools:
#   - Java 21 JDK (Spring Boot runtime & Java compilation)
#   - Semgrep CLI (Static analysis scanning engine)
#   - Maven (Java build & test runner)
#   - Gradle (Java/Kotlin build & test runner)
#   - Git (Repository workspace management)
#   - Python 3 & pip (Semgrep & script execution)
#   - Docker CLI (Sandboxed agent execution client)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Frontend Builder
# ------------------------------------------------------------------------------
FROM node:22-alpine AS frontend-builder
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ------------------------------------------------------------------------------
# Stage 2: Backend Builder
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-bookworm AS backend-builder
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn/ .mvn/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

COPY src/ ./src/
# Inject pre-built frontend static assets into Spring Boot's static resources
COPY --from=frontend-builder /app/src/main/resources/static/ ./src/main/resources/static/

RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 3: Runtime Image with Built-in Tools
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-bookworm AS runtime

LABEL maintainer="AuditAgent Core Team" \
      description="AuditAgent AI-assisted security auditing and automated remediation platform"

# Avoid interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive \
    GRADLE_VERSION=8.10.2 \
    AUDITAGENT_HOME=/app \
    AUDITAGENT_DATA_DIR=/var/lib/auditagent/data \
    AUDITAGENT_WORKSPACE_DIR=/var/lib/auditagent/workspaces

# Install core OS packages, build tools, python, git, and utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-venv \
    maven \
    git \
    curl \
    unzip \
    ca-certificates \
    procps \
    docker.io \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle
RUN curl -sSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle \
    && rm /tmp/gradle.zip

# Install Semgrep CLI statically for security scanning
RUN python3 -m pip install --no-cache-dir --break-system-packages semgrep

# Verify installation of built-in tools
RUN java -version \
    && semgrep --version \
    && mvn -version \
    && gradle -version \
    && git --version \
    && python3 --version

# Create dedicated non-root service user and group
RUN groupadd -g 10001 auditagent \
    && useradd -u 10001 -g auditagent -m -s /bin/bash auditagent

# Create required application and data directories
RUN mkdir -p ${AUDITAGENT_HOME}/rules \
             ${AUDITAGENT_HOME}/.auditagent \
             ${AUDITAGENT_DATA_DIR} \
             ${AUDITAGENT_WORKSPACE_DIR} \
    && chown -R auditagent:auditagent ${AUDITAGENT_HOME} \
                                     /var/lib/auditagent

WORKDIR ${AUDITAGENT_HOME}

# Copy the packaged Spring Boot executable JAR
COPY --from=backend-builder --chown=auditagent:auditagent /app/target/auditagent-*.jar /app/auditagent.jar

# Copy security scanning rules and dynamic stage skills
COPY --chown=auditagent:auditagent rules/ /app/rules/
COPY --chown=auditagent:auditagent .auditagent/ /app/.auditagent/

# Set standard environment defaults
ENV AUDITAGENT_DB_PATH=${AUDITAGENT_DATA_DIR}/audit_reports.duckdb \
    AUDITAGENT_WORKSPACE_ROOT=${AUDITAGENT_WORKSPACE_DIR} \
    SERVER_PORT=8173

EXPOSE 8173

# Switch to non-root service user
USER auditagent

# Healthcheck checking the unauthenticated session readiness endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -f http://localhost:8173/api/auth/session || exit 1

ENTRYPOINT ["java", "-jar", "/app/auditagent.jar"]
