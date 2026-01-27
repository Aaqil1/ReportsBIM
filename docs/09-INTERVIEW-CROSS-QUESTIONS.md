# Interview Cross-Questions

This document provides concise answers to common system design interview questions.

## Kafka Partitioning

### Q: How many partitions? Why?

**Answer**: 6 partitions per topic.

**Reasoning**:
- Allows 1-6 consumer pods to process messages in parallel
- Current setup: 3 `reports-ms` replicas = 2 partitions per pod (good balance)
- Too few partitions limit scalability; too many add overhead
- 6 partitions provides flexibility for scaling (can scale to 6 pods)

**What I would say in interview**:
> "We use 6 partitions per topic. This allows us to scale consumers from 1 to 6 pods while maintaining good parallelism. With our current 3 replicas, each pod handles 2 partitions, which balances load effectively. We chose 6 because it's a good middle ground—enough for scalability without excessive overhead."

### Q: How many pods? What is the mapping between partitions and consumer pods?

**Answer**: 3 `reports-ms` pods.

**Mapping**:
- Pod 1: Partitions 0, 1
- Pod 2: Partitions 2, 3
- Pod 3: Partitions 4, 5

**What I would say**:
> "We run 3 consumer pods in the `reports-ms-group` consumer group. Kafka automatically assigns partitions to consumers. With 6 partitions and 3 pods, each pod gets 2 partitions. This ensures all partitions are consumed and provides redundancy—if one pod fails, its partitions are reassigned to remaining pods."

### Q: Who decided partitions and pods? How did you arrive at capacity?

**Answer**: Platform architect/SRE team decision based on capacity planning.

**Process**:
1. **Throughput Analysis**: Estimated message rate (e.g., 1000 reports/hour)
2. **Processing Time**: Average report generation time (e.g., 2 seconds)
3. **Capacity Calculation**: 
   - 1 pod: 2 partitions × 0.5 msg/sec = 1 msg/sec = 3600 msg/hour
   - 3 pods: 6 partitions × 0.5 msg/sec = 3 msg/sec = 10800 msg/hour
4. **Headroom**: 3 pods provide 3x capacity (safety margin)
5. **Cost vs Performance**: Balance between resource cost and performance

**What I would say**:
> "The platform architect and SRE team decided based on capacity planning. We estimated 1000 reports/hour peak load, with each report taking ~2 seconds to generate. With 6 partitions and 3 pods, we can process 3 reports/second (10,800/hour), giving us 10x headroom. We chose 3 pods to balance cost and performance—enough capacity for growth without over-provisioning."

### Q: What happens if consumers > partitions? consumers < partitions?

**Answer**:

**Consumers > Partitions**:
- Extra consumers are idle (no partitions assigned)
- Waste of resources
- Example: 8 consumers, 6 partitions → 2 consumers idle

**Consumers < Partitions**:
- Some consumers handle multiple partitions
- Still works, but less parallelism
- Example: 2 consumers, 6 partitions → each handles 3 partitions

**What I would say**:
> "If consumers exceed partitions, extra consumers remain idle—they don't get any partitions assigned. This wastes resources. If consumers are fewer than partitions, each consumer handles multiple partitions, which still works but reduces parallelism. Ideally, consumers should equal partitions for optimal parallelism, but having fewer consumers is fine—they'll just handle more partitions each."

## Pod Failures

### Q: How do you handle pod failures?

**Answer**: Kubernetes restart policy, probes, replica count, rollout, PDB.

**Mechanisms**:

1. **Restart Policy**: `Always` (default for Deployments)
   - Pod restarts automatically on failure

2. **Liveness Probe**: Detects if pod is dead
   ```yaml
   livenessProbe:
     httpGet:
       path: /actuator/health/liveness
       port: 8082
     initialDelaySeconds: 60
     periodSeconds: 10
   ```
   - If probe fails → Kubernetes restarts pod

3. **Readiness Probe**: Detects if pod is ready
   ```yaml
   readinessProbe:
     httpGet:
       path: /actuator/health/readiness
       port: 8082
     initialDelaySeconds: 30
     periodSeconds: 5
   ```
   - If probe fails → Pod removed from service endpoints

4. **Replica Count**: Multiple pods for redundancy
   - 3 replicas → if 1 fails, 2 still serve traffic

5. **Rolling Update**: Gradual replacement during updates
   - New pods start before old pods terminate

