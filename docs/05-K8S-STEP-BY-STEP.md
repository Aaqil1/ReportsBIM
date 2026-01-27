# Kubernetes Step-by-Step Guide

This guide provides hands-on commands for deploying and managing the platform on Kubernetes.

## Prerequisites

- Kubernetes cluster (minikube, kind, GKE, EKS, AKS, etc.)
- `kubectl` configured to access cluster
- Helm 3.x installed
- Docker images built and pushed to registry (or use local registry)

## Step 1: Create Namespace

```bash
kubectl apply -f k8s/namespace.yaml
```

**Verify**:
```bash
kubectl get namespace edjo-bim
```

**Expected Output**:
```
NAME       STATUS   AGE
edjo-bim   Active   5s
```

## Step 2: Deploy PostgreSQL

### 2.1 Create ConfigMap and Secret

```bash
kubectl apply -f k8s/postgres-configmap.yaml
kubectl apply -f k8s/postgres-secret.yaml
```

**Verify**:
```bash
kubectl get configmap postgres-config -n edjo-bim
kubectl get secret postgres-secret -n edjo-bim
```

### 2.2 Deploy PostgreSQL

```bash
kubectl apply -f k8s/postgres-deployment.yaml
```

**Wait for pod to be ready**:
```bash
kubectl wait --for=condition=ready pod -l app=postgres -n edjo-bim --timeout=120s
```

**Verify**:
```bash
kubectl get pods -n edjo-bim
```

**Expected Output**:
```
NAME                       READY   STATUS    RESTARTS   AGE
postgres-7d8f9c4b5-abc12   1/1     Running   0          30s
```

**Check logs**:
```bash
kubectl logs -l app=postgres -n edjo-bim
```

**Expected Output**:
```
PostgreSQL init process complete; ready for start up.
database system is ready to accept connections
```

## Step 3: Deploy Auth Service

```bash
kubectl apply -f k8s/auth-service-deployment.yaml
```

**Note**: Update image reference in YAML if using different registry.

**Wait for pod**:
```bash
kubectl wait --for=condition=ready pod -l app=auth-service -n edjo-bim --timeout=120s
```

**Verify**:
```bash
kubectl get pods -n edjo-bim -l app=auth-service
kubectl get svc -n edjo-bim -l app=auth-service
```

**Test health endpoint**:
```bash
kubectl port-forward svc/auth-service 8081:8081 -n edjo-bim &
curl http://localhost:8081/actuator/health
```

## Step 4: Deploy Reports Microservice

```bash
kubectl apply -f k8s/reports-ms-deployment.yaml
```

**Wait for pods**:
```bash
kubectl wait --for=condition=ready pod -l app=reports-ms -n edjo-bim --timeout=120s
```

**Verify replicas**:
```bash
kubectl get pods -n edjo-bim -l app=reports-ms
```

**Expected Output** (3 replicas):
```
NAME                         READY   STATUS    RESTARTS   AGE
reports-ms-7d8f9c4b5-abc12   1/1     Running   0          30s
reports-ms-7d8f9c4b5-def34   1/1     Running   0          30s
reports-ms-7d8f9c4b5-ghi56   1/1     Running   0          30s
```

**Check logs**:
```bash
kubectl logs -l app=reports-ms -n edjo-bim --tail=50
```

## Step 5: Deploy API Gateway

```bash
kubectl apply -f k8s/api-gateway-deployment.yaml
```

**Wait for pods**:
```bash
kubectl wait --for=condition=ready pod -l app=api-gateway-bff -n edjo-bim --timeout=120s
```

**Verify**:
```bash
kubectl get pods -n edjo-bim -l app=api-gateway-bff
kubectl get svc -n edjo-bim -l app=api-gateway-bff
```

## Step 6: Verify Deployment

### Check All Pods

```bash
kubectl get pods -n edjo-bim
```

