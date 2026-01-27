# Failure Handling

This document describes error handling, retry strategies, and dead letter queue implementation.

## Error Categories

### Retryable Errors

**Transient failures that may succeed on retry**:

- Network timeouts
- Database connection failures
- Kafka broker unavailability
- Temporary service unavailability (503, 504)

**Handling**: Automatic retry with exponential backoff

### Non-Retryable Errors

**Permanent failures that won't succeed on retry**:

- Invalid report type (400 Bad Request)
- Authorization failures (401, 403)
- Malformed request data (400)
- Business logic errors

**Handling**: Immediate failure, send to DLQ

## Retry Strategy

### Current Implementation

**Location**: `services/api-gateway-bff/src/main/java/com/edjobim/gateway/service/EnrichmentService.java`

```java
@Retry(name = "enrichment")
public CompletableFuture<EnrichmentData> enrich(...) {
    // Automatically retried on failure
}
```

**Configuration**: `services/api-gateway-bff/src/main/resources/application.yml`

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

| Attempt | Wait Time | Total Elapsed |
|---------|-----------|---------------|
| 1       | 0ms       | 0ms           |
| 2       | 500ms     | 500ms         |
| 3       | 1000ms    | 1500ms        |
| After 3 | Circuit breaker opens | - |

### Retryable Exception Types

**Default**: All exceptions are retried

**Customization** (conceptual):
```java
@Retry(name = "enrichment", 
       retryExceptions = {TimeoutException.class, ConnectException.class},
       ignoreExceptions = {IllegalArgumentException.class})
```

## Circuit Breaker

### Configuration

**Location**: `services/api-gateway-bff/src/main/resources/application.yml`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      enrichment:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50  # Open at 50% failures
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 3
```

### States

1. **CLOSED**: Normal operation
   - Requests pass through
   - Failures counted

2. **OPEN**: Circuit open
   - Requests fail fast (no calls made)
   - Fallback method called
   - After `waitDurationInOpenState`, transitions to HALF_OPEN

3. **HALF_OPEN**: Testing recovery
   - Limited requests allowed (`permittedNumberOfCallsInHalfOpenState`)
   - If successful → CLOSED
   - If failed → OPEN

### Fallback

**Location**: `EnrichmentService.java`

```java
public CompletableFuture<EnrichmentData> fallbackEnrichment(
        String clientId, String startDate, String endDate, Exception ex) {
    logger.warn("Enrichment fallback triggered", ex);
    EnrichmentData data = new EnrichmentData();
    data.setPerformanceData(new HashMap<>());
    data.setAimsConfig(new HashMap<>());
    return CompletableFuture.completedFuture(data);
}
```

**Behavior**: Returns empty data, allows report generation to proceed without enrichment

## Kafka Consumer Retry

### Current Implementation

**Location**: `services/reports-ms/src/main/java/com/edjobim/reports/consumer/ReportRequestConsumer.java`

```java
@KafkaListener(topics = "report.requested", groupId = "reports-ms-group")
public void consume(ConsumerRecord<String, String> record,
                   Acknowledgment acknowledgment) {
    try {
        reportService.processReportRequest(event, correlationId);
        acknowledgment.acknowledge();
    } catch (Exception e) {
        logger.error("Error processing report request", e);
        // In production: implement retry with exponential backoff
        acknowledgment.acknowledge();  // Prevent infinite retries
    }
}
```

### Improved Retry Implementation (Conceptual)

```java
@KafkaListener(topics = "report.requested", groupId = "reports-ms-group")
public void consume(ConsumerRecord<String, String> record,
                   Acknowledgment acknowledgment) {
    String reportRequestId = extractReportRequestId(record);
    int retryCount = getRetryCount(record);
    
    try {
        reportService.processReportRequest(event, correlationId);
        acknowledgment.acknowledge();
    } catch (RetryableException e) {
        if (retryCount < MAX_RETRIES) {
            // Republish with incremented retry count
            republishWithRetry(record, retryCount + 1);
            acknowledgment.acknowledge();
        } else {
            // Send to DLQ
            sendToDLQ(record, e);
            acknowledgment.acknowledge();
        }
    } catch (NonRetryableException e) {
        // Send directly to DLQ
        sendToDLQ(record, e);
        acknowledgment.acknowledge();
    }
}
```

## Dead Letter Queue (DLQ)

### Purpose

Handle messages that cannot be processed after maximum retries.

### Implementation (Conceptual)

**DLQ Topic**: `report.dlq`

**Message Structure**:
```json
{
  "originalTopic": "report.requested",
  "originalPartition": 0,
  "originalOffset": 12345,
  "originalKey": "report-request-id",
  "originalValue": {...},
  "failureReason": "Database connection timeout",
  "retryCount": 3,
  "failedAt": "2024-01-27T10:00:00Z",
  "correlationId": "correlation-id"
}
```

### DLQ Consumer

**Purpose**: Monitor DLQ, alert operations, manual intervention

```java
@KafkaListener(topics = "report.dlq", groupId = "dlq-monitor-group")
public void consumeDLQ(ConsumerRecord<String, String> record) {
    DLQMessage dlqMessage = parseDLQMessage(record);
    
    // Log for monitoring
    logger.error("DLQ message received: {}", dlqMessage);
    
    // Send alert
    alertService.sendAlert("Report processing failed", dlqMessage);
    
    // Store for manual review
    dlqRepository.save(dlqMessage);
}
```

### DLQ Monitoring

```bash
# Check DLQ message count
docker-compose exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic report.dlq

