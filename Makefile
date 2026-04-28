.PHONY: doctor up down backend logs clean status monitoring-up monitoring-down

SHELL := /usr/bin/env bash
ROOT_DIR := $(shell pwd)

# --- Local Development ---

doctor:
	@bash scripts/doctor.sh

up:
	@test -f infra/.env || cp infra/.env.example infra/.env
	@cd infra && docker compose up -d
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
	@test -f backend/.env || cp backend/.env.example backend/.env
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

# --- Monitoring ---

monitoring-up:
	@test -f infra/monitoring/.env || cp infra/monitoring/.env.example infra/monitoring/.env
	@cd infra/monitoring && docker compose up -d

monitoring-down:
	@cd infra/monitoring && docker compose down
