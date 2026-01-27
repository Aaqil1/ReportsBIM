# Reports Design Patterns

This document explicitly maps all design patterns used in the platform, with code references and explanations.

## Strategy Pattern

### Purpose

Encapsulate different report generation algorithms and make them interchangeable at runtime.

### Implementation

**Interface**: `services/reports-ms/src/main/java/com/edjobim/reports/strategy/ReportGenerationStrategy.java`

```java
public interface ReportGenerationStrategy {
    String generateReport(ReportRequestEvent request);
    String getReportType();
}
```

**Concrete Strategies**:
- `PerformanceReportStrategy` - Generates performance reports
- `BenchmarkReportStrategy` - Generates benchmark comparison reports
- `ByProductTypeReportStrategy` - Generates product-type grouped reports
- `DiversificationReportStrategy` - Generates diversification metrics
- `AssetAllocationReportStrategy` - Generates asset allocation breakdowns

**Example**: `services/reports-ms/src/main/java/com/edjobim/reports/strategy/PerformanceReportStrategy.java`

```java
public class PerformanceReportStrategy implements ReportGenerationStrategy {
    @Override
    public String generateReport(ReportRequestEvent request) {
        // Generate performance-specific report
        ObjectNode report = objectMapper.createObjectNode();
        report.put("reportType", "performance");
        // ... build report JSON
        return report.toString();
    }

    @Override
    public String getReportType() {
        return "performance";
    }
}
```

### Usage

**Location**: `services/reports-ms/src/main/java/com/edjobim/reports/service/ReportService.java`

```java
// Select strategy based on reportType
ReportGenerationStrategy strategy = strategyFactory.getStrategy(event.getReportType());
String reportData = strategy.generateReport(event);
```

### Benefits

- **Open/Closed Principle**: Add new report types without modifying existing code
- **Single Responsibility**: Each strategy handles one report type
- **Testability**: Test each strategy independently

## Factory Pattern

### Purpose

Create appropriate strategy instances without exposing instantiation logic.

### Implementation

**Location**: `services/reports-ms/src/main/java/com/edjobim/reports/factory/ReportStrategyFactory.java`

```java
@Component
public class ReportStrategyFactory {
    private final Map<String, ReportGenerationStrategy> strategies;

    public ReportStrategyFactory() {
        this.strategies = new HashMap<>();
        strategies.put("performance", new PerformanceReportStrategy());
        strategies.put("benchmark", new BenchmarkReportStrategy());
        strategies.put("by-product-type", new ByProductTypeReportStrategy());
        strategies.put("diversification", new DiversificationReportStrategy());
        strategies.put("asset-allocation", new AssetAllocationReportStrategy());
    }

    public ReportGenerationStrategy getStrategy(String reportType) {
        ReportGenerationStrategy strategy = strategies.get(reportType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown report type: " + reportType);
        }
        return strategy;
    }
}
```

### Usage

```java
@Autowired
private ReportStrategyFactory strategyFactory;

// Get strategy
ReportGenerationStrategy strategy = strategyFactory.getStrategy("performance");
```

### Benefits

- **Encapsulation**: Hides strategy creation logic
- **Centralized**: All strategies registered in one place
- **Validation**: Can validate report type before creation

## Idempotent Consumer Pattern

### Purpose

Handle duplicate Kafka messages gracefully by ensuring idempotent processing.

### Implementation

**Problem**: Kafka provides at-least-once delivery. If consumer crashes after processing but before committing offset, message will be reprocessed.

**Solution**: Track processed `reportRequestId` in database.

**Entity**: `services/reports-ms/src/main/java/com/edjobim/reports/entity/ProcessedRequest.java`

```java
@Entity
@Table(name = "processed_requests")
public class ProcessedRequest {
    @Column(name = "report_request_id", unique = true, nullable = false)
    private String reportRequestId;
    
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
```

**Usage**: `services/reports-ms/src/main/java/com/edjobim/reports/service/ReportService.java`