**Expected Output**:
```
NAME                          READY   STATUS    RESTARTS   AGE
api-gateway-bff-abc12-def34   2/2     Running   0          1m
auth-service-xyz78-uvw90      1/1     Running   0          2m
postgres-7d8f9c4b5-abc12      1/1     Running   0          5m
reports-ms-7d8f9c4b5-abc12    1/1     Running   0          3m
reports-ms-7d8f9c4b5-def34   1/1     Running   0          3m
reports-ms-7d8f9c4b5-ghi56   1/1     Running   0          3m
```

### Check Services

```bash
kubectl get svc -n edjo-bim
```

**Expected Output**:
```
NAME              TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)        AGE
api-gateway-bff  LoadBalancer   10.96.123.45    <pending>     80:30080/TCP   1m
auth-service      ClusterIP      10.96.234.56    <none>        8081/TCP       2m
postgres          ClusterIP      10.96.345.67    <none>        5432/TCP       5m
reports-ms        ClusterIP      10.96.456.78    <none>        8082/TCP       3m
```

## Common Operations

### Check Pod Status

```bash
# All pods
kubectl get pods -n edjo-bim

# Specific pod
kubectl get pod <pod-name> -n edjo-bim

# Detailed status
kubectl describe pod <pod-name> -n edjo-bim
```

### Check Pod Logs

```bash
# Current logs
kubectl logs <pod-name> -n edjo-bim

# Follow logs
kubectl logs -f <pod-name> -n edjo-bim

# Previous container logs (if restarted)
kubectl logs <pod-name> -n edjo-bim --previous

# Logs by label selector
kubectl logs -l app=reports-ms -n edjo-bim

# Logs from all pods with label
kubectl logs -l app=reports-ms -n edjo-bim --all-containers=true
```

### Check Events

```bash
# All events in namespace
kubectl get events -n edjo-bim --sort-by='.lastTimestamp'

# Events for specific pod
kubectl describe pod <pod-name> -n edjo-bim | grep Events -A 10
```

### Check if Pod is Down

```bash
# Check pod status
kubectl get pods -n edjo-bim | grep <pod-name>

# If STATUS is not "Running", check details
kubectl describe pod <pod-name> -n edjo-bim

# Common statuses:
# - Pending: Not scheduled yet
# - ContainerCreating: Pulling image or creating container
# - CrashLoopBackOff: Container crashing repeatedly
# - Error: Container failed to start
# - Running: Healthy
```

### Execute Commands in Pod

```bash
# Shell into pod
kubectl exec -it <pod-name> -n edjo-bim -- /bin/sh

# Run command
kubectl exec <pod-name> -n edjo-bim -- ps aux

# Execute in specific container (if multi-container pod)
kubectl exec -it <pod-name> -n edjo-bim -c <container-name> -- /bin/sh
```

### Port Forwarding

```bash
# Forward service port
kubectl port-forward svc/api-gateway-bff 8080:80 -n edjo-bim

# Forward pod port
kubectl port-forward pod/<pod-name> 8080:8080 -n edjo-bim

# Background port forward
kubectl port-forward svc/api-gateway-bff 8080:80 -n edjo-bim &
```

### Scale Deployment

```bash
# Scale reports-ms to 5 replicas
kubectl scale deployment reports-ms -n edjo-bim --replicas=5

# Verify
kubectl get pods -n edjo-bim -l app=reports-ms
```

### Restart Deployment

```bash
# Restart all pods in deployment
kubectl rollout restart deployment reports-ms -n edjo-bim

# Check rollout status
kubectl rollout status deployment reports-ms -n edjo-bim
```

### Update Deployment

```bash
# Update image
kubectl set image deployment/reports-ms reports-ms=your-registry.io/edjo-bim/reports-ms:1.0.1 -n edjo-bim

# Check rollout
kubectl rollout status deployment reports-ms -n edjo-bim

# Rollback if needed
kubectl rollout undo deployment reports-ms -n edjo-bim
```

## Troubleshooting

### Pod Not Starting

**Symptoms**: Pod status is `Pending` or `ContainerCreating` for long time

