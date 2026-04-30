#!/usr/bin/env bash
set -euo pipefail

# F!NT EC2 배포 스크립트
# 사용법: bash scripts/deploy.sh [BUILD_TAG]
# 예시:   bash scripts/deploy.sh 42       -> fint-backend:42
#         bash scripts/deploy.sh           -> fint-backend:latest

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_TAG="${1:-latest}"
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.dev.yml"
ENV_FILE="$ROOT_DIR/.env.dev"

echo "=== F!NT Deploy (tag: $BUILD_TAG) ==="

# 1. 환경변수 파일 확인
if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: $ENV_FILE 파일이 없습니다."
    echo "  cp .env.dev.example .env.dev 후 값을 채우세요."
    exit 1
fi

# 2. Spring Boot JAR 빌드
echo "[1/4] Building JAR..."
cd "$ROOT_DIR/backend"
./gradlew bootJar -x test --no-daemon

# 3. Docker 이미지 빌드
echo "[2/4] Building Docker image (fint-backend:$BUILD_TAG)..."
docker build -t "fint-backend:$BUILD_TAG" -t fint-backend:latest "$ROOT_DIR/backend"

# 4. Compose 배포
echo "[3/4] Deploying containers..."
cd "$ROOT_DIR/infra"
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d

# 5. 헬스체크 대기
echo "[4/4] Waiting for health check..."
for i in $(seq 1 30); do
    if curl -sf http://localhost/actuator/health > /dev/null 2>&1; then
        echo ""
        echo "=== Deploy SUCCESS ==="
        docker compose -f docker-compose.dev.yml ps
        exit 0
    fi
    printf "."
    sleep 2
done

echo ""
echo "=== Deploy WARNING: Health check timeout ==="
docker compose -f docker-compose.dev.yml ps
docker compose -f docker-compose.dev.yml logs --tail=20 spring
exit 1
