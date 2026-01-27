# EDJO-BIM Reports Platform

A production-grade, event-driven reporting platform built with Spring Boot, Kafka, and Kubernetes.

## Architecture Overview

This platform implements a microservices architecture with:
- **API Gateway/BFF**: Aggregates downstream services and publishes report requests to Kafka
- **Auth Service**: Issues JWT tokens with RBAC
- **Reports Service**: Consumes Kafka events, generates reports using Strategy pattern, persists to ArchiveDB
- **Downstream Services**: Client Performance and AIMS Config Central services

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- kubectl & Helm (for Kubernetes deployment)

### Local Development with Docker Compose

1. **Start all services**:
   ```bash
   docker-compose up -d
   ```

2. **Wait for services to be healthy** (check logs):
   ```bash
   docker-compose logs -f
   ```

3. **Get an access token**:
   ```bash
   curl -X POST http://localhost:8081/oauth2/token \
     -H "Content-Type: application/json" \
     -d '{"username": "user", "password": "password", "roles": ["ROLE_USER"]}'
   ```

4. **Request a report**:
   ```bash
   export TOKEN="<token-from-step-3>"
   curl -X POST http://localhost:8080/api/v1/reports/request \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "reportType": "performance",
       "clientId": "client-123",
       "startDate": "2024-01-01",
       "endDate": "2024-12-31"
     }'
   ```

5. **Check report status**:
   ```bash
   curl -X GET http://localhost:8080/api/v1/reports/{reportRequestId}/status \
     -H "Authorization: Bearer $TOKEN"
   ```

6. **Retrieve completed report**:
   ```bash
   curl -X GET http://localhost:8080/api/v1/reports/{reportRequestId} \
     -H "Authorization: Bearer $TOKEN"
   ```

## Documentation

Comprehensive documentation is available in the `/docs` directory:

- **[00-ARCHITECTURE.md](docs/00-ARCHITECTURE.md)**: System architecture and design decisions
- **[01-RUN-LOCAL-DOCKER.md](docs/01-RUN-LOCAL-DOCKER.md)**: Step-by-step Docker setup and troubleshooting
- **[02-KAFKA-DEEP-DIVE.md](docs/02-KAFKA-DEEP-DIVE.md)**: Kafka configuration, partitioning, and event handling
- **[03-REPORTS-DESIGN-PATTERNS.md](docs/03-REPORTS-DESIGN-PATTERNS.md)**: Design patterns used (Strategy, Factory, etc.)
- **[04-SECURITY-OAUTH-JWT.md](docs/04-SECURITY-OAUTH-JWT.md)**: OAuth2/JWT implementation details
- **[05-K8S-STEP-BY-STEP.md](docs/05-K8S-STEP-BY-STEP.md)**: Kubernetes deployment guide
- **[06-HELM-DEPLOYMENT.md](docs/06-HELM-DEPLOYMENT.md)**: Helm chart deployment
- **[07-CICD-PIPELINE.md](docs/07-CICD-PIPELINE.md)**: CI/CD pipeline configuration
- **[08-FAILURE-HANDLING.md](docs/08-FAILURE-HANDLING.md)**: Error handling, retries, and DLQ
- **[09-INTERVIEW-CROSS-QUESTIONS.md](docs/09-INTERVIEW-CROSS-QUESTIONS.md)**: Interview Q&A on system design
- **[ASSUMPTIONS.md](docs/ASSUMPTIONS.md)**: Design decisions and assumptions

## Project Structure

```
/
├── services/
│   ├── api-gateway-bff/          # API Gateway / BFF service
│   ├── auth-service/              # OAuth2/JWT authentication service
│   ├── reports-ms/                # Report generation microservice
│   ├── client-performance-svc/    # Client performance downstream service
│   └── aims-config-central/       # AIMS config downstream service
├── docs/                          # Documentation
├── helm/                          # Helm charts
├── docker-compose.yml             # Local development setup
├── Jenkinsfile                    # CI/CD pipeline
└── README.md                      # This file
```

## Key Features

- ✅ Event-driven architecture with Kafka
- ✅ OAuth2/JWT authentication with RBAC
- ✅ Idempotent Kafka consumers
- ✅ Circuit breaker, retry, and timeout patterns
- ✅ Correlation ID propagation
- ✅ Strategy pattern for report generation
- ✅ Docker Compose for local development
- ✅ Kubernetes + Helm for production deployment
- ✅ Comprehensive documentation

## Technology Stack

- **Runtime**: Java 17, Spring Boot 3.x
- **Build**: Maven
- **Messaging**: Apache Kafka
- **Database**: PostgreSQL (ArchiveDB), MongoDB (optional)
- **Security**: Spring Security OAuth2 Resource Server
- **Resilience**: Resilience4j
- **Containerization**: Docker
- **Orchestration**: Kubernetes, Helm

## License

Internal use only.
