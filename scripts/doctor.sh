#!/usr/bin/env bash
set -euo pipefail

PASS="\033[32mPASS\033[0m"
FAIL="\033[31mFAIL\033[0m"
WARN="\033[33mWARN\033[0m"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
errors=0

print_result() {
    printf "  %-45s [%b]\n" "$1" "$2"
}

echo ""
echo "=== F!NT Development Environment Check ==="
echo ""

# 1. Docker daemon
if docker info > /dev/null 2>&1; then
    print_result "Docker daemon running" "$PASS"
else
    print_result "Docker daemon running" "$FAIL"
    echo "    -> Docker Desktop을 실행하세요."
    errors=$((errors + 1))
fi

# 2. Docker Compose v2
if docker compose version > /dev/null 2>&1; then
    compose_ver=$(docker compose version --short 2>/dev/null || echo "unknown")
    print_result "Docker Compose v2 ($compose_ver)" "$PASS"
else
    print_result "Docker Compose v2" "$FAIL"
    echo "    -> Docker Desktop 최신 버전을 설치하세요."
    errors=$((errors + 1))
fi

# 3. Port checks
check_port() {
    local port=$1
    local name=$2
    local in_use=false

    if command -v ss > /dev/null 2>&1; then
        ss -tlnp 2>/dev/null | grep -q ":${port} " && in_use=true
    elif command -v netstat > /dev/null 2>&1; then
        netstat -an 2>/dev/null | grep -q ":${port}.*LISTEN" && in_use=true
    fi

    if [ "$in_use" = true ]; then
        print_result "Port $port ($name) free" "$FAIL"
        echo "    -> 포트 ${port}이 이미 사용 중입니다. 충돌하는 프로세스를 종료하세요."
        errors=$((errors + 1))
    else
        print_result "Port $port ($name) free" "$PASS"
    fi
}

check_port 5432 "PostgreSQL"
check_port 6379 "Redis"
check_port 8080 "Spring Boot"

# 4. JDK 21
if java -version 2>&1 | grep -q '"21'; then
    java_ver=$(java -version 2>&1 | head -1)
    print_result "JDK 21 ($java_ver)" "$PASS"
else
    print_result "JDK 21 installed" "$WARN"
    echo "    -> JDK 21이 없지만, Gradle Foojay 플러그인이 자동 다운로드합니다."
fi

# 5. .env files
if [ ! -f "$ROOT_DIR/infra/.env" ]; then
    cp "$ROOT_DIR/infra/.env.example" "$ROOT_DIR/infra/.env"
    print_result "infra/.env" "$WARN"
    echo "    -> .env.example에서 자동 복사했습니다."
else
    print_result "infra/.env exists" "$PASS"
fi

if [ ! -f "$ROOT_DIR/backend/.env" ]; then
    cp "$ROOT_DIR/backend/.env.example" "$ROOT_DIR/backend/.env"
    print_result "backend/.env" "$WARN"
    echo "    -> .env.example에서 자동 복사했습니다."
else
    print_result "backend/.env exists" "$PASS"
fi

# 6. Git autocrlf (Windows)
if [ "$(git config core.autocrlf 2>/dev/null)" = "true" ]; then
    print_result "Git core.autocrlf" "$WARN"
    echo "    -> core.autocrlf=true는 .gitattributes와 충돌할 수 있습니다."
    echo "    -> 실행: git config --global core.autocrlf input"
else
    print_result "Git core.autocrlf (not true)" "$PASS"
fi

echo ""
if [ $errors -gt 0 ]; then
    printf "\033[31m%d개 항목이 실패했습니다. 위 안내를 확인하세요.\033[0m\n" "$errors"
    exit 1
else
    printf "\033[32m모든 검사를 통과했습니다. make up으로 시작하세요.\033[0m\n"
fi
echo ""
