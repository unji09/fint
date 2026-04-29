pipeline {
    agent any

    environment {
        COMPOSE_FILE = 'infra/docker-compose.dev.yml'
        IMAGE_NAME   = 'fint-backend'
    }

    stages {
        // ===== CI: MR 열릴 때 테스트 =====
        stage('Test') {
            when {
                expression { env.gitlabMergeRequestId != null }
            }
            steps {
                dir('backend') {
                    sh './gradlew test --no-daemon'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/build/test-results/test/*.xml'
                }
                success {
                    updateGitlabCommitStatus name: 'jenkins-ci', state: 'success'
                }
                failure {
                    updateGitlabCommitStatus name: 'jenkins-ci', state: 'failed'
                }
            }
        }

        // ===== CD: dev 머지 시 배포 =====
        stage('Build JAR') {
            when {
                expression { env.gitlabMergeRequestId == null }
            }
            steps {
                dir('backend') {
                    sh './gradlew bootJar -x test --no-daemon'
                }
            }
        }

        stage('Build Image') {
            when {
                expression { env.gitlabMergeRequestId == null }
            }
            steps {
                dir('backend') {
                    sh "docker build -f Dockerfile.runtime -t ${IMAGE_NAME}:${BUILD_NUMBER} -t ${IMAGE_NAME}:latest ."
                }
            }
        }

        stage('Deploy') {
            when {
                expression { env.gitlabMergeRequestId == null }
            }
            steps {
                sh 'cd ${DEPLOY_DIR} && docker compose -f infra/docker-compose.dev.yml --env-file infra/.env.dev up -d'
            }
        }

        stage('Health Check') {
            when {
                expression { env.gitlabMergeRequestId == null }
            }
            steps {
                retry(30) {
                    sleep 10
                    sh 'curl -sfk --connect-timeout 5 --max-time 10 https://localhost/actuator/health'
                }
            }
        }
    }

    post {
        failure {
            echo 'Pipeline failed. Check logs above.'
        }
    }
}
