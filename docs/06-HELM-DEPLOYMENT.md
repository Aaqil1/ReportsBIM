# Helm Deployment Guide

This guide explains how to deploy the platform using Helm charts.

## Prerequisites

- Helm 3.x installed
- Kubernetes cluster access
- Docker images built and pushed to registry

## Helm Chart Structure

```
helm/edjo-bim/
├── Chart.yaml              # Chart metadata
├── values.yaml             # Default values
└── templates/
    ├── _helpers.tpl        # Template helpers
    ├── postgres.yaml       # PostgreSQL deployment
    ├── reports-ms.yaml     # Reports microservice
    └── api-gateway.yaml    # API Gateway
```

## Step 1: Review Values

**File**: `helm/edjo-bim/values.yaml`

```yaml
global:
  imageRegistry: your-registry.io/edjo-bim
  imageTag: "1.0.0"

reportsMs:
  enabled: true
  replicas: 3
  resources:
    requests:
      memory: "512Mi"
      cpu: "500m"
```

**Customize**:
```bash
# Create custom values file
cat > my-values.yaml <<EOF
global:
  imageRegistry: my-registry.io/edjo-bim
  imageTag: "1.0.1"

reportsMs:
  replicas: 5
EOF
```

## Step 2: Install Chart

### Dry Run (Validate)

```bash
helm install edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --create-namespace \
  --dry-run \
  --debug
```

**Expected Output**: Shows rendered YAML without installing

### Install

```bash
helm install edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --create-namespace
```

**Expected Output**:
```
NAME: edjo-bim
LAST DEPLOYED: Mon Jan 27 10:00:00 2024
NAMESPACE: edjo-bim
STATUS: deployed
REVISION: 1
```

### Install with Custom Values

```bash
helm install edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --create-namespace \
  -f my-values.yaml
```

## Step 3: Verify Installation

### Check Release Status

```bash
helm status edjo-bim -n edjo-bim
```

**Expected Output**:
```
NAME: edjo-bim
LAST DEPLOYED: Mon Jan 27 10:00:00 2024
NAMESPACE: edjo-bim
STATUS: deployed
REVISION: 1
```

### Check Resources

```bash
# All resources
kubectl get all -n edjo-bim

# Specific resource type
kubectl get deployments -n edjo-bim
kubectl get services -n edjo-bim
kubectl get configmaps -n edjo-bim
kubectl get secrets -n edjo-bim
```

### Check Pods

```bash
kubectl get pods -n edjo-bim
```

**Expected Output**:
```
NAME                          READY   STATUS    RESTARTS   AGE
api-gateway-bff-abc12-def34   2/2     Running   0          1m
postgres-xyz78-uvw90          1/1     Running   0          2m
reports-ms-7d8f9c4b5-abc12   1/1     Running   0          3m
reports-ms-7d8f9c4b5-def34   1/1     Running   0          3m
reports-ms-7d8f9c4b5-ghi56   1/1     Running   0          3m
```

## Step 4: Upgrade Release

### Upgrade with New Values

```bash
helm upgrade edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  -f my-values.yaml
```

**Expected Output**:
```
Release "edjo-bim" has been upgraded. Happy Helming!
NAME: edjo-bim
LAST DEPLOYED: Mon Jan 27 10:05:00 2024
NAMESPACE: edjo-bim
STATUS: deployed
REVISION: 2
```

### Upgrade with New Image Tag

```bash
helm upgrade edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --set global.imageTag=1.0.2 \
  --set reportsMs.replicas=5
```

### Check Upgrade Status

```bash
helm status edjo-bim -n edjo-bim
kubectl rollout status deployment/reports-ms -n edjo-bim
```

## Step 5: Rollback

### List Revisions

```bash
helm history edjo-bim -n edjo-bim
```

**Expected Output**:
```
REVISION	UPDATED                 	STATUS    	CHART          	APP VERSION	DESCRIPTION
1       	Mon Jan 27 10:00:00 2024	deployed  	edjo-bim-1.0.0	1.0.0      	Install complete
2       	Mon Jan 27 10:05:00 2024	deployed  	edjo-bim-1.0.0	1.0.0      	Upgrade complete
```

