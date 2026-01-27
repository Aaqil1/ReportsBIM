# Architecture Overview

## System Architecture

The EDJO-BIM Reports Platform is an event-driven microservices architecture built with Spring Boot, Kafka, and Kubernetes.

```
┌─────────────────────────────────────────────────────────────┐
│                    Client Applications                       │
└───────────────────────┬─────────────────────────────────────┘
                        │ HTTPS + JWT
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              API Gateway / BFF (api-gateway-bff)            │
│  - OAuth2 Resource Server (JWT validation)                 │
│  - BFF Aggregation (calls downstream services)              │
│  - Circuit Breaker / Retry / Timeout                       │
│  - Correlation ID generation/propagation                    │
│  - Kafka Producer (report.requested)                       │
└───────────────────────┬─────────────────────────────────────┘
                        │ HTTP (parallel calls)
        ┌───────────────┴───────────────┐
        ▼                               ▼
┌──────────────────┐          ┌──────────────────┐
│ client-performance│          │ aims-config-     │
│      -svc        │          │   central        │
│  Port: 8083      │          │  Port: 8084      │
└──────────────────┘          └──────────────────┘
                        │
                        │ Kafka Events
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Kafka Cluster                            │
│  Topics:                                                    │
│    - report.requested (6 partitions)                        │
│    - report.completed (6 partitions)                       │
│    - report.failed (6 partitions)                           │
│    - report.dlq (6 partitions)                              │
└───────────────────────┬─────────────────────────────────────┘
                        │ Consumer
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              Reports Microservice (reports-ms)              │
│  - Kafka Consumer (idempotent)                             │
│  - Strategy Pattern (report generation)                     │
│  - ArchiveDB persistence                                    │
│  - Kafka Producer (report.completed/failed)                │
│  Port: 8082                                                 │
└───────────────────────┬─────────────────────────────────────┘
                        │ JDBC
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              ArchiveDB (PostgreSQL)                          │
│  Tables:                                                    │
│    - reports (metadata + JSONB payload)                    │
│    - processed_requests (idempotency tracking)             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│              Auth Service (auth-service)                    │
│  - JWT Token Issuance (RSA-256)                            │
│  - JWKS Endpoint                                            │
│  Port: 8081                                                 │
└─────────────────────────────────────────────────────────────┘
```

## Service Responsibilities

### 1. API Gateway / BFF (`api-gateway-bff`)
- **Port**: 8080
- **Responsibilities**:
  - Exposes external REST APIs
  - Validates JWT tokens (OAuth2 Resource Server)
  - Implements BFF pattern: aggregates data from downstream services
  - Publishes report requests to Kafka
  - Correlation ID generation and propagation
  - Resilience patterns: Circuit Breaker, Retry, Timeout

### 2. Auth Service (`auth-service`)
- **Port**: 8081
- **Responsibilities**:
  - Issues JWT access tokens (RSA-256)
  - Exposes JWKS endpoint for token validation
  - RBAC roles: `ROLE_USER`, `ROLE_ADMIN`

### 3. Reports Microservice (`reports-ms`)
- **Port**: 8082
- **Responsibilities**:
  - Consumes `report.requested` Kafka events
  - Generates reports using Strategy pattern
  - Idempotent consumer (deduplicates by `reportRequestId`)
  - Persists reports to ArchiveDB
  - Publishes `report.completed` / `report.failed` events

### 4. Client Performance Service (`client-performance-svc`)
- **Port**: 8083
- **Responsibilities**:
  - Provides client performance metrics
  - Downstream service called by BFF

### 5. AIMS Config Central (`aims-config-central`)
- **Port**: 8084
- **Responsibilities**:
  - Provides AIMS configuration data
  - Downstream service called by BFF

## Data Flow

### Report Request Flow

1. **Client** → **API Gateway**: POST `/api/v1/reports/request` with JWT token
2. **API Gateway**:
   - Validates JWT token
   - Generates correlation ID (if missing)
   - Calls downstream services in parallel:
     - `client-performance-svc` (with correlation ID header)
     - `aims-config-central` (with correlation ID header)
   - Aggregates enrichment data
   - Publishes Kafka event: `report.requested` (with correlation ID in headers)
   - Returns `reportRequestId` to client

3. **Kafka**: Routes event to `reports-ms` consumer (partitioned by `reportRequestId`)

4. **Reports-MS**:
   - Consumes event (idempotent check)
   - Selects report generation strategy based on `reportType`
   - Generates report
   - Persists to ArchiveDB
   - Marks request as processed (idempotency)
   - Publishes `report.completed` event

5. **Client** → **API Gateway**: GET `/api/v1/reports/{reportRequestId}` to retrieve report

## Design Patterns

### Strategy Pattern
- **Location**: `reports-ms` service
- **Purpose**: Different report generation algorithms
- **Implementation**: `ReportGenerationStrategy` interface with implementations:
  - `PerformanceReportStrategy`
  - `BenchmarkReportStrategy`
  - `ByProductTypeReportStrategy`
  - `DiversificationReportStrategy`
  - `AssetAllocationReportStrategy`
- **Factory**: `ReportStrategyFactory` selects strategy based on `reportType`

### Factory Pattern
- **Location**: `ReportStrategyFactory` in `reports-ms`
- **Purpose**: Creates appropriate strategy instance

### Idempotent Consumer Pattern
- **Location**: `reports-ms` consumer
- **Purpose**: Handle duplicate Kafka events
- **Implementation**: `ProcessedRequest` table tracks processed `reportRequestId`

### Circuit Breaker Pattern
- **Location**: `api-gateway-bff` when calling downstream services
- **Implementation**: Resilience4j
- **Configuration**: 50% failure threshold, 60s wait duration

### BFF (Backend for Frontend) Pattern
- **Location**: `api-gateway-bff`
- **Purpose**: Aggregates multiple downstream service calls
- **Implementation**: Parallel calls to `client-performance-svc` and `aims-config-central`

## Technology Stack

- **Runtime**: Java 17
- **Framework**: Spring Boot 3.2.0
- **Build**: Maven
- **Messaging**: Apache Kafka 7.5.0
- **Database**: PostgreSQL 15 (ArchiveDB)
- **Security**: Spring Security OAuth2 Resource Server, JWT (RSA-256)
- **Resilience**: Resilience4j (Circuit Breaker, Retry, TimeLimiter)
- **Containerization**: Docker
- **Orchestration**: Kubernetes, Helm

## Scalability Considerations

- **Kafka Partitions**: 6 partitions per topic (allows 2-6 consumer pods)
- **Consumer Group**: `reports-ms-group` (3 replicas = 2 partitions per pod)
- **Gateway Replicas**: 2 (load balanced)
- **Partitioning Key**: `reportRequestId` (ensures ordering per request)

## Security

- **Authentication**: JWT tokens issued by `auth-service`
- **Authorization**: RBAC roles in JWT claims
- **Token Validation**: JWKS endpoint (`/oauth2/jwks`)
- **Correlation ID**: Propagated via HTTP headers and Kafka message headers

## Observability

- **Correlation ID**: Generated at gateway, propagated everywhere, logged via MDC
- **Logging**: Structured logs with correlation ID
- **Metrics**: Spring Boot Actuator endpoints
- **Health Checks**: Liveness and readiness probes
