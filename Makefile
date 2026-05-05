.PHONY: doctor up down backend logs clean status monitoring-up monitoring-down ai ai-test ai-lint ai-format ai-docker

SHELL := /usr/bin/env bash
ROOT_DIR := $(shell pwd)
ENV_FILE := $(ROOT_DIR)/.env.local

# --- Local Development ---

doctor:
	@bash scripts/doctor.sh

up:
	@test -f $(ENV_FILE) || cp $(ROOT_DIR)/.env.local.example $(ENV_FILE)
	@cd infra && docker compose --env-file $(ENV_FILE) up -d
	@echo "Waiting for containers to be healthy..."
	@cd infra && timeout 60 bash -c 'until docker compose ps --format json 2>/dev/null | grep -q healthy; do sleep 2; done' 2>/dev/null || \
		cd infra && for i in $$(seq 1 30); do docker compose ps 2>/dev/null | grep -q healthy && break || sleep 2; done
	@cd infra && docker compose ps
	@echo ""
	@echo "PostgreSQL: localhost:5432 (fint/fint/fint_local)"
	@echo "Redis:      localhost:6379"

down:
	@cd infra && docker compose down

backend:
	@test -f $(ENV_FILE) || cp $(ROOT_DIR)/.env.local.example $(ENV_FILE)
	cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'

logs:
	@cd infra && docker compose logs -f

clean:
	@cd infra && docker compose down -v
	@echo "Volumes removed. Database reset complete."

status:
	@cd infra && docker compose ps
	@echo ""
	@curl -sf http://localhost:8080/actuator/health 2>/dev/null && echo "" || echo "Spring Boot: not running"
	@curl -sf http://localhost:8000/health 2>/dev/null && echo "" || echo "FastAPI AI:  not running"

# --- Monitoring ---

monitoring-up:
	@test -f $(ENV_FILE) || cp $(ROOT_DIR)/.env.local.example $(ENV_FILE)
	@cd infra/monitoring && docker compose --env-file $(ENV_FILE) up -d

monitoring-down:
	@cd infra/monitoring && docker compose down

# --- AI Service ---

ai:
	cd ai && uv run uvicorn app.main:create_app --factory --reload --port 8000

ai-test:
	cd ai && uv run pytest -v

ai-lint:
	cd ai && uv run ruff check . && uv run ruff format --check .

ai-format:
	cd ai && uv run ruff format .

ai-docker:
	docker build -t fint-ai:local ./ai