**Diagnosis**:
```bash
# Check pod details
kubectl describe pod <pod-name> -n edjo-bim

# Check events
kubectl get events -n edjo-bim --field-selector involvedObject.name=<pod-name>
```

**Common Causes**:
1. **Image pull errors**: Check image name/tag, registry credentials
2. **Resource constraints**: Check node resources, resource requests/limits
3. **Volume mount issues**: Check PVC exists, storage class available

### Pod CrashLoopBackOff

**Symptoms**: Pod restarts repeatedly

**Diagnosis**:
```bash
# Check logs
kubectl logs <pod-name> -n edjo-bim --previous

# Check events
kubectl describe pod <pod-name> -n edjo-bim | grep Events -A 20
```

**Common Causes**:
1. **Application errors**: Check application logs
2. **Configuration errors**: Check ConfigMap/Secret values
3. **Database connection**: Verify database is accessible
4. **Kafka connection**: Verify Kafka bootstrap servers

### Service Not Accessible

**Symptoms**: Cannot connect to service

**Diagnosis**:
```bash
# Check service endpoints
kubectl get endpoints <service-name> -n edjo-bim

# Check service selector matches pod labels
kubectl get svc <service-name> -n edjo-bim -o yaml | grep selector
kubectl get pods -n edjo-bim --show-labels
```

**Solutions**:
1. Verify service selector matches pod labels
2. Check pods are running and ready
3. Verify port numbers match

### Database Connection Issues

**Symptoms**: Reports-MS cannot connect to PostgreSQL

**Diagnosis**:
```bash
# Check PostgreSQL pod
kubectl get pods -n edjo-bim -l app=postgres

# Check PostgreSQL logs
kubectl logs -l app=postgres -n edjo-bim

# Test connection from reports-ms pod
kubectl exec -it <reports-ms-pod> -n edjo-bim -- \
  wget -O- http://postgres:5432 || echo "Connection failed"
```

**Solutions**:
1. Verify PostgreSQL service name (`postgres`)
2. Check environment variables (ARCHIVE_DB_HOST, ARCHIVE_DB_PORT)
3. Verify ConfigMap/Secret exist and are mounted

### Kafka Connection Issues

**Symptoms**: Services cannot connect to Kafka

**Diagnosis**:
```bash
# Check Kafka pod (if deployed in cluster)
kubectl get pods -n kafka

# Check environment variables
kubectl exec <pod-name> -n edjo-bim -- env | grep KAFKA

# Test connection
kubectl exec <pod-name> -n edjo-bim -- \
  nc -zv kafka-host 9092 || echo "Connection failed"
```

**Solutions**:
1. Verify KAFKA_BOOTSTRAP_SERVERS environment variable
2. Check Kafka service is accessible from pod network
3. Verify network policies allow traffic

## Health Checks

### Liveness Probe

**Purpose**: Detect if container is alive (restart if dead)

**Configuration**: `k8s/reports-ms-deployment.yaml`

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8082
  initialDelaySeconds: 60
  periodSeconds: 10
```

**Check**:
```bash
kubectl get pod <pod-name> -n edjo-bim -o yaml | grep -A 10 livenessProbe
```

### Readiness Probe

**Purpose**: Detect if container is ready to serve traffic

**Configuration**:

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8082
  initialDelaySeconds: 30
  periodSeconds: 5
```

**Check**:
```bash
kubectl get pod <pod-name> -n edjo-bim -o yaml | grep -A 10 readinessProbe
```

## Resource Management

### Check Resource Usage

```bash
# Node resources
kubectl top nodes

# Pod resources
kubectl top pods -n edjo-bim
```

### Resource Limits

**Configuration**: `k8s/reports-ms-deployment.yaml`

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

**Verify**:
```bash
kubectl describe pod <pod-name> -n edjo-bim | grep -A 5 "Limits\|Requests"
```

## Next Steps

- See [06-HELM-DEPLOYMENT.md](06-HELM-DEPLOYMENT.md) for Helm-based deployment
- See [07-CICD-PIPELINE.md](07-CICD-PIPELINE.md) for CI/CD automation