### Rollback to Previous Revision

```bash
helm rollback edjo-bim -n edjo-bim
```

**Expected Output**:
```
Rollback was a success! Happy Helming!
```

### Rollback to Specific Revision

```bash
helm rollback edjo-bim 1 -n edjo-bim
```

## Step 6: Uninstall

### Uninstall Release

```bash
helm uninstall edjo-bim -n edjo-bim
```

**Expected Output**:
```
release "edjo-bim" uninstalled
```

**Note**: This removes Helm-managed resources. PVCs and secrets may remain.

### Clean Up Resources

```bash
# Delete namespace (removes all resources)
kubectl delete namespace edjo-bim

# Or delete specific resources
kubectl delete pvc -n edjo-bim --all
```

## Common Operations

### List Releases

```bash
helm list -n edjo-bim
```

### Get Values

```bash
# Current deployed values
helm get values edjo-bim -n edjo-bim

# All values (including defaults)
helm get values edjo-bim -n edjo-bim --all
```

### Template Rendering

```bash
# Render templates locally
helm template edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  -f my-values.yaml

# Save rendered YAML
helm template edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  -f my-values.yaml > rendered.yaml
```

### Validate Chart

```bash
helm lint ./helm/edjo-bim
```

**Expected Output**:
```
==> Linting ./helm/edjo-bim
[INFO] Chart.yaml: icon is recommended

1 chart(s) linted, 0 failures
```

## Customization Examples

### Example 1: Increase Replicas

```bash
helm upgrade edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --set reportsMs.replicas=6 \
  --set apiGatewayBff.replicas=3
```

### Example 2: Change Resource Limits

```bash
helm upgrade edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --set reportsMs.resources.limits.memory=2Gi \
  --set reportsMs.resources.limits.cpu=2000m
```

### Example 3: Disable Component

```bash
helm upgrade edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --set postgres.enabled=false
```

### Example 4: External Database

```bash
helm upgrade edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --set postgres.enabled=false \
  --set reportsMs.env.ARCHIVE_DB_HOST=external-db.example.com \
  --set reportsMs.env.ARCHIVE_DB_PORT=5432
```

## Troubleshooting

### Issue: Chart Installation Fails

**Symptoms**: `helm install` returns error

**Diagnosis**:
```bash
# Dry run to see errors
helm install edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --dry-run \
  --debug
```

**Common Causes**:
1. **Invalid YAML**: Check template syntax
2. **Missing values**: Verify required values are set
3. **Resource conflicts**: Check if resources already exist

### Issue: Pods Not Starting After Upgrade

**Symptoms**: Pods stuck in `Pending` or `CrashLoopBackOff`

**Diagnosis**:
```bash
# Check rollout status
kubectl rollout status deployment/reports-ms -n edjo-bim

# Check pod events
kubectl describe pod <pod-name> -n edjo-bim

# Check previous revision
helm history edjo-bim -n edjo-bim
```

**Solution**: Rollback if needed
```bash
helm rollback edjo-bim -n edjo-bim
```

### Issue: Values Not Applied

**Symptoms**: Changes in values.yaml not reflected

**Diagnosis**:
```bash
# Check deployed values
helm get values edjo-bim -n edjo-bim

# Verify values file syntax
helm lint ./helm/edjo-bim -f my-values.yaml
```

**Solution**: Upgrade with explicit values
```bash
helm upgrade edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  -f my-values.yaml \
  --reset-values
```

## Best Practices

1. **Version Control**: Store values files in Git
2. **Environment-Specific Values**: Use separate values files per environment
3. **Dry Run First**: Always test with `--dry-run` before installing
4. **Incremental Upgrades**: Upgrade one component at a time
5. **Monitor Rollouts**: Watch `kubectl rollout status` during upgrades
6. **Keep Revisions**: Don't delete Helm history (helps with rollback)

## Next Steps

- See [07-CICD-PIPELINE.md](07-CICD-PIPELINE.md) for automated Helm deployments
- See [05-K8S-STEP-BY-STEP.md](05-K8S-STEP-BY-STEP.md) for manual Kubernetes operations
