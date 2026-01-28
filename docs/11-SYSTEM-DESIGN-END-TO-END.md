# System Design: EDJO-BIM Reports Platform - End-to-End

## Table of Contents
1. [System Overview](#system-overview)
2. [Requirements & Constraints](#requirements--constraints)
3. [High-Level Architecture](#high-level-architecture)
4. [Detailed Component Design](#detailed-component-design)
5. [Data Flow & Processing](#data-flow--processing)
6. [Scalability & Performance](#scalability--performance)
7. [Reliability & Fault Tolerance](#reliability--fault-tolerance)
8. [Security & Compliance](#security--compliance)
9. [Trade-offs & Design Decisions](#trade-offs--design-decisions)
10. [Interview Q&A Format](#interview-qa-format)

---

## System Overview

### What is EDJO-BIM Reports Platform?

**One-Line Answer**: "A production-grade, event-driven microservices platform that generates financial reports asynchronously using Kafka, with a BFF pattern for API aggregation and idempotent processing for exactly-once semantics."

**Extended Answer**: 
The EDJO-BIM Reports Platform is an enterprise-grade system that allows clients to request various types of financial reports (Performance, Benchmark Summary, Asset Allocation, etc.). The system processes these requests asynchronously, aggregates data from multiple downstream services, generates reports using a Strategy pattern, and stores them for retrieval. It's designed for high availability, scalability, and compliance with financial industry standards.

### Key Characteristics

- **Event-Driven Architecture**: Asynchronous processing via Kafka
- **Microservices**: 6 independent services
- **BFF Pattern**: Backend-for-Frontend aggregates downstream calls
- **Idempotent Processing**: Exactly-once semantics via database constraints
- **Resilient**: Circuit breakers, retries, DLQ for failure handling
- **Observable**: Correlation IDs, structured logging, metrics

---

## Requirements & Constraints

### Functional Requirements

1. **Report Request**: Clients can request reports via REST API
2. **Report Types**: Support 5+ report types (Performance, Benchmark, Asset Allocation, etc.)
3. **Report Status**: Clients can check report status and retrieve completed reports
4. **Authentication**: OAuth2/JWT-based authentication
5. **Data Aggregation**: Aggregate data from multiple downstream services
6. **Asynchronous Processing**: Reports generated asynchronously (non-blocking)

### Non-Functional Requirements

1. **Availability**: 99.9% uptime (8.76 hours downtime/year)
2. **Latency**: 
   - API response: < 200ms (request acceptance)
   - Report generation: < 5 seconds (P95)
3. **Throughput**: Handle 10K reports/day initially, scale to 100K+
4. **Consistency**: Eventually consistent (reports may not be immediately available)
5. **Durability**: No data loss (Kafka replication, database backups)
6. **Security**: OAuth2/JWT, RBAC, encrypted data in transit and at rest

### Constraints

1. **Technology Stack**: Spring Boot 3.x, Java 17, Kafka, Postgres
2. **Deployment**: Kubernetes with Helm charts
3. **Compliance**: Financial data retention (7 years), audit trails
4. **Budget**: Cost-effective scaling (use auto-scaling, spot instances)

---

## High-Level Architecture

### Architecture Diagram (Textual)

```
┌─────────────┐
│   Client    │
│  (Browser)  │
└──────┬──────┘
       │ HTTPS
       │ JWT Token
       ▼
┌─────────────────────────────────────┐
│      API Gateway / BFF              │
│  - OAuth2 Resource Server           │
│  - Downstream Aggregation           │
│  - Circuit Breaker                 │
│  - Correlation ID Generation       │
└──────┬──────────────────────────────┘
       │
       │ (Parallel Calls)
       ├──────────────┬──────────────┐
       ▼              ▼              ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│  Client     │ │  AIMS       │ │  Archive    │
│ Performance │ │  Config     │ │  DB Service │
│  Service    │ │  Central    │ │             │
└─────────────┘ └─────────────┘ └─────────────┘
       │
       │ Kafka Event (report.requested)
       ▼
┌─────────────────────────────────────┐
│         Kafka Cluster              │
│  Topics:                            │
│  - report.requested (6 partitions) │
│  - report.completed                 │
│  - report.failed (DLQ)              │
└──────┬──────────────────────────────┘
       │
       │ Consumer Group: reports-ms
       ▼
┌─────────────────────────────────────┐
│      Reports Microservice           │
│  - Kafka Consumer                   │
│  - Strategy Pattern                │
│  - Idempotent Consumer             │
│  - Report Generation                │
└──────┬──────────────────────────────┘
       │
       │ Save Report
       ▼
┌─────────────────────────────────────┐
│      ArchiveDB (Postgres)           │
│  - report_records                   │
│  - processed_requests               │
└─────────────────────────────────────┘
```

### Interview Answer Format

**Q: "Walk me through the high-level architecture."**

**A**: "The system follows an event-driven microservices architecture with 6 services:

1. **API Gateway/BFF**: Entry point that validates JWT tokens, aggregates data from 3 downstream services in parallel, then publishes a Kafka event. Uses circuit breakers for resilience.

2. **Downstream Services**: Three services provide data:
   - Client Performance Service: Performance metrics
   - AIMS Config Central: Configuration data
   - Archive DB Service: Report retrieval

3. **Kafka**: Event bus with 3 topics:
   - `report.requested`: New report requests
   - `report.completed`: Successfully generated reports
   - `report.failed`: Failed reports (DLQ)

4. **Reports Microservice**: Consumes events, generates reports using Strategy pattern, ensures idempotency via database constraints.

5. **ArchiveDB**: Postgres database storing reports and deduplication records.

**Key Design Principles**:
- **Separation of Concerns**: Each service has a single responsibility
- **Async Processing**: Non-blocking report generation
- **Resilience**: Circuit breakers, retries, DLQ
- **Observability**: Correlation IDs throughout"

---

## Detailed Component Design

### 1. API Gateway / BFF

#### Responsibilities
- **Authentication**: Validate JWT tokens (OAuth2 Resource Server)
- **Authorization**: Role-based access control (RBAC)
- **Aggregation**: Parallel calls to downstream services
- **Event Publishing**: Publish Kafka events with correlation IDs
- **Resilience**: Circuit breaker, retry, timeout

#### Key Components

**SecurityConfig**:
```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    // JWT decoder with HMAC secret
    // OAuth2 resource server configuration
    // Method-level security (@PreAuthorize)
}
```

**DownstreamClient**:
```java
@Service
public class DownstreamClient {
    @CircuitBreaker(name = "downstreams")
    @Retry(name = "downstreams")
    @TimeLimiter(name = "downstreams")
    public CompletableFuture<Map<String, Object>> fetchPerformance(...)
    // Parallel aggregation with Resilience4j
}
```

**ReportRequestService**:
```java
@Service
public class ReportRequestService {
    public ReportRequestResponse requestReport(...) {
        // 1. Generate reportRequestId (UUID)
        // 2. Parallel aggregation (CompletableFuture)
        // 3. Create Kafka event
        // 4. Publish with correlation ID
    }
}
```

#### Interview Answer

**Q: "Why did you choose BFF pattern?"**

**A**: "The BFF (Backend-for-Frontend) pattern allows us to:
1. **Reduce Client Complexity**: Client makes one call instead of multiple
2. **Optimize for Client**: Aggregate only what's needed, format for client
3. **Parallel Aggregation**: Call 3 downstream services simultaneously, reducing latency
4. **Centralized Logic**: Authentication, authorization, correlation ID generation in one place
5. **Future Flexibility**: Can add new clients (mobile, web) with different BFFs

**Trade-off**: Adds a network hop, but the latency reduction from parallel calls more than compensates."

---

### 2. Kafka Event Bus

#### Topic Design

**report.requested**:
- **Partitions**: 6 (allows 6 parallel consumers)
- **Replication**: 3 (for durability)
- **Key**: `reportRequestId` (ensures ordering per request)
- **Retention**: 7 days (compliance)

**report.completed**:
- **Partitions**: 6
- **Purpose**: Notify completion (can be consumed by other services)
- **Key**: `reportRequestId`

**report.failed**:
- **Partitions**: 6
- **Purpose**: Dead Letter Queue (DLQ)
- **Retention**: 30 days (for investigation)

#### Producer Configuration

```yaml
spring:
  kafka:
    producer:
      properties:
        retries: 3
        acks: all  # All replicas must acknowledge
        enable.idempotence: true  # Prevent duplicates
        max.in.flight.requests.per.connection: 5
```

**Why `acks=all`?**
- Financial data requires durability
- Ensures message replicated to multiple brokers
- Prevents data loss if leader fails immediately after acknowledgment
- Trade-off: Higher latency (~10-20ms) but acceptable for async processing

#### Interview Answer

**Q: "Why 6 partitions? How did you decide?"**

**A**: "I chose 6 partitions based on:
1. **Current Load**: 2 consumer pods × 3 partitions each = optimal distribution
2. **Future Scaling**: Can scale up to 6 pods (1:1 partition ratio)
3. **Conservative Start**: Partitions can't be decreased, so start conservative
4. **Rule of Thumb**: 2-3x number of consumers for even distribution

**Scaling Strategy**:
- Monitor consumer lag
- If lag > 100 messages/partition, scale consumers
- If consumers = partitions and still lagging, increase partitions
- For 10x load, would increase to 24-30 partitions"

---

### 3. Reports Microservice

#### Consumer Design

**Idempotent Consumer Pattern**:
```java
@KafkaListener(topics = "${app.kafka.topics.report-requested}")
@Transactional
public void handleReportRequested(ReportRequestedEvent event) {
    String requestId = event.getReportRequestId();
    
    // Idempotent check
    if (processedRequestRepository.existsByReportRequestId(requestId)) {
        return;  // Already processed
    }
    
    // Generate report using Strategy pattern
    ReportStrategy strategy = strategyFactory.get(event.getReportType());
    String payload = strategy.generate(event);
    
    // Save report and mark as processed (same transaction)
    reportRecordRepository.save(record);
    processedRequestRepository.save(processed);
}
```

**Database Schema**:
```sql
CREATE TABLE processed_requests (
    id BIGSERIAL PRIMARY KEY,
    report_request_id VARCHAR(255) NOT NULL UNIQUE,  -- Unique constraint
    processed_at TIMESTAMP NOT NULL
);
```

**Why Unique Constraint?**
- Database enforces atomicity
- Even if two consumers process same message simultaneously, only one succeeds
- Prevents duplicate processing without distributed locking

#### Strategy Pattern

**Interface**:
```java
public interface ReportStrategy {
    String reportType();
    String generate(ReportRequestedEvent event);
}
```

**Implementations**:
- `PerformanceReportStrategy`
- `BenchmarkSummaryReportStrategy`
- `AssetAllocationReportStrategy`
- `DiversificationReportStrategy`
- `ByProductTypeReportStrategy`

**Factory**:
```java
@Component
public class ReportStrategyFactory {
    private final Map<String, ReportStrategy> strategies;
    // Auto-wired by Spring, creates map by reportType()
}
```

**Benefits**:
- Open/Closed Principle: Add new report types without modifying existing code
- Single Responsibility: Each strategy handles one report type
- Testability: Easy to unit test each strategy

#### Interview Answer

**Q: "How do you ensure exactly-once processing?"**

**A**: "We use an idempotent consumer pattern:

1. **At-Least-Once Delivery**: Kafka guarantees message delivery but may duplicate
2. **Idempotent Check**: Before processing, check `processed_requests` table
3. **Unique Constraint**: Database enforces uniqueness on `reportRequestId`
4. **Transactional**: Save report and processed record in same transaction

**How it works**:
- First message: Processed, saved to database
- Duplicate message: Check fails, skip processing
- Concurrent processing: Database constraint ensures only one succeeds

**Why not exactly-once semantics?**
- Kafka's exactly-once is complex and has performance overhead
- Idempotent consumer is simpler and sufficient for our use case
- Database constraint provides strong guarantee"

---

### 4. Error Handling & DLQ

#### Error Handler Configuration

```java
@Bean
DefaultErrorHandler errorHandler(...) {
    DeadLetterPublishingRecoverer recoverer = 
        new DeadLetterPublishingRecoverer(kafkaTemplate, ...);
    
    ExponentialBackOffWithMaxRetries backOff = 
        new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(1000);  // 1 second
    backOff.setMultiplier(2.0);        // 2s, 4s, 8s
    
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
    handler.addNotRetryableExceptions(NonRetryableReportException.class);
    return handler;
}
```

#### Retry Strategy

**Retryable Errors**:
- Network timeouts
- Database connection pool exhausted
- Temporary service unavailability (503)

**Non-Retryable Errors**:
- Invalid report type
- Malformed request data
- Authorization failure

**Retry Flow**:
1. Attempt 1: Immediate
2. Attempt 2: Wait 1 second
3. Attempt 3: Wait 2 seconds
4. Attempt 4: Wait 4 seconds
5. After max retries: Send to DLQ (`report.failed`)

#### Interview Answer

**Q: "How do you handle failures?"**

**A**: "Multi-layered failure handling:

1. **Producer Level**:
   - Retries: 3 attempts
   - Idempotent producer: Prevents duplicates on retry
   - `acks=all`: Ensures durability

2. **Consumer Level**:
   - Exponential backoff: 1s, 2s, 4s
   - Max 3 retries
   - DLQ for permanent failures
   - Idempotent processing prevents duplicates

3. **Downstream Services**:
   - Circuit breaker: Opens if failure rate > 50%
   - Retry: 3 attempts with 500ms delay
   - Timeout: 2 seconds per attempt

4. **Database**:
   - Connection pooling
   - Transaction rollback on failure
   - Unique constraints prevent duplicates

**DLQ Strategy**:
- Monitor DLQ message count
- Investigate root cause
- Fix issue and reprocess, or archive for analysis"

---

## Data Flow & Processing

### End-to-End Flow

#### Step 1: Client Request

```
Client → POST /api/v1/reports
Headers:
  Authorization: Bearer <JWT>
  Content-Type: application/json
Body:
  {
    "clientId": "C-1001",
    "reportType": "PERFORMANCE"
  }
```

#### Step 2: API Gateway Processing

1. **Authentication**: Validate JWT token
2. **Authorization**: Check roles (ROLE_USER, ROLE_ADMIN)
3. **Correlation ID**: Generate if missing, add to MDC
4. **Generate Request ID**: UUID for `reportRequestId`

#### Step 3: Downstream Aggregation (Parallel)

```java
CompletableFuture<Map<String, Object>> perfFuture = 
    downstreamClient.fetchPerformance(clientId, authHeader);
CompletableFuture<Map<String, Object>> configFuture = 
    downstreamClient.fetchConfig(clientId, authHeader);

Map<String, Object> perfData = perfFuture.join();
Map<String, Object> configData = configFuture.join();
```

**Why Parallel?**
- Sequential: 200ms + 200ms = 400ms
- Parallel: max(200ms, 200ms) = 200ms
- **50% latency reduction**

#### Step 4: Kafka Event Publishing

```java
ReportRequestedEvent event = new ReportRequestedEvent();
event.setReportRequestId(reportRequestId);
event.setReportType("PERFORMANCE");
event.setPerformanceData(perfData);
event.setConfigData(configData);
event.setCorrelationId(correlationId);

ProducerRecord<String, ReportRequestedEvent> record = 
    new ProducerRecord<>("report.requested", reportRequestId, event);
record.headers().add("X-Correlation-Id", correlationId.getBytes());
kafkaTemplate.send(record);
```

**Response**: `{"reportRequestId": "uuid", "status": "REQUESTED"}`

#### Step 5: Consumer Processing

1. **Consume Message**: Kafka consumer receives event
2. **Idempotent Check**: Query `processed_requests` table
3. **Strategy Selection**: Factory selects strategy by report type
4. **Report Generation**: Strategy generates report payload
5. **Database Save**: Save report and processed record (transactional)
6. **Completion Event**: Publish `report.completed` event

#### Step 6: Report Retrieval

```
Client → GET /api/v1/reports/{reportRequestId}
Response:
{
  "reportRequestId": "uuid",
  "status": "COMPLETED",
  "reportType": "PERFORMANCE",
  "payload": "type=PERFORMANCE\nclientId=C-1001\n..."
}
```

### Interview Answer

**Q: "Walk me through what happens when a client requests a report."**

**A**: "Here's the end-to-end flow:

1. **Client Request**: POST to `/api/v1/reports` with JWT token
2. **Authentication**: API Gateway validates JWT, extracts user/roles
3. **Aggregation**: Parallel calls to 3 downstream services (200ms total vs 600ms sequential)
4. **Event Publishing**: Create Kafka event with aggregated data, publish to `report.requested`
5. **Response**: Return `reportRequestId` and `REQUESTED` status immediately
6. **Async Processing**: Consumer picks up event, checks idempotency, generates report
7. **Storage**: Save report to ArchiveDB, mark as processed
8. **Completion**: Publish `report.completed` event
9. **Retrieval**: Client polls GET endpoint, retrieves completed report

**Key Points**:
- **Non-blocking**: Client doesn't wait for report generation
- **Idempotent**: Duplicate messages don't cause duplicate reports
- **Observable**: Correlation ID tracks request through entire flow"

---

## Scalability & Performance

### Current Capacity

- **Throughput**: ~10K reports/day (~0.12 reports/second average)
- **Peak Load**: ~1 report/second
- **Report Generation Time**: 2-5 seconds average
- **API Latency**: < 200ms (P95)

### Scaling Strategy

#### Horizontal Scaling

**API Gateway/BFF**:
- **Current**: 2 replicas
- **Scale to**: 10-15 replicas for 10x load
- **Stateless**: Easy horizontal scaling
- **Auto-scaling**: Based on CPU/memory or request rate

**Reports Microservice**:
- **Current**: 2 replicas (3 partitions each)
- **Scale to**: 6 replicas (1 partition each) for optimal parallelism
- **Constraint**: Limited by partition count (6)
- **Solution**: Increase partitions to 24-30 for 10x load

**Kafka**:
- **Partitions**: Increase from 6 to 24-30
- **Consumers**: Scale to match partition count
- **Brokers**: Add brokers if needed (current: single broker in docker-compose)

#### Vertical Scaling

**Database**:
- **Current**: Single Postgres instance
- **Scale to**: 
  - Read replicas (3-5) for read-heavy workload
  - Connection pooling optimization
  - Query optimization (indexes, partitioning)

#### Performance Optimization

1. **Caching**:
   - Redis for downstream service responses
   - Cache frequently accessed reports
   - TTL: 5-15 minutes

2. **Database**:
   - Indexes on `reportRequestId` (already unique)
   - Partitioning by date for `report_records`
   - Connection pool tuning

3. **Kafka**:
   - Batch processing for consumers
   - Compression (gzip/snappy)
   - Tune consumer fetch size

### Interview Answer

**Q: "How would you scale this to handle 10x the load?"**

**A**: "For 10x load (100K reports/day):

1. **API Gateway**: Scale from 2 to 10-15 replicas (stateless, easy scaling)

2. **Kafka**: 
   - Increase partitions from 6 to 24-30
   - Scale consumers to match (24-30 replicas)
   - Add brokers if needed

3. **Database**:
   - Add read replicas (3-5) for report retrieval
   - Optimize queries (indexes, partitioning)
   - Connection pool tuning

4. **Caching**:
   - Redis for downstream responses (reduce load)
   - Cache frequently accessed reports

5. **Auto-scaling**:
   - Kubernetes HPA based on consumer lag
   - Scale down during off-peak hours

**Cost Optimization**:
- Use spot instances for non-critical workloads
- Right-size pods (CPU/memory)
- Monitor and optimize resource allocation

**Bottleneck Analysis**:
- Current bottleneck: Consumer processing (2-5 seconds per report)
- Solution: Scale consumers, optimize report generation logic"

---

## Reliability & Fault Tolerance

### Availability Design

#### Multi-Layer Redundancy

1. **Service Level**:
   - Multiple replicas per service (2+)
   - Kubernetes auto-restart on failure
   - Health checks (liveness/readiness probes)

2. **Kafka Level**:
   - Replication factor: 3 (message replicated to 3 brokers)
   - Leader election: Automatic failover
   - Partition distribution: Across multiple brokers

3. **Database Level**:
   - Daily full backups
   - Hourly incremental backups
   - Point-in-time recovery capability

#### Failure Scenarios & Handling

**Scenario 1: Consumer Pod Crashes**
- **Detection**: Kubernetes liveness probe fails
- **Action**: Kubernetes restarts pod
- **Impact**: Minimal (other pods continue processing)
- **Recovery**: Pod restarts, rejoins consumer group, resumes from last committed offset

**Scenario 2: Downstream Service Down**
- **Detection**: Circuit breaker opens after 50% failure rate
- **Action**: Fail fast, don't call downstream
- **Impact**: Report requests fail gracefully
- **Recovery**: Circuit breaker half-open after 5 seconds, test request

**Scenario 3: Database Connection Pool Exhausted**
- **Detection**: Connection timeout errors
- **Action**: Retry with exponential backoff
- **Impact**: Temporary slowdown, messages go to DLQ if persistent
- **Recovery**: Scale database, increase connection pool size

**Scenario 4: Kafka Broker Failure**
- **Detection**: Producer/consumer errors
- **Action**: Kafka handles automatically (leader election)
- **Impact**: Brief unavailability during failover
- **Recovery**: Automatic (Kafka replication)

### Interview Answer

**Q: "How do you ensure 99.9% availability?"**

**A**: "Multi-layered approach:

1. **Redundancy**:
   - Multiple replicas per service (2+)
   - Kafka replication factor 3
   - Database backups (daily + hourly)

2. **Health Checks**:
   - Liveness probes: Restart unhealthy pods
   - Readiness probes: Remove from load balancer
   - Circuit breakers: Prevent cascading failures

3. **Failure Handling**:
   - Retries with exponential backoff
   - DLQ for permanent failures
   - Idempotent processing prevents duplicates

4. **Monitoring**:
   - Alert on critical metrics (error rate, latency)
   - On-call rotation
   - Incident response playbook

**SLA Calculation**:
- 99.9% uptime = 8.76 hours downtime/year
- Target: < 1 hour downtime/year (better than SLA)
- Achieved through: Redundancy, health checks, fast recovery"

---

## Security & Compliance

### Security Architecture

#### Authentication & Authorization

**OAuth2/JWT Flow**:
1. Client requests token from auth-service
2. Auth-service issues JWT with roles
3. API Gateway validates JWT (HMAC signature)
4. Extract roles from JWT claims
5. Method-level authorization (`@PreAuthorize`)

**JWT Structure**:
```json
{
  "iss": "http://auth-service:8081",
  "sub": "alice",
  "roles": ["ROLE_USER"],
  "iat": 1234567890,
  "exp": 1234571490
}
```

**HMAC Secret**: Shared secret across services (in production, use key rotation)

#### Data Protection

1. **Encryption in Transit**: HTTPS/TLS for all communication
2. **Encryption at Rest**: Database encryption (Postgres)
3. **Secrets Management**: Kubernetes secrets (not hardcoded)
4. **Network Policies**: Restrict inter-service communication

#### Compliance

**Data Retention**:
- Reports stored for 7 years (financial compliance)
- Automated archival to cold storage after 1 year
- Deletion process documented and auditable

**Audit Trails**:
- All requests logged with: user ID, timestamp, report type, correlation ID
- Immutable audit log (WORM storage)
- Centralized logging (ELK stack)

**Access Control**:
- Role-based access control (RBAC)
- Principle of least privilege
- Regular access reviews

### Interview Answer

**Q: "How do you ensure security and compliance?"**

**A**: "Multi-layered security:

1. **Authentication**: OAuth2/JWT with HMAC signature validation
2. **Authorization**: Role-based access control (RBAC) at method level
3. **Data Protection**: 
   - TLS for all communication
   - Database encryption at rest
   - Secrets in Kubernetes (not code)

4. **Compliance**:
   - 7-year data retention (financial regulations)
   - Audit trails (all requests logged with correlation IDs)
   - Immutable audit logs

5. **Network Security**:
   - Network policies restrict communication
   - Private networks for inter-service communication

**In Production**:
- Key rotation for JWT secrets
- Regular security scans
- Penetration testing
- Compliance audits"

---

## Trade-offs & Design Decisions

### Key Trade-offs

#### 1. Eventual Consistency vs Strong Consistency

**Chose**: Eventual consistency
**Why**: 
- Better scalability (async processing)
- Non-blocking API calls
- Acceptable for report generation (not transactional)

**Mitigation**: 
- Show "processing" status immediately
- Typically completes in 2-5 seconds
- Client polls for completion

#### 2. At-Least-Once vs Exactly-Once Semantics

**Chose**: At-least-once with idempotent consumer
**Why**:
- Simpler than Kafka's exactly-once semantics
- Database unique constraint provides strong guarantee
- Better performance

**Mitigation**:
- Idempotent consumer pattern
- Database unique constraint
- No duplicate processing

#### 3. Microservices vs Monolith

**Chose**: Microservices (6 services)
**Why**:
- Independent scaling
- Technology flexibility
- Team ownership
- Fault isolation

**Trade-off**:
- More complex deployment
- Network latency
- Distributed tracing needed

#### 4. Kafka Partitions: 6 vs More

**Chose**: 6 partitions (conservative)
**Why**:
- Can't decrease partitions
- Start conservative, scale up
- Current load doesn't need more

**Future**: Scale to 24-30 for 10x load

#### 5. acks=all vs acks=1

**Chose**: acks=all
**Why**:
- Financial data requires durability
- Prevents data loss if leader fails
- Acceptable latency for async processing

**Trade-off**: Higher latency (~10-20ms) but acceptable

### Interview Answer

**Q: "What trade-offs did you make, and why?"**

**A**: "Key trade-offs:

1. **Eventual Consistency**: Chose async processing for scalability, acceptable delay (2-5 seconds)

2. **At-Least-Once**: Simpler than exactly-once, idempotent consumer provides guarantee

3. **Microservices**: More complex but better scalability and fault isolation

4. **Conservative Partitions**: Start with 6, scale up as needed (can't decrease)

5. **acks=all**: Higher latency but ensures durability for financial data

**Decision Framework**:
- Evaluate against requirements (scalability, durability, latency)
- Consider team expertise and operational complexity
- Document decisions (ADR format)
- Review periodically (every 6 months)"

---

## Interview Q&A Format

### Common System Design Questions

#### Q1: "Design a system to generate financial reports"

**Answer Structure**:
1. **Clarify Requirements**: 
   - Report types, volume, latency requirements
   - Authentication, compliance needs

2. **High-Level Design**:
   - API Gateway → Kafka → Consumer → Database
   - Show data flow diagram

3. **Detailed Design**:
   - Components, APIs, data models
   - Scalability, reliability, security

4. **Trade-offs**:
   - Consistency, availability, latency
   - Technology choices

5. **Scale**:
   - How to handle 10x, 100x load
   - Bottlenecks and solutions

#### Q2: "How do you ensure no duplicate reports?"

**Answer**:
- Idempotent consumer pattern
- Database unique constraint on `reportRequestId`
- Explain how it handles retries, concurrent processing
- Show code example

#### Q3: "What if Kafka is down?"

**Answer**:
- Producer retries (3 attempts)
- Circuit breaker opens after failures
- Messages buffered in producer
- Fallback: Queue requests, process when Kafka recovers
- Monitoring: Alert on producer errors

#### Q4: "How do you handle database failures?"

**Answer**:
- Connection pooling with retries
- Read replicas for availability
- Transaction rollback on failure
- DLQ for messages that can't be processed
- Monitoring: Alert on connection pool exhaustion

#### Q5: "What's your approach to monitoring?"

**Answer**:
- **Metrics**: Request rate, error rate, latency, consumer lag
- **Logging**: Structured logs with correlation IDs
- **Alerting**: Critical alerts (error rate > 5%, consumer lag > 1000)
- **Dashboards**: Business metrics, system metrics, infrastructure metrics

---

## Summary: Key Points for Interview

### Architecture Highlights

1. **Event-Driven**: Async processing via Kafka
2. **Microservices**: 6 independent services
3. **BFF Pattern**: Aggregates downstream calls
4. **Idempotent Consumer**: Exactly-once processing
5. **Resilient**: Circuit breakers, retries, DLQ

### Scalability

- **Horizontal Scaling**: Stateless services, easy to scale
- **Partition Strategy**: Start conservative, scale up
- **Database**: Read replicas, connection pooling
- **Caching**: Redis for downstream responses

### Reliability

- **Redundancy**: Multiple replicas, Kafka replication
- **Health Checks**: Liveness/readiness probes
- **Failure Handling**: Retries, circuit breakers, DLQ
- **Monitoring**: Comprehensive metrics and alerting

### Security & Compliance

- **OAuth2/JWT**: Authentication and authorization
- **Data Protection**: TLS, encryption at rest
- **Compliance**: 7-year retention, audit trails
- **Network Security**: Network policies, private networks

### Design Principles

- **Separation of Concerns**: Single responsibility per service
- **Open/Closed Principle**: Strategy pattern for extensibility
- **Fail Fast**: Circuit breakers prevent cascading failures
- **Observability**: Correlation IDs, structured logging

---

**This document provides comprehensive, interview-ready explanations of the EDJO-BIM Reports Platform system design.**
