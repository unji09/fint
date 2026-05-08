pipeline {
    agent any

    environment {
        COMPOSE_FILE        = 'infra/docker-compose.dev.yml'
        IMAGE_NAME          = 'fint-backend'
        FRONTEND_IMAGE_NAME = 'fint-frontend'
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

        stage('Build Frontend Image') {
            when {
                expression { env.gitlabMergeRequestId == null }
            }
            steps {
                // .env.dev 에서 NEXT_PUBLIC_API_URL 을 읽어 --build-arg 로 주입
                // (NEXT_PUBLIC_* 는 빌드 시점에 번들에 박히기 때문)
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
                    cd ${DEPLOY_DIR}
                    if [ -f .env.dev ]; then
                        ENV_FILE=.env.dev
                    elif [ -f infra/.env.dev ]; then
                        ENV_FILE=infra/.env.dev
                    else
                        echo "ERROR: .env.dev not found"
                        exit 1
                    fi
                    docker compose -f infra/docker-compose.dev.yml --env-file "$ENV_FILE" up -d
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
                // 프론트(Next.js standalone) — / 는 /playground 로 307 리다이렉트되므로
                // 200/307/308 모두 healthy 로 간주
                retry(30) {
                    sleep 5
                    sh 'curl -sk --connect-timeout 5 --max-time 10 -o /dev/null -w "%{http_code}\\n" https://localhost/ | grep -qE "^(200|307|308)$"'
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
