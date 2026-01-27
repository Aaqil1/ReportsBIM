# Running Locally with Docker Compose

This guide provides step-by-step instructions to run the entire platform locally using Docker Compose.

## Prerequisites

- Docker Desktop (or Docker Engine + Docker Compose)
- At least 4GB RAM available for Docker
- Ports available: 8080, 8081, 8082, 8083, 8084, 9092, 2181, 5432

## Step 1: Build Docker Images

Build all service images:

```bash
# From project root
docker-compose build
```

**Expected Output**:
```
Building auth-service...
Step 1/8 : FROM maven:3.9-eclipse-temurin-17 AS build
...
Successfully built abc123def456
Successfully tagged edjo-bim-auth-service:latest
```

**Troubleshooting**:
- If build fails, ensure Maven can download dependencies (check internet)
- If out of memory, increase Docker Desktop memory allocation

## Step 2: Start Infrastructure Services

Start Kafka, Zookeeper, and PostgreSQL first:

```bash
docker-compose up -d zookeeper kafka postgres
```

**Verify Services**:
```bash
docker-compose ps
```

**Expected Output**:
```
NAME                STATUS          PORTS
kafka               Up 2 minutes    0.0.0.0:9092->9092/tcp
postgres            Up 2 minutes    0.0.0.0:5432->5432/tcp
zookeeper           Up 2 minutes    0.0.0.0:2181->2181/tcp
```

**Wait for Kafka to be ready** (30-60 seconds):
```bash
docker-compose logs kafka | grep "started"
```

**Expected Output**:
```
[2024-01-27 10:00:00,000] INFO Kafka version: 7.5.0 (org.apache.kafka.common.utils.AppInfoParser)
[2024-01-27 10:00:00,000] INFO Kafka commitId: abc123 (org.apache.kafka.common.utils.AppInfoParser)
[2024-01-27 10:00:00,000] INFO Started (kafka.server.KafkaServer)
```

## Step 3: Start Application Services

Start all application services:

```bash
docker-compose up -d
```

**Verify All Services**:
```bash
docker-compose ps
```

**Expected Output** (all services should be "Up"):
```
NAME                    STATUS          PORTS
aims-config-central     Up 1 minute     0.0.0.0:8084->8084/tcp
api-gateway-bff         Up 1 minute     0.0.0.0:8080->8080/tcp
auth-service            Up 1 minute     0.0.0.0:8081->8081/tcp
client-performance-svc  Up 1 minute     0.0.0.0:8083->8083/tcp
kafka                   Up 3 minutes    0.0.0.0:9092->9092/tcp
postgres                Up 3 minutes    0.0.0.0:5432->5432/tcp
reports-ms              Up 1 minute     0.0.0.0:8082->8082/tcp
zookeeper               Up 3 minutes    0.0.0.0:2181->2181/tcp
```

## Step 4: Check Service Logs

Verify services started successfully:

```bash
# Check all logs
docker-compose logs

# Check specific service
docker-compose logs api-gateway-bff

# Follow logs in real-time
docker-compose logs -f reports-ms
```

**Expected Log Patterns**:

**auth-service**:
```
Started AuthServiceApplication in 5.123 seconds
```

**reports-ms**:
```
Started ReportsMsApplication in 8.456 seconds
Connected to Kafka at kafka:9092
```

**api-gateway-bff**:
```
Started GatewayApplication in 6.789 seconds
```

## Step 5: Verify Health Endpoints

Check service health:

```bash
# Auth Service
curl http://localhost:8081/actuator/health

# Reports MS
curl http://localhost:8082/actuator/health

# API Gateway
curl http://localhost:8080/actuator/health
```

**Expected Output**:
```json
{"status":"UP"}
```

## Step 6: Test the Platform

### 6.1 Get Access Token

```bash
curl -X POST http://localhost:8081/oauth2/token \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user",
    "password": "password",
    "roles": ["ROLE_USER"]
  }'
```

**Expected Output**:
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Save the token**:
```bash
export TOKEN="<paste-token-here>"
```

### 6.2 Request a Report

