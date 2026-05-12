pipeline {
    agent any

    environment {
        COMPOSE_FILE        = 'infra/docker-compose.dev.yml'
        IMAGE_NAME          = 'fint-backend'
        FRONTEND_IMAGE_NAME = 'fint-frontend'
        AI_IMAGE_NAME       = 'fint-ai'
    }

    stages {
        stage('Detect Changes') {
            steps {
                script {
                    def diffCmd = (env.gitlabMergeRequestId != null)
                        ? 'git diff --name-only origin/dev...HEAD'
                        : 'git diff --name-only HEAD~1'
                    def changes = sh(script: diffCmd, returnStdout: true).trim()

                    env.BACKEND_CHANGED  = changes.contains('backend/') ? 'true' : 'false'
                    env.FRONTEND_CHANGED = changes.contains('frontend-web/') ? 'true' : 'false'
                    env.AI_CHANGED       = changes.contains('ai/') ? 'true' : 'false'

                    if (changes.contains('Jenkinsfile') || changes.contains('infra/')) {
                        env.BACKEND_CHANGED  = 'true'
                        env.FRONTEND_CHANGED = 'true'
                        env.AI_CHANGED       = 'true'
                    }

                    env.ANY_CHANGED = (env.BACKEND_CHANGED == 'true' || env.FRONTEND_CHANGED == 'true' || env.AI_CHANGED == 'true') ? 'true' : 'false'

                    echo "Backend: ${env.BACKEND_CHANGED}, Frontend: ${env.FRONTEND_CHANGED}, AI: ${env.AI_CHANGED}"
                }
            }
        }

        // ===== CI: 변경된 영역만 테스트 =====
        // MR → 테스트 + 프로덕션 Dockerfile 빌드 검증
        // dev 머지 → 테스트만 (빌드/배포는 CD 단계에서 수행)
        stage('Test') {
            parallel {
                stage('Backend Test') {
                    when { expression { env.BACKEND_CHANGED == 'true' } }
                    steps {
                        dir('backend') {
                            sh './gradlew test --no-daemon'
                            script {
                                if (env.gitlabMergeRequestId != null) {
                                    sh './gradlew bootJar -x test --no-daemon'
                                    sh "docker build -f Dockerfile.runtime -t ${IMAGE_NAME}:ci-build-${BUILD_NUMBER} ."
                                    sh "docker rmi ${IMAGE_NAME}:ci-build-${BUILD_NUMBER} || true"
                                }
                            }
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'backend/build/test-results/test/*.xml'
                        }
                    }
                }
                stage('Frontend Build Check') {
                    when { expression { env.FRONTEND_CHANGED == 'true' } }
                    steps {
                        dir('frontend-web') {
                            sh """
                                docker build \
                                  --build-arg NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1 \
                                  --target builder \
                                  -t ${FRONTEND_IMAGE_NAME}:ci-${BUILD_NUMBER} \
                                  .
                            """
                        }
                    }
                    post {
                        always {
                            sh "docker rmi ${FRONTEND_IMAGE_NAME}:ci-${BUILD_NUMBER} || true"
                        }
                    }
                }
                stage('AI Test') {
                    when { expression { env.AI_CHANGED == 'true' } }
                    steps {
                        dir('ai') {
                            sh '''
                                docker build --no-cache --target test -t ${AI_IMAGE_NAME}:ci-${BUILD_NUMBER} .
                                docker run --rm ${AI_IMAGE_NAME}:ci-${BUILD_NUMBER} sh -c '
                                    echo "=== FILES ===" && ls /app/ && echo "---" && ls /app/app/ 2>&1 || echo "NO /app/app/ DIR"
                                    echo "=== ENV ===" && echo "PYTHONPATH=$PYTHONPATH" && echo "PATH=$PATH" && echo "CWD=$(pwd)"
                                    echo "=== SYS.PATH ===" && python -c "import sys; print(chr(10).join(sys.path))"
                                    echo "=== IMPORT TEST ===" && python -c "import app; print(app.__file__)" 2>&1 || echo "IMPORT FAILED"
                                    echo "=== RUN PYTEST ===" && python -m pytest --tb=short -q
                                '
                            '''
                            script {
                                if (env.gitlabMergeRequestId != null) {
                                    sh "docker build -t ${AI_IMAGE_NAME}:ci-build-${BUILD_NUMBER} ."
                                    sh "docker rmi ${AI_IMAGE_NAME}:ci-build-${BUILD_NUMBER} || true"
                                }
                            }
                        }
                    }
                    post {
                        always {
                            sh "docker rmi ${AI_IMAGE_NAME}:ci-${BUILD_NUMBER} || true"
                        }
                    }
                }
            }
            post {
                success {
                    script {
                        if (env.gitlabMergeRequestId != null) {
                            updateGitlabCommitStatus name: 'jenkins-ci', state: 'success'
                        }
                    }
                }
                failure {
                    script {
                        if (env.gitlabMergeRequestId != null) {
                            updateGitlabCommitStatus name: 'jenkins-ci', state: 'failed'
                        }
                    }
                }
            }
        }

        // ===== CD: 변경된 영역만 빌드 + 배포 =====
        stage('Build JAR') {
            when {
                expression { env.gitlabMergeRequestId == null && env.BACKEND_CHANGED == 'true' }
            }
            steps {
                dir('backend') {
                    sh './gradlew bootJar -x test --no-daemon'
                }
            }
        }

        stage('Build Image') {
            when {
                expression { env.gitlabMergeRequestId == null && env.BACKEND_CHANGED == 'true' }
            }
            steps {
                dir('backend') {
                    sh "docker build -f Dockerfile.runtime -t ${IMAGE_NAME}:${BUILD_NUMBER} -t ${IMAGE_NAME}:latest ."
                }
            }
        }

        stage('Build AI Image') {
            when {
                expression { env.gitlabMergeRequestId == null && env.AI_CHANGED == 'true' }
            }
            steps {
                dir('ai') {
                    sh "docker build -t ${AI_IMAGE_NAME}:${BUILD_NUMBER} -t ${AI_IMAGE_NAME}:latest ."
                }
            }
        }

        stage('Build Frontend Image') {
            when {
                expression { env.gitlabMergeRequestId == null && env.FRONTEND_CHANGED == 'true' }
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
                expression { env.gitlabMergeRequestId == null && env.ANY_CHANGED == 'true' }
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
                expression { env.gitlabMergeRequestId == null && env.ANY_CHANGED == 'true' }
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
