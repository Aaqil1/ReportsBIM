# CI/CD Pipeline

This document describes the CI/CD pipeline configuration and deployment strategy.

## Pipeline Overview

```
┌─────────┐    ┌─────────┐    ┌──────────────┐    ┌─────────────┐    ┌──────────┐
│  Build  │ -> │  Test   │ -> │ Build Docker │ -> │ Push Images │ -> │  Deploy  │
└─────────┘    └─────────┘    └──────────────┘    └─────────────┘    └──────────┘
```

## Pipeline Stages

### Stage 1: Build

**Purpose**: Compile Java code and run unit tests

**Commands**:
```bash
mvn clean compile
mvn test
mvn package -DskipTests
```

**Artifacts**: JAR files in `target/` directory

### Stage 2: Test

**Purpose**: Run integration tests and code quality checks

**Commands**:
```bash
mvn verify
mvn sonar:sonar  # If SonarQube configured
```

**Gates**: Tests must pass, code coverage threshold met

### Stage 3: Build Docker Images

**Purpose**: Create Docker images for each service

**Commands**:
```bash
# Build each service
docker build -t your-registry.io/edjo-bim/auth-service:${BUILD_NUMBER} ./services/auth-service
docker build -t your-registry.io/edjo-bim/reports-ms:${BUILD_NUMBER} ./services/reports-ms
docker build -t your-registry.io/edjo-bim/api-gateway-bff:${BUILD_NUMBER} ./services/api-gateway-bff
# ... other services
```

**Artifacts**: Docker images tagged with build number

### Stage 4: Push to Registry

**Purpose**: Push images to container registry

**Commands**:
```bash
docker push your-registry.io/edjo-bim/auth-service:${BUILD_NUMBER}
docker push your-registry.io/edjo-bim/reports-ms:${BUILD_NUMBER}
docker push your-registry.io/edjo-bim/api-gateway-bff:${BUILD_NUMBER}
# ... other services

# Tag as latest
docker tag your-registry.io/edjo-bim/auth-service:${BUILD_NUMBER} \
           your-registry.io/edjo-bim/auth-service:latest
docker push your-registry.io/edjo-bim/auth-service:latest
```

**Gates**: All images pushed successfully

### Stage 5: Deploy

**Purpose**: Deploy to Kubernetes using Helm

**Commands**:
```bash
helm upgrade edjo-bim ./helm/edjo-bim \
  --namespace edjo-bim \
  --create-namespace \
  --set global.imageTag=${BUILD_NUMBER} \
  --wait \
  --timeout 5m
```

**Gates**: Helm deployment succeeds, pods become ready

## Jenkinsfile

**Location**: `Jenkinsfile`

```groovy
pipeline {
    agent any
    
    environment {
        REGISTRY = 'your-registry.io/edjo-bim'
        KUBERNETES_NAMESPACE = 'edjo-bim'
        HELM_CHART_PATH = './helm/edjo-bim'
    }
    
    stages {
        stage('Build') {
            steps {
                sh 'mvn clean compile'
            }
        }
        
        stage('Test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }
        
        stage('Package') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }
        
        stage('Build Docker Images') {
            steps {
                script {
                    def services = ['auth-service', 'reports-ms', 'api-gateway-bff', 
                                   'client-performance-svc', 'aims-config-central']
                    services.each { service ->
                        sh """
                            docker build -t ${REGISTRY}/${service}:${BUILD_NUMBER} \
                                         -t ${REGISTRY}/${service}:latest \
                                         ./services/${service}
                        """
                    }
                }
            }
        }
        
        stage('Push Images') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'docker-registry-creds',
                                                    usernameVariable: 'DOCKER_USER',
                                                    passwordVariable: 'DOCKER_PASS')]) {
                        sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin ${REGISTRY}'
                        
                        def services = ['auth-service', 'reports-ms', 'api-gateway-bff',
                                       'client-performance-svc', 'aims-config-central']
                        services.each { service ->
                            sh """
                                docker push ${REGISTRY}/${service}:${BUILD_NUMBER}
                                docker push ${REGISTRY}/${service}:latest
                            """
                        }
                    }
                }
            }
        }
        
        stage('Deploy to Kubernetes') {
            steps {
                script {
                    withKubeConfig([credentialsId: 'kubeconfig-creds']) {
                        sh """
                            helm upgrade edjo-bim ${HELM_CHART_PATH} \
                              --namespace ${KUBERNETES_NAMESPACE} \
                              --create-namespace \
                              --set global.imageTag=${BUILD_NUMBER} \
                              --wait \
                              --timeout 5m
                        """
                    }
                }
            }
        }
        
        stage('Verify Deployment') {
            steps {
                script {
                    withKubeConfig([credentialsId: 'kubeconfig-creds']) {
                        sh """
                            kubectl wait --for=condition=ready pod \
                              -l app=api-gateway-bff \
                              -n ${KUBERNETES_NAMESPACE} \
                              --timeout=120s
                        """
                    }
                }
            }
        }
    }
    
    post {
        success {
            echo 'Pipeline succeeded!'
        }
        failure {
            echo 'Pipeline failed!'
            // Send notification
        }
        always {
            cleanWs()
        }
    }
}
```

## GitHub Actions

**Location**: `.github/workflows/ci-cd.yml`

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