# Consume DLQ messages
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic report.dlq \
  --from-beginning
```

## Idempotent Consumer

### Implementation

**Location**: `services/reports-ms/src/main/java/com/edjobim/reports/service/ReportService.java`

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

- **Handles Duplicates**: Same message processed multiple times → single result
- **Crash Recovery**: If consumer crashes after processing but before commit, reprocessing is safe
- **Kafka Retries**: Safe to retry from Kafka's perspective

## Outbox Pattern (Conceptual)

### Problem

Current implementation has separate transactions:
1. Save report to database
2. Publish Kafka event

If step 2 fails, database has report but no event published → inconsistency.

### Solution: Outbox Pattern

**Step 1**: Save report and outbox event in same transaction

```java
@Transactional
public void processReportRequest(ReportRequestEvent event) {
    // Save report
    Report report = new Report();
    report.setReportRequestId(event.getReportRequestId());
    report.setStatus(ReportStatus.COMPLETED);
    reportRepository.save(report);
    
    // Save outbox event (same transaction)
    OutboxEvent outboxEvent = new OutboxEvent();
    outboxEvent.setEventType("report.completed");
    outboxEvent.setPayload(serializeEvent(event));
    outboxEvent.setStatus(OutboxStatus.PENDING);
    outboxRepository.save(outboxEvent);
    
    // Both committed atomically
}
```

**Step 2**: Separate process polls outbox and publishes events

```java
@Scheduled(fixedDelay = 1000)
@Transactional
public void processOutbox() {
    List<OutboxEvent> events = outboxRepository.findByStatus(OutboxStatus.PENDING);
    
    for (OutboxEvent event : events) {
        try {
            // Publish to Kafka
            kafkaTemplate.send(event.getTopic(), event.getPayload());
            
            // Mark as processed
            event.setStatus(OutboxStatus.PROCESSED);
            outboxRepository.save(event);
        } catch (Exception e) {
            logger.error("Failed to publish outbox event", e);
            // Will retry on next poll
        }
    }
}
```

### Benefits

- **Atomicity**: Database and Kafka updates in single transaction
- **Reliability**: Events eventually published even if Kafka temporarily unavailable
- **Ordering**: Can maintain event order

### Trade-offs

- **Complexity**: Additional table and polling process
- **Latency**: Slight delay between DB write and event publish
- **Idempotency**: Consumer must still be idempotent

## Error Handling Best Practices

### 1. Classify Errors

- **Retryable**: Network, timeouts, temporary unavailability
- **Non-Retryable**: Validation, authorization, business logic

### 2. Exponential Backoff

- Prevents overwhelming failing services
- Reduces retry load

### 3. Circuit Breaker

- Fails fast when service is down
- Prevents cascading failures

### 4. Idempotent Operations

- Safe to retry
- Handles duplicate messages

### 5. Dead Letter Queue

- Capture permanent failures
- Enable manual intervention
- Alert operations team

### 6. Monitoring

- Track retry rates
- Monitor DLQ size
- Alert on circuit breaker opens

## Monitoring Failures

### Metrics

```yaml
# Resilience4j metrics (exposed via Actuator)
resilience4j.circuitbreaker.calls{name="enrichment",state="OPEN"}
resilience4j.retry.calls{name="enrichment",result="successful_retry"}
resilience4j.timelimiter.calls{name="enrichment",result="timeout"}
```

### Logging

```java
logger.error("Report processing failed: reportRequestId={}, error={}", 
             reportRequestId, e.getMessage(), e);
```

### Alerts

- Circuit breaker opens
- DLQ message count > threshold
- Retry rate > threshold
- Processing failures > threshold

## Next Steps

- See [02-KAFKA-DEEP-DIVE.md](02-KAFKA-DEEP-DIVE.md) for Kafka retry details
- See [09-INTERVIEW-CROSS-QUESTIONS.md](09-INTERVIEW-CROSS-QUESTIONS.md) for failure handling Q&A