6. **Pod Disruption Budget (PDB)**: Ensures minimum availability
   ```yaml
   apiVersion: policy/v1
   kind: PodDisruptionBudget
   metadata:
     name: reports-ms-pdb
   spec:
     minAvailable: 2
     selector:
       matchLabels:
         app: reports-ms
   ```

**What I would say**:
> "We handle pod failures through multiple layers: Kubernetes automatically restarts failed pods due to the Always restart policy. Liveness probes detect dead pods and trigger restarts. Readiness probes remove unhealthy pods from service endpoints. We run 3 replicas for redundancy—if one pod fails, the other two continue serving. During updates, rolling updates ensure new pods start before old ones terminate. We also use Pod Disruption Budgets to ensure at least 2 pods are always available during voluntary disruptions."

## Kafka Failure Handling

### Q: Producer retries, acks, idempotent producer concept

**Answer**:

**Retries**: `retries: 3`
- Automatically retries on transient failures
- Combined with idempotence, safe to retry

**Acks**: `acks: all`
- Waits for all in-sync replicas to acknowledge
- Strongest durability guarantee
- Trade-off: Higher latency

**Idempotent Producer**: `enable-idempotence: true`
- Prevents duplicate messages even with retries
- Uses producer ID and sequence numbers
- Ensures exactly-once semantics at producer level

**What I would say**:
> "Our producer is configured with `retries: 3` for automatic retry on transient failures, `acks: all` to wait for all replicas (strongest durability), and `enable-idempotence: true` to prevent duplicates. The idempotent producer uses a producer ID and sequence numbers—even if a message is retried, Kafka deduplicates it based on these identifiers. This gives us exactly-once semantics at the producer level."

### Q: Consumer retries/DLQ, offset commit strategy

**Answer**:

**Retries**: Manual retry logic (conceptual)
- Retry up to 3 times with exponential backoff
- After max retries → send to DLQ

**DLQ**: Dead Letter Queue topic (`report.dlq`)
- Captures messages that cannot be processed
- Enables manual intervention and alerting

**Offset Commit**: Manual commit (`enable-auto-commit: false`)
- Offset committed only after successful processing
- Prevents message loss on crashes
- Trade-off: Must handle commit failures

**What I would say**:
> "We use manual offset commits—offsets are only committed after successful processing, preventing message loss if a consumer crashes mid-processing. For retries, we implement exponential backoff (conceptually)—retry up to 3 times, then send to the Dead Letter Queue. The DLQ captures permanently failed messages for manual review and alerting. This ensures we don't lose messages and can recover from transient failures."

## Kubernetes Operations

### Q: How to check if a pod is down?

**Answer**:

```bash
# Check pod status
kubectl get pods -n edjo-bim

# Detailed status
kubectl describe pod <pod-name> -n edjo-bim

# Check events
kubectl get events -n edjo-bim --sort-by='.lastTimestamp'
```

**Status Indicators**:
- `Running`: Healthy
- `Pending`: Not scheduled yet
- `CrashLoopBackOff`: Container crashing repeatedly
- `Error`: Container failed to start

**What I would say**:
> "I use `kubectl get pods` to see pod status. If a pod shows `CrashLoopBackOff` or `Error`, I check `kubectl describe pod` for details and `kubectl get events` to see recent events. The describe command shows container state, restart count, and recent events which help diagnose the issue."

### Q: How to check logs?

**Answer**:

```bash
# Current logs
kubectl logs <pod-name> -n edjo-bim

# Follow logs
kubectl logs -f <pod-name> -n edjo-bim

# Previous container logs
kubectl logs <pod-name> -n edjo-bim --previous

# Logs by label selector
kubectl logs -l app=reports-ms -n edjo-bim
```

**What I would say**:
> "I use `kubectl logs` with the pod name. For real-time monitoring, I use `-f` to follow logs. If a pod restarted, I use `--previous` to see logs from the previous container. For multiple pods with the same label, I use `-l app=reports-ms` to get logs from all matching pods."

## Docker Operations

### Q: How to build Docker image?

**Answer**:

```bash
# Build image
docker build -t your-registry.io/edjo-bim/reports-ms:1.0.0 ./services/reports-ms

# Tag image
docker tag your-registry.io/edjo-bim/reports-ms:1.0.0 \
           your-registry.io/edjo-bim/reports-ms:latest

# Run container
docker run -p 8082:8082 your-registry.io/edjo-bim/reports-ms:1.0.0

# Push to registry
docker push your-registry.io/edjo-bim/reports-ms:1.0.0
```