env:
  REGISTRY: your-registry.io/edjo-bim
  KUBERNETES_NAMESPACE: edjo-bim

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Cache Maven dependencies
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          
      - name: Build
        run: mvn clean compile
        
      - name: Test
        run: mvn test
        
      - name: Package
        run: mvn package -DskipTests
        
      - name: Upload test results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-results
          path: '**/target/surefire-reports/*.xml'
          
  build-docker:
    needs: build
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [auth-service, reports-ms, api-gateway-bff, client-performance-svc, aims-config-central]
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2
        
      - name: Login to Registry
        uses: docker/login-action@v2
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}
          
      - name: Build and push
        uses: docker/build-push-action@v4
        with:
          context: ./services/${{ matrix.service }}
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ matrix.service }}:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ matrix.service }}:latest
          cache-from: type=registry,ref=${{ env.REGISTRY }}/${{ matrix.service }}:buildcache
          cache-to: type=registry,ref=${{ env.REGISTRY }}/${{ matrix.service }}:buildcache,mode=max
          
  deploy:
    needs: build-docker
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Helm
        uses: azure/setup-helm@v3
        with:
          version: '3.12.0'
          
      - name: Configure kubectl
        uses: azure/setup-kubectl@v3
        
      - name: Deploy with Helm
        run: |
          helm upgrade edjo-bim ./helm/edjo-bim \
            --namespace ${{ env.KUBERNETES_NAMESPACE }} \
            --create-namespace \
            --set global.imageTag=${{ github.sha }} \
            --wait \
            --timeout 5m
        env:
          KUBECONFIG: ${{ secrets.KUBECONFIG }}
          
      - name: Verify deployment
        run: |
          kubectl wait --for=condition=ready pod \
            -l app=api-gateway-bff \
            -n ${{ env.KUBERNETES_NAMESPACE }} \
            --timeout=120s
        env:
          KUBECONFIG: ${{ secrets.KUBECONFIG }}
```

## Deployment Strategies

### Rolling Update (Default)

**How it works**:
- Kubernetes gradually replaces old pods with new ones
- Zero downtime if health checks pass
- Automatic rollback on failure

**Configuration**: Default Kubernetes behavior

**Pros**:
- Zero downtime
- Automatic rollback
- Resource efficient

**Cons**:
- Brief period with mixed versions
- Requires backward compatibility

### Blue-Green Deployment

**How it works**:
- Deploy new version alongside old version
- Switch traffic all at once
- Keep old version for quick rollback

**Implementation**:
```bash
# Deploy green version
helm install edjo-bim-green ./helm/edjo-bim \
  --namespace edjo-bim-green \
  --set global.imageTag=new-version

# Switch service to green
kubectl patch svc api-gateway-bff -n edjo-bim \
  -p '{"spec":{"selector":{"version":"green"}}}'

# Delete blue version
helm uninstall edjo-bim -n edjo-bim
```

**Pros**:
- Instant rollback
- No mixed versions
- Easy to test before switch

**Cons**:
- Requires double resources
- More complex setup

### Canary Deployment

**How it works**:
- Deploy new version to small percentage of traffic
- Monitor metrics
- Gradually increase traffic if healthy

**Implementation** (using Istio/Service Mesh):
```yaml
# Route 10% traffic to canary
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: api-gateway-bff
spec:
  hosts:
  - api-gateway-bff
  http:
  - match:
    - headers:
        canary:
          exact: "true"
    route:
    - destination:
        host: api-gateway-bff
        subset: canary
      weight: 100
  - route:
    - destination:
        host: api-gateway-bff
        subset: stable
      weight: 90
    - destination:
        host: api-gateway-bff
        subset: canary
      weight: 10
```

**Pros**:
- Low risk
- Gradual rollout
- Real-world testing

**Cons**:
- Requires service mesh
- More complex monitoring

## Pipeline Gates

### Quality Gates

1. **Unit Tests**: Must pass (>80% coverage)
2. **Integration Tests**: Must pass
3. **Code Quality**: SonarQube quality gate passed
4. **Security Scan**: No critical vulnerabilities

### Deployment Gates

1. **Image Build**: All images built successfully
2. **Image Push**: All images pushed to registry
3. **Helm Deploy**: Deployment succeeds
4. **Health Checks**: Pods become ready
5. **Smoke Tests**: Basic functionality verified

## Artifact Management

### Docker Images

- **Tagging Strategy**: `${BUILD_NUMBER}` or `${GIT_SHA}`
- **Latest Tag**: Always points to latest successful build
- **Registry**: Container registry (Docker Hub, ECR, GCR, ACR)

### Helm Charts

- **Versioning**: Semantic versioning (1.0.0, 1.0.1, etc.)
- **Storage**: Helm chart repository or Git
- **Publishing**: After successful build

## Monitoring Pipeline

### Metrics

- Build duration
- Test pass rate
- Deployment success rate
- Time to production

### Alerts

- Build failures
- Test failures
- Deployment failures
- Rollback events

## Best Practices

1. **Fast Feedback**: Run quick tests first, slow tests later
2. **Parallel Execution**: Build Docker images in parallel
3. **Caching**: Cache Maven dependencies and Docker layers
4. **Idempotency**: Deployments should be idempotent
5. **Rollback Plan**: Always have rollback procedure ready
6. **Environment Parity**: Keep dev/staging/prod similar
7. **Security**: Scan images for vulnerabilities
8. **Documentation**: Document deployment procedures

## Next Steps

- See [05-K8S-STEP-BY-STEP.md](05-K8S-STEP-BY-STEP.md) for manual deployment
- See [06-HELM-DEPLOYMENT.md](06-HELM-DEPLOYMENT.md) for Helm details