```bash
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

**Expected Output**:
```json
{
  "reportRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Report request submitted successfully"
}
```

**Save the reportRequestId**:
```bash
export REPORT_ID="<paste-reportRequestId-here>"
```

### 6.3 Check Report Status

```bash
curl -X GET http://localhost:8080/api/v1/reports/$REPORT_ID/status \
  -H "Authorization: Bearer $TOKEN"
```

### 6.4 Verify Kafka Event

Check Kafka topics:

```bash
# Enter Kafka container
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Check messages in report.requested topic
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic report.requested \
  --from-beginning \
  --max-messages 1
```

### 6.5 Verify Report Generation

Check reports-ms logs:

```bash
docker-compose logs reports-ms | grep "Processing report request"
```

**Expected Output**:
```
2024-01-27 10:05:00 [550e8400-e29b-41d4-a716-446655440000] - Processing report request: 550e8400-e29b-41d4-a716-446655440000
2024-01-27 10:05:01 [550e8400-e29b-41d4-a716-446655440000] - Report 550e8400-e29b-41d4-a716-446655440000 generated successfully
```

### 6.6 Verify ArchiveDB

Check PostgreSQL:

```bash
docker-compose exec postgres psql -U archivedb_user -d archivedb -c "SELECT report_request_id, report_type, status FROM reports LIMIT 5;"
```

**Expected Output**:
```
      report_request_id       | report_type |  status
------------------------------+-------------+----------
 550e8400-e29b-41d4-a716-4466 | performance | COMPLETED
```

## Troubleshooting

### Issue: Services won't start

**Symptoms**: `docker-compose ps` shows services as "Restarting" or "Exited"

**Solution**:
```bash
# Check logs
docker-compose logs <service-name>

# Common causes:
# 1. Port conflict - check if ports are already in use
netstat -an | grep 8080

# 2. Database connection failure - wait longer for postgres to start
docker-compose logs postgres

# 3. Kafka not ready - wait for Kafka to fully start
docker-compose logs kafka | grep "started"
```

### Issue: Kafka connection errors

**Symptoms**: Logs show "Connection refused" to Kafka

**Solution**:
```bash
# Verify Kafka is running
docker-compose ps kafka

# Check Kafka logs
docker-compose logs kafka

# Restart Kafka
docker-compose restart kafka

# Wait 30 seconds, then restart dependent services
docker-compose restart reports-ms api-gateway-bff
```

### Issue: Database connection errors

**Symptoms**: `reports-ms` logs show "Connection refused" to PostgreSQL

**Solution**:
```bash
# Verify PostgreSQL is running
docker-compose ps postgres

# Check PostgreSQL logs
docker-compose logs postgres

# Test connection manually
docker-compose exec postgres psql -U archivedb_user -d archivedb -c "SELECT 1;"

# If connection works, restart reports-ms
docker-compose restart reports-ms
```

### Issue: JWT validation fails

**Symptoms**: API Gateway returns 401 Unauthorized

**Solution**:
```bash
# Verify auth-service is running
curl http://localhost:8081/oauth2/jwks

# Check if JWKS endpoint returns valid keys
# Verify token format (should start with "eyJ")

# Check API Gateway logs
docker-compose logs api-gateway-bff | grep -i "jwt\|token\|unauthorized"
```

### Issue: Reports not being generated

**Symptoms**: Report request succeeds but no report appears in ArchiveDB

**Solution**:
```bash
# Check Kafka consumer logs
docker-compose logs reports-ms | grep -i "consumer\|kafka\|error"

# Verify Kafka topic has messages
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic report.requested \
  --from-beginning \
  --max-messages 5

# Check reports-ms database connection
docker-compose logs reports-ms | grep -i "database\|postgres\|jdbc"
```

## Stopping Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (clears database)
docker-compose down -v

# Stop specific service
docker-compose stop reports-ms
```

## Cleaning Up

```bash
# Remove all containers, networks, and volumes
docker-compose down -v --remove-orphans

# Remove images
docker-compose down --rmi all
```

## Next Steps

- See [02-KAFKA-DEEP-DIVE.md](02-KAFKA-DEEP-DIVE.md) for Kafka details
- See [05-K8S-STEP-BY-STEP.md](05-K8S-STEP-BY-STEP.md) for Kubernetes deployment
