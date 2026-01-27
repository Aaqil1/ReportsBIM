# Kafka Deep Dive

This document provides detailed information about Kafka configuration, partitioning, event handling, and troubleshooting.

## Topics Configuration

### Topic List

| Topic | Partitions | Replication Factor | Purpose |
|-------|-----------|---------------------|---------|
| `report.requested` | 6 | 1 (local) | Report generation requests from BFF |
| `report.completed` | 6 | 1 (local) | Successful report generation events |
| `report.failed` | 6 | 1 (local) | Failed report generation events |
| `report.dlq` | 6 | 1 (local) | Dead Letter Queue for permanent failures |

### Creating Topics Manually

Topics are auto-created by Kafka (`auto.create.topics.enable=true`), but you can create them explicitly:

```bash
docker-compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic report.requested \
  --partitions 6 \
  --replication-factor 1

docker-compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic report.completed \
  --partitions 6 \
  --replication-factor 1

docker-compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic report.failed \
  --partitions 6 \
  --replication-factor 1

docker-compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic report.dlq \
  --partitions 6 \
  --replication-factor 1
```

### Verify Topics

```bash
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

**Expected Output**:
```
report.completed
report.dlq
report.failed
report.requested
```

### Describe Topic

```bash
docker-compose exec kafka kafka-topics --describe \
  --bootstrap-server localhost:9092 \
  --topic report.requested
```

**Expected Output**:
```
Topic: report.requested	PartitionCount: 6	ReplicationFactor: 1	Configs: segment.ms=604800000
	Topic: report.requested	Partition: 0	Leader: 1	Replicas: 1	Isr: 1
	Topic: report.requested	Partition: 1	Leader: 1	Replicas: 1	Isr: 1
	Topic: report.requested	Partition: 2	Leader: 1	Replicas: 1	Isr: 1
	Topic: report.requested	Partition: 3	Leader: 1	ReplicationFactor: 1	Isr: 1
	Topic: report.requested	Partition: 4	Leader: 1	Replicas: 1	Isr: 1
	Topic: report.requested	Partition: 5	Leader: 1	Replicas: 1	Isr: 1
```

## Partitioning Strategy

### Why 6 Partitions?

- **Consumer Parallelism**: Allows 1-6 consumer pods to process messages in parallel
- **Optimal Range**: Too few partitions limit scalability; too many add overhead
- **Current Setup**: 3 `reports-ms` replicas = 2 partitions per pod (good balance)

### Partitioning Key

- **Key**: `reportRequestId` (UUID)
- **Reason**: Ensures all events for the same report request go to the same partition
- **Benefit**: Maintains ordering per request (important for idempotency)

### Partition Assignment

With 3 consumer pods and 6 partitions:
- Pod 1: Partitions 0, 1
- Pod 2: Partitions 2, 3
- Pod 3: Partitions 4, 5

**Verify Assignment**:
```bash
docker-compose logs reports-ms | grep "partition"
```

## Producer Configuration

### API Gateway Producer Settings

**Location**: `services/api-gateway-bff/src/main/resources/application.yml`

```yaml
spring:
  kafka:
    producer:
      acks: all                    # Wait for all replicas to acknowledge
      retries: 3                   # Retry up to 3 times
      max-in-flight-requests-per-connection: 1  # Ensure ordering
      enable-idempotence: true     # Prevent duplicates
```

### Key Producer Behaviors

1. **Idempotent Producer** (`enable-idempotence: true`):
   - Prevents duplicate messages even with retries
   - Uses producer ID and sequence numbers
   - Ensures exactly-once semantics at producer level

2. **Acknowledgment** (`acks: all`):
   - Waits for all in-sync replicas to acknowledge
   - Strongest durability guarantee
   - Trade-off: Higher latency

3. **Retries** (`retries: 3`):
   - Automatically retries on transient failures
   - Combined with idempotence, safe to retry

### Publishing Events

**Code Location**: `services/api-gateway-bff/src/main/java/com/edjobim/gateway/service/ReportService.java`

```java
kafkaTemplate.send(MessageBuilder
    .withPayload(event)
    .setHeader(KafkaHeaders.TOPIC, "report.requested")
    .setHeader(KafkaHeaders.KEY, reportRequestId)  // Partitioning key
    .setHeader("X-Correlation-ID", correlationId)   // Custom header
    .build());
