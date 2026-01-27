# Assumptions and Design Decisions

This document captures all design decisions and assumptions made during implementation.

## Authentication & Security

- **JWT Signing Algorithm**: RSA-256 (RS256) using public/private key pair
  - More realistic for production than HMAC
  - Allows token validation without sharing secret keys
  - Public key exposed via `/oauth2/jwks` endpoint

- **Token Structure**: 
  - Access tokens with `sub`, `roles`, `exp`, `iat` claims
  - Refresh tokens: Not implemented (can be added later)
  - Token expiry: 1 hour for access tokens

- **RBAC Roles**: `ROLE_USER`, `ROLE_ADMIN` (extensible)

## Data Stores

- **ArchiveDB (Postgres)**:
  - Schema: `reports` table with columns: `id`, `report_request_id`, `report_type`, `status`, `created_at`, `completed_at`, `report_data` (JSONB)
  - Used for: Report metadata and payload storage

- **MongoDB (Config Central)**:
  - Stubbed as in-memory service for simplicity
  - Can be replaced with real MongoDB later
  - Used for: AIMS configuration data

## Eventing

- **Kafka Topics**:
  - `report.requested`: Partitioned by `reportRequestId` (6 partitions default)
  - `report.completed`: Partitioned by `reportRequestId` (6 partitions)
  - `report.failed`: Partitioned by `reportRequestId` (6 partitions)
  - `report.dlq`: Dead Letter Queue (6 partitions)

- **Partitioning Strategy**:
  - Default: 6 partitions per topic
  - Reasoning: Allows for 2-6 consumer pods with good parallelism
  - Key: `reportRequestId` ensures ordering per request

- **Delivery Semantics**:
  - Producer: At-least-once (with idempotent consumer)
  - Consumer: At-least-once with idempotent processing
  - Offset commit: After successful processing (manual commit)

- **Retry Strategy**:
  - Retryable errors: Transient failures (network, DB connection, timeout)
  - Non-retryable: Invalid report type, authorization failures
  - Backoff: Exponential (1s, 2s, 4s, 8s)
  - Max retries: 3 attempts before DLQ

## Report Types

- **Performance Report**: Client performance metrics
- **Benchmark/Summary Report**: Benchmark comparisons
- **By-Product-Type Report**: Grouped by product type
- **Diversification Report**: Portfolio diversification metrics
- **Asset-Allocation Report**: Asset allocation breakdown

All reports return JSON format for simplicity.

## Service Ports

- `api-gateway-bff`: 8080
- `auth-service`: 8081
- `reports-ms`: 8082
- `client-performance-svc`: 8083
- `aims-config-central`: 8084

## Resilience

- **Circuit Breaker**: 
  - Failure threshold: 50% failures in 10 requests
  - Half-open after: 60 seconds
  - Timeout: 5 seconds per downstream call

- **Retry**: 
  - Max attempts: 3
  - Backoff: Exponential (500ms, 1s, 2s)

## Observability

- **Correlation ID**: 
  - Generated at gateway if missing (UUID)
  - Propagated via `X-Correlation-ID` HTTP header
  - Included in Kafka message headers
  - Logged via MDC

- **Logging**: 
  - Structured JSON logs for production
  - Console logs for local development
  - Log level: INFO by default

- **Metrics**: 
  - Basic health endpoints: `/actuator/health`
  - Custom metrics: Report generation count, failure count (via Actuator)

## Kubernetes

- **Replicas**:
  - Gateway: 2 replicas
  - Reports-MS: 3 replicas (to match 6 partitions)
  - Other services: 1 replica each

- **Resource Limits**:
  - CPU: 500m request, 1000m limit
  - Memory: 512Mi request, 1Gi limit

- **Probes**:
  - Liveness: `/actuator/health/liveness`
  - Readiness: `/actuator/health/readiness`

## CI/CD

- **Build Tool**: Maven
- **Docker Registry**: Placeholder (e.g., `your-registry.io/edjo-bim`)
- **Deployment Strategy**: Rolling update (default Kubernetes behavior)
- **Pipeline Stages**: Build → Test → Build Docker → Push → Deploy via Helm