```java
@Transactional
public void processReportRequest(ReportRequestEvent event, String correlationId) {
    String reportRequestId = event.getReportRequestId();

    // Idempotent check
    if (processedRequestRepository.findByReportRequestId(reportRequestId).isPresent()) {
        logger.info("Report request {} already processed, skipping", reportRequestId);
        return;  // Skip duplicate
    }

    // Process report...
    
    // Mark as processed
    processedRequestRepository.save(new ProcessedRequest(reportRequestId));
}
```

### Benefits

- **Reliability**: Handles duplicate messages safely
- **Consistency**: Prevents duplicate report generation
- **Performance**: Fast lookup (indexed `report_request_id`)

## Circuit Breaker Pattern

### Purpose

Prevent cascading failures by failing fast when downstream services are unavailable.

### Implementation

**Library**: Resilience4j

**Location**: `services/api-gateway-bff/src/main/java/com/edjobim/gateway/service/EnrichmentService.java`

```java
@CircuitBreaker(name = "enrichment", fallbackMethod = "fallbackEnrichment")
@Retry(name = "enrichment")
@TimeLimiter(name = "enrichment")
public CompletableFuture<EnrichmentData> enrich(String clientId, String startDate, String endDate) {
    // Call downstream services
}

public CompletableFuture<EnrichmentData> fallbackEnrichment(
        String clientId, String startDate, String endDate, Exception ex) {
    // Return empty data on failure
    return CompletableFuture.completedFuture(new EnrichmentData());
}
```

**Configuration**: `services/api-gateway-bff/src/main/resources/application.yml`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      enrichment:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50  # Open circuit at 50% failures
        waitDurationInOpenState: 60s
```

### States

1. **CLOSED**: Normal operation, requests pass through
2. **OPEN**: Circuit open, requests fail fast (no calls made)
3. **HALF_OPEN**: Testing if service recovered (limited requests allowed)

### Benefits

- **Resilience**: Prevents overwhelming failing services
- **Fast Failure**: Fails fast instead of timing out
- **Recovery**: Automatically tests recovery

## Retry Pattern

### Purpose

Handle transient failures by automatically retrying operations.

### Implementation

**Location**: Same as Circuit Breaker (`EnrichmentService`)

```java
@Retry(name = "enrichment")
public CompletableFuture<EnrichmentData> enrich(...) {
    // Automatically retried on failure
}
```

**Configuration**:

```yaml
resilience4j:
  retry:
    instances:
      enrichment:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
```

### Retry Behavior

- **Attempt 1**: Immediate
- **Attempt 2**: Wait 500ms
- **Attempt 3**: Wait 1000ms (exponential backoff)
- **After 3 failures**: Circuit breaker opens

### Benefits

- **Transient Failure Handling**: Automatically recovers from temporary issues
- **Exponential Backoff**: Reduces load on failing service

## Timeout Pattern

### Purpose

Prevent hanging requests by enforcing maximum wait time.

### Implementation

**Location**: Same as Circuit Breaker (`EnrichmentService`)

```java
@TimeLimiter(name = "enrichment")
public CompletableFuture<EnrichmentData> enrich(...) {
    // Times out after configured duration
}
```

**Configuration**:

```yaml
resilience4j:
  timelimiter:
    instances:
      enrichment:
        timeoutDuration: 5s
```

### Benefits

- **Prevents Hanging**: Fails fast instead of waiting indefinitely
- **Resource Management**: Frees threads quickly

## BFF (Backend for Frontend) Pattern

### Purpose

Aggregate data from multiple downstream services into a single response.

### Implementation

**Location**: `services/api-gateway-bff/src/main/java/com/edjobim/gateway/service/EnrichmentService.java`

```java
public CompletableFuture<EnrichmentData> enrich(...) {
    // Call services in parallel
    Mono<Map<String, Object>> performanceMono = webClient.get()
        .uri(performanceServiceUrl + "/api/v1/performance/client/{clientId}", clientId)
        .retrieve()
        .bodyToMono(Map.class);

    Mono<Map<String, Object>> aimsConfigMono = webClient.get()
        .uri(aimsConfigServiceUrl + "/api/v1/config/aims/{clientId}", clientId)
        .retrieve()
        .bodyToMono(Map.class);

    // Combine results
    return Mono.zip(performanceMono, aimsConfigMono)
        .map(tuple -> {
            EnrichmentData data = new EnrichmentData();
            data.setPerformanceData(tuple.getT1());
            data.setAimsConfig(tuple.getT2());
            return data;
        })
        .toFuture();
}
```

### Benefits

- **Reduced Round Trips**: Client makes one call instead of multiple
- **Parallel Processing**: Calls downstream services concurrently
- **Data Aggregation**: Combines data before sending to client

## Template Method Pattern (Conceptual)

### Potential Use Case

If report generation had common steps:

```java
public abstract class AbstractReportStrategy implements ReportGenerationStrategy {
    @Override
    public final String generateReport(ReportRequestEvent request) {
        validateRequest(request);
        Map<String, Object> data = fetchData(request);
        String report = buildReport(data);
        enrichReport(report, request);
        return report;
    }

