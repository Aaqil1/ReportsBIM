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
                echo 'Building application...'
                sh 'mvn clean compile'
            }
        }
        
        stage('Test') {
            steps {
                echo 'Running tests...'
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
                echo 'Packaging application...'
                sh 'mvn package -DskipTests'
            }
        }
        
        stage('Build Docker Images') {
            steps {
                script {
                    def services = ['auth-service', 'reports-ms', 'api-gateway-bff', 
                                   'client-performance-svc', 'aims-config-central']
                    services.each { service ->
                        echo "Building Docker image for ${service}..."
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
                            echo "Pushing Docker image for ${service}..."
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
                        echo 'Deploying to Kubernetes with Helm...'
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
                        echo 'Verifying deployment...'
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
        }
        always {
            cleanWs()
        }
    }
}