**What I would say**:
> "I build images with `docker build -t` specifying the tag. I tag with both version and `latest` for flexibility. To run locally, I use `docker run -p` to map ports. To push to a registry, I use `docker push` after logging in with `docker login`."

## CI/CD Pipeline

### Q: What stages and why?

**Answer**:

1. **Build**: Compile code (`mvn compile`)
2. **Test**: Run unit/integration tests (`mvn test`)
3. **Build Docker**: Create container images
4. **Push Images**: Push to registry
5. **Deploy**: Deploy via Helm to Kubernetes

**Why**: Each stage validates quality and prepares artifacts for deployment.

**What I would say**:
> "Our pipeline has 5 stages: Build compiles the code, Test runs unit and integration tests to catch bugs early, Build Docker creates container images, Push Images stores them in the registry, and Deploy uses Helm to update Kubernetes. Each stage acts as a gate—if any stage fails, the pipeline stops, preventing bad code from reaching production."

### Q: Deployment strategy (rolling, blue-green, canary)

**Answer**:

**Rolling Update** (Current):
- Gradual replacement of pods
- Zero downtime if health checks pass
- Default Kubernetes behavior

**Blue-Green**:
- Deploy new version alongside old
- Switch traffic all at once
- Instant rollback

**Canary**:
- Deploy to small percentage of traffic
- Gradually increase if healthy
- Low risk

**What I would say**:
> "We use rolling updates by default—Kubernetes gradually replaces old pods with new ones, providing zero downtime. For critical releases, we'd consider blue-green deployment—deploy the new version alongside the old, test it, then switch traffic all at once for instant rollback. For high-risk changes, canary deployments route a small percentage of traffic to the new version, gradually increasing if metrics look good."

## Idempotent Consumer

### Q: What is idempotent consumer? How implemented here?

**Answer**:

**Definition**: Consumer that produces the same result regardless of how many times a message is processed.

**Implementation**: Track processed `reportRequestId` in `processed_requests` table.

```java
// Check if already processed
if (processedRequestRepository.findByReportRequestId(reportRequestId).isPresent()) {
    return;  // Skip duplicate
}

// Process report...

// Mark as processed
processedRequestRepository.save(new ProcessedRequest(reportRequestId));
```

**What I would say**:
> "An idempotent consumer produces the same result even if a message is processed multiple times. We implement this by tracking processed `reportRequestId` values in a `processed_requests` table. Before processing, we check if the ID exists—if it does, we skip processing. After successful processing, we save the ID. This handles duplicate messages from Kafka's at-least-once delivery."

## Outbox Pattern

### Q: What/why/how (and where you'd add it)

**Answer**:

**What**: Pattern to ensure atomicity between database writes and event publishing.

**Why**: Prevents inconsistency if database write succeeds but Kafka publish fails.

**How**: 
1. Save to outbox table in same transaction as main data
2. Separate process polls outbox and publishes events
3. Mark events as processed after successful publish

**Where to add**: In `reports-ms` service, when saving reports and publishing completion events.

**What I would say**:
> "The Outbox pattern ensures atomicity between database writes and event publishing. Currently, if we save a report to the database but Kafka publish fails, we have inconsistency. With Outbox, we'd save both the report and an outbox event in the same database transaction. A separate scheduled process polls the outbox table and publishes events to Kafka, marking them as processed. This ensures events are eventually published even if Kafka is temporarily unavailable. I'd add this in the `reports-ms` service when saving completed reports."

## Summary

| Topic | Key Points |
|-------|------------|
| Partitions | 6 partitions, allows 1-6 consumer pods |
| Pods | 3 replicas, 2 partitions per pod |
| Pod Failures | Restart policy, probes, replicas, PDB |
| Producer | Retries, acks=all, idempotent |
| Consumer | Manual commit, retry logic, DLQ |
| Kubernetes | `kubectl get/describe/logs` |
| Docker | `build`, `tag`, `run`, `push` |
| CI/CD | Build → Test → Docker → Push → Deploy |
| Idempotent Consumer | Track processed IDs in database |
| Outbox Pattern | Atomic DB + Kafka writes |