```

## Consumer Configuration

### Reports-MS Consumer Settings

**Location**: `services/reports-ms/src/main/resources/application.yml`

```yaml
spring:
  kafka:
    consumer:
      group-id: reports-ms-group
      enable-auto-commit: false    # Manual commit after processing
      auto-offset-reset: earliest  # Start from beginning if no offset
      max-poll-records: 10         # Process up to 10 records per poll
```

### Key Consumer Behaviors

1. **Manual Commit** (`enable-auto-commit: false`):
   - Offset committed only after successful processing
   - Prevents message loss on crashes
   - Trade-off: Must handle commit failures

2. **Offset Reset** (`auto-offset-reset: earliest`):
   - If no offset exists, start from beginning
   - Useful for new consumer groups
   - Alternative: `latest` (only new messages)

3. **Poll Size** (`max-poll-records: 10`):
   - Process up to 10 records per poll
   - Balance between throughput and latency
   - Adjust based on processing time

### Consuming Events

**Code Location**: `services/reports-ms/src/main/java/com/edjobim/reports/consumer/ReportRequestConsumer.java`

```java
@KafkaListener(topics = "report.requested", groupId = "reports-ms-group")
public void consume(ConsumerRecord<String, String> record,
                   @Header(KafkaHeaders.RECEIVED_KEY) String key,
                   Acknowledgment acknowledgment) {
    // Process event
    reportService.processReportRequest(event, correlationId);
    
    // Manual acknowledgment
    acknowledgment.acknowledge();
}
```

## Idempotent Consumer Pattern

### Problem

Kafka provides at-least-once delivery. If a consumer crashes after processing but before committing offset, the message will be reprocessed.

### Solution

Track processed `reportRequestId` in `processed_requests` table:

```sql
CREATE TABLE processed_requests (
    id BIGSERIAL PRIMARY KEY,
    report_request_id VARCHAR(255) UNIQUE NOT NULL,
    processed_at TIMESTAMP NOT NULL
);
```

### Implementation

**Code Location**: `services/reports-ms/src/main/java/com/edjobim/reports/service/ReportService.java`

```java
// Check if already processed
if (processedRequestRepository.findByReportRequestId(reportRequestId).isPresent()) {
    logger.info("Report request {} already processed, skipping", reportRequestId);
    return;  // Idempotent: skip duplicate
}

// Process report...

// Mark as processed
processedRequestRepository.save(new ProcessedRequest(reportRequestId));
```

### Testing Idempotency

1. **Send duplicate event**:
```bash
# Publish same event twice
docker-compose exec kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic report.requested \
  --property "parse.key=true" \
  --property "key.separator=:"
```

2. **Verify single processing**:
```bash
docker-compose exec postgres psql -U archivedb_user -d archivedb \
  -c "SELECT COUNT(*) FROM processed_requests WHERE report_request_id = 'test-id';"
```

**Expected**: Count = 1 (even if event consumed twice)

## Correlation ID Propagation

### Purpose

Track requests across services for debugging and observability.

### Implementation

1. **Gateway generates correlation ID** (if missing):
```java
String correlationId = request.getHeader("X-Correlation-ID");
if (correlationId == null) {
    correlationId = UUID.randomUUID().toString();
}
```

2. **Propagated via HTTP headers**:
```java
webClient.get()
    .header("X-Correlation-ID", correlationId)
