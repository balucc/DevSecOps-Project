pipeline {
    agent any
    tools {
        jdk 'jdk17'
        nodejs 'node16'
    }
    environment {
        SCANNER_HOME = tool 'sonar-scanner'
        DOCKER_REGISTRY = 'bccdevopsite.azurecr.io'
        DOCKER_REPO = 'netflix'
        DOCKER_IMAGE_TAG = "${DOCKER_REGISTRY}/${DOCKER_REPO}:${BUILD_NUMBER}"
        ACR_URL = 'bccdevopsite.azurecr.io'
        ACR_CREDENTIALS_ID = 'ACR-ID'
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
                    waitForQualityGate abortPipeline: True, credentialsId: 'Sonar-token'
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
                dependencyCheckPublisher pattern: '**//*dependency-check-report.xml'
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
                    withDockerRegistry(credentialsId: "${ACR_CREDENTIALS_ID}", url: "https://${ACR_URL}") {
                        sh "docker build --build-arg TMDB_V3_API_KEY=c25230d950fe8c1f6aac8d96d863b07cgi -t netflix ."
                        sh "docker tag netflix bccdevopsite.azurecr.io/netflix:${BUILD_NUMBER} "
                        sh "docker push bccdevopsite.azurecr.io/netflix:${BUILD_NUMBER}"
        }
    }
}}

        stage('Trivy Image Scan') {
            steps {
                sh 'trivy image bccdevopsite.azurecr.io/netflix:${BUILD_NUMBER} > trivyimage.txt'
            }
        }
        stage('Edit Deployment File') {
            steps {
                script {
                    sh "sed -i 's|image: mydevopsregistry.azurecr.io/netflix:.*|image: bccdevopsite.azurecr.io/netflix:${BUILD_NUMBER}|' deployment.yml"
                }
            }
        }
        stage('Deploy to AKS') {
            steps {
                sh 'kubectl apply -f deployment.yml'
                sh 'kubectl apply -f service.yaml'
            }
        }    
    }
    /*post {
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
    }*/
}
