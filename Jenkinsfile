pipeline {
    agent any

    environment {
        COMPOSE_FILE        = 'infra/docker-compose.dev.yml'
        IMAGE_NAME          = 'fint-backend'
        FRONTEND_IMAGE_NAME = 'fint-frontend'
        AI_IMAGE_NAME       = 'fint-ai'
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

        stage('Build AI Image') {
            when {
                expression { env.gitlabMergeRequestId == null }
            }
            steps {
                dir('ai') {
                    sh "docker build -t ${AI_IMAGE_NAME}:${BUILD_NUMBER} -t ${AI_IMAGE_NAME}:latest ."
                }
            }
        }

        stage('Build Frontend Image') {
            when {
                expression { env.gitlabMergeRequestId == null }
            }
            steps {
                sh '''
                    if [ -f "${DEPLOY_DIR}/.env.dev" ]; then
                        ENV_FILE="${DEPLOY_DIR}/.env.dev"
                    elif [ -f "${DEPLOY_DIR}/infra/.env.dev" ]; then
                        ENV_FILE="${DEPLOY_DIR}/infra/.env.dev"
                    else
                        echo "ERROR: .env.dev not found in DEPLOY_DIR"
                        exit 1
                    fi

                    NEXT_PUBLIC_API_URL=$(grep -E '^NEXT_PUBLIC_API_URL=' "$ENV_FILE" | head -n1 | cut -d= -f2-)
                    if [ -z "$NEXT_PUBLIC_API_URL" ]; then
                        echo "ERROR: NEXT_PUBLIC_API_URL is empty or missing in $ENV_FILE"
                        exit 1
                    fi

                    cd frontend-web
                    docker build \
                      --build-arg NEXT_PUBLIC_API_URL="$NEXT_PUBLIC_API_URL" \
                      -t ${FRONTEND_IMAGE_NAME}:${BUILD_NUMBER} \
                      -t ${FRONTEND_IMAGE_NAME}:latest \
                      .
                '''
            }
        }

        stage('Deploy') {
            when {
                expression { env.gitlabMergeRequestId == null }
            }
            steps {
                sh '''
                    cp "${DEPLOY_DIR}/.env.dev" "$WORKSPACE/.env.dev"
                    docker compose -f "$WORKSPACE/infra/docker-compose.dev.yml" --env-file "$WORKSPACE/.env.dev" up -d
                    docker restart fint-nginx
                '''
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
                retry(30) {
                    sleep 5
                    sh 'curl -sk --connect-timeout 5 --max-time 10 -o /dev/null -w "%{http_code}\\n" https://localhost/ | grep -qE "^(200|307|308)$"'
                }
                retry(10) {
                    sleep 5
                    sh 'docker exec fint-ai curl -sf --connect-timeout 3 --max-time 5 http://localhost:8000/health'
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