```

3. **Propagated via Kafka headers**:
```java
.setHeader("X-Correlation-ID", correlationId)
```

4. **Logged via MDC**:
```java
MDC.put("correlationId", correlationId);
```

### Verify Correlation ID

```bash
# Check logs across services
docker-compose logs api-gateway-bff | grep "correlationId"
docker-compose logs reports-ms | grep "correlationId"
```

## Exactly-Once vs At-Least-Once

### Current Implementation: At-Least-Once

- **Producer**: Idempotent producer prevents duplicates
- **Consumer**: At-least-once delivery (may receive duplicates)
- **Solution**: Idempotent consumer handles duplicates

### Exactly-Once Semantics (Conceptual)

To achieve exactly-once:

1. **Transactional Producer**:
```yaml
spring:
  kafka:
    producer:
      transaction-id-prefix: reports-ms-tx-
```

2. **Transactional Consumer**:
```java
@KafkaListener(topics = "report.requested")
@Transactional
public void consume(...) {
    // Process and commit in same transaction
}
```

3. **Outbox Pattern**: See [08-FAILURE-HANDLING.md](08-FAILURE-HANDLING.md)

## Retry and DLQ

### Retry Strategy

**Location**: Consumer error handling

```java
try {
    reportService.processReportRequest(event, correlationId);
    acknowledgment.acknowledge();
} catch (Exception e) {
    logger.error("Error processing", e);
    // In production: implement retry with exponential backoff
    // After max retries: send to DLQ
    acknowledgment.acknowledge();  // Prevent infinite retries
}
```

### Retryable vs Non-Retryable Errors

**Retryable**:
- Network timeouts
- Database connection failures
- Transient Kafka errors

**Non-Retryable**:
- Invalid report type
- Authorization failures
- Malformed messages

### Dead Letter Queue (DLQ)

**Conceptual Implementation**:

```java
if (retryCount >= MAX_RETRIES) {
    kafkaTemplate.send("report.dlq", reportRequestId, event);
    // Alert operations team
}
```

## Monitoring Kafka

### Check Consumer Lag

```bash
docker-compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group reports-ms-group
```

**Expected Output**:
```
GROUP           TOPIC              PARTITION  CURRENT-OFFSET  LAG
reports-ms-group report.requested   0          100             0
reports-ms-group report.requested   1          95              0
```

**LAG = 0**: Consumer is up-to-date
**LAG > 0**: Consumer is behind (investigate)

### Check Topic Messages

```bash
# Count messages in topic
docker-compose exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic report.requested

# Consume messages (for debugging)
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic report.requested \
  --from-beginning \
  --max-messages 10
```

## Troubleshooting

### Issue: Consumer not receiving messages

**Symptoms**: Events published but not consumed

**Diagnosis**:
```bash
# Check consumer group
docker-compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group reports-ms-group

# Check consumer logs
docker-compose logs reports-ms | grep -i "consumer\|kafka"
```

**Solutions**:
1. Verify consumer group ID matches
2. Check partition assignment
3. Verify offset reset policy
4. Check for consumer errors in logs

### Issue: Messages stuck in topic

**Symptoms**: High consumer lag, messages not processing

**Diagnosis**:
```bash
# Check consumer lag
docker-compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group reports-ms-group
```

**Solutions**:
1. Scale consumers (increase replicas)
2. Check for processing errors
3. Verify database connectivity
4. Check resource limits (CPU/memory)

### Issue: Duplicate processing

**Symptoms**: Same report generated multiple times

**Diagnosis**:
```bash
# Check processed_requests table
docker-compose exec postgres psql -U archivedb_user -d archivedb \
  -c "SELECT report_request_id, COUNT(*) FROM processed_requests GROUP BY report_request_id HAVING COUNT(*) > 1;"
```

**Solutions**:
1. Verify idempotency check is working
2. Check database unique constraint
3. Verify transaction boundaries

## Next Steps

- See [03-REPORTS-DESIGN-PATTERNS.md](03-REPORTS-DESIGN-PATTERNS.md) for design patterns
- See [08-FAILURE-HANDLING.md](08-FAILURE-HANDLING.md) for retry/DLQ implementation
