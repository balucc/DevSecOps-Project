pipeline {
    agent any
    tools {
        jdk 'jdk17'
        nodejs 'node16'
    }
    environment {
        SCANNER_HOME = tool 'sonar-scanner'
        DOCKER_BUILDKIT = "1"
    }
    stages {
        stage('clean ws') {
            steps {
                cleanWs()
            }    
        }
        stage('Git Checkout') {
            steps {
                git branch: 'main', credentialsId: 'gitcred', url: 'https://github.com/balucc/DevSecOps-Project.git'
            }
        }
        stage('Sonarqube Analysis') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh '''$SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=Netflix \
                    -Dsonar.projectKey=Netflix'''
                }
            }
        }
        stage('Quality Gate') {
            steps {
                script {
                    waitForQualityGate abortPipeline: false, credentialsId: 'Sonar-token'
                }
            }
        }
        stage('Install Dependencies') {
            steps {
                sh "npm install"
            }
        }
        stage('OWASP SCAN') {
            steps {
                dependencyCheck additionalArguments: '--nvdApiKey fb5da539-fea3-4307-8d0a-54c2fe097dd7 --scan ./ --disableYarnAudit --disableNodeAudit', odcInstallation: 'Dp-check'
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }
        stage('Trivy FS Scan') {
            steps {
                sh "trivy fs . > trivyfs.txt"
            }
        }
        stage('Docker Build & Push') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'Acr-ID', url: 'bcccontainerreistry.azurecr.io') {
                        sh 'az acr login --name BccContainerReistry'
                        sh 'docker run --rm --privileged tonistiigi/binfmt --install all'
                        sh 'docker buildx create --name mybuilder --driver docker-container --use'
                        sh 'docker buildx inspect --bootstrap'
                        sh 'docker buildx build --platform linux/amd64,linux/arm64 --build-arg TMDB_V3_API_KEY=c25230d950fe8c1f6aac8d96d863b07c -t bcccontainerreistry.azurecr.io/netflix/$BUILD_NUMBER --push .'
                    }
                }
            }
        }
        stage('Trivy Image Scan') {
            steps {
                sh 'trivy image bcccontainerreistry.azurecr.io/netflix/$BUILD_NUMBER > trivyimage.txt'
            }
        }
        stage('Edit Deployment File') {
            steps {
                script {
                    sh "sed -i 's|image: bcccontainerreistry.azurecr.io/netflix/*|image: bcccontainerreistry.azurecr.io/netflix/${BUILD_NUMBER}|' deployment.yaml"
                }
            }
        }
        stage('Deploy to AKS') {
            steps {
                sh 'kubectl apply -f deployment.yaml'
                sh 'kubectl apply -f service.yaml'
                sh 'kubectl apply -f ingress.yaml'
            }
        }    
    }
    post {
        always {
            emailext(
                attachLog: true,
                subject: "${currentBuild.result}",
                body: """<p>Project: ${env.JOB_NAME}</p>
                        <p>Build Number: ${env.BUILD_NUMBER}</p>
                        <p>URL: <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>""",
                to: 'balucc@gnrgy.com',
                attachmentsPattern: 'trivyfs.txt,trivyimage.txt'
            )
        }
    }
}