    protected abstract Map<String, Object> fetchData(ReportRequestEvent request);
    protected abstract String buildReport(Map<String, Object> data);
    
    // Common methods
    protected void validateRequest(ReportRequestEvent request) { ... }
    protected void enrichReport(String report, ReportRequestEvent request) { ... }
}
```

**Not Currently Implemented**: Each strategy is independent. Could be refactored if common steps emerge.

## Builder Pattern (Conceptual)

### Potential Use Case

For complex report construction:

```java
Report report = Report.builder()
    .withClientId(clientId)
    .withDateRange(startDate, endDate)
    .withMetrics(metrics)
    .withEnrichmentData(enrichmentData)
    .build();
```

**Not Currently Implemented**: Reports are simple JSON objects. Builder pattern would add unnecessary complexity.

## Outbox Pattern (Conceptual)

### Purpose

Ensure database writes and Kafka publishes happen atomically.

### Current Implementation

**Problem**: If database write succeeds but Kafka publish fails, data inconsistency occurs.

**Current Approach**: Separate transactions (not atomic)

```java
// Transaction 1: Save report
reportRepository.save(report);

// Transaction 2: Publish event (may fail)
kafkaTemplate.send("report.completed", event);
```

### Outbox Pattern Solution

1. **Save to Outbox Table** (same transaction as report):
```sql
INSERT INTO reports (...) VALUES (...);
INSERT INTO outbox (event_type, payload) VALUES ('report.completed', '...');
COMMIT;
```

2. **Separate Process Polls Outbox**:
```java
@Scheduled(fixedDelay = 1000)
public void processOutbox() {
    List<OutboxEvent> events = outboxRepository.findUnprocessed();
    for (OutboxEvent event : events) {
        kafkaTemplate.send(event.getTopic(), event.getPayload());
        event.markAsProcessed();
        outboxRepository.save(event);
    }
}
```

**Not Currently Implemented**: See [08-FAILURE-HANDLING.md](08-FAILURE-HANDLING.md) for details.

## Summary

| Pattern | Location | Purpose | Status |
|---------|----------|---------|--------|
| Strategy | `reports-ms/strategy/` | Report generation algorithms | ✅ Implemented |
| Factory | `reports-ms/factory/` | Strategy creation | ✅ Implemented |
| Idempotent Consumer | `reports-ms/service/` | Handle duplicate messages | ✅ Implemented |
| Circuit Breaker | `api-gateway-bff/service/` | Prevent cascading failures | ✅ Implemented |
| Retry | `api-gateway-bff/service/` | Handle transient failures | ✅ Implemented |
| Timeout | `api-gateway-bff/service/` | Prevent hanging requests | ✅ Implemented |
| BFF | `api-gateway-bff/service/` | Aggregate downstream calls | ✅ Implemented |
| Template Method | N/A | Common report steps | ⚠️ Conceptual |
| Builder | N/A | Complex object construction | ⚠️ Conceptual |
| Outbox | N/A | Atomic DB + Kafka writes | ⚠️ Conceptual |

## Next Steps

- See [08-FAILURE-HANDLING.md](08-FAILURE-HANDLING.md) for Outbox pattern implementation
- See [09-INTERVIEW-CROSS-QUESTIONS.md](09-INTERVIEW-CROSS-QUESTIONS.md) for pattern-related interview questions
