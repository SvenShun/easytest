SHELL := /bin/bash
ROOT  := $(shell pwd)

.PHONY: help setup build test e2e up up-mw down infra-down web fmt clean

help:
	@echo "TestHub V1"
	@echo "  make setup   install maven/python/web dependencies"
	@echo "  make build   build all Java services + web bundle"
	@echo "  make test    run Java tests + Python tests"
	@echo "  make up      start core, ui-execution, device-agent, ai-runtime (H2/memory)"
	@echo "  make up-mw   start with Docker MySQL/Redis/MinIO (run make infra first)"
	@echo "  make infra   start middleware containers (mysql/redis/minio/kafka)"
	@echo "  make web     start web console (5173)"
	@echo "  make e2e     run the end-to-end acceptance script"
	@echo "  make down    stop local services"
	@echo "  make clean   remove build artifacts and runtime data"

setup:
	./scripts/setup.sh

build:
	./scripts/mvn.sh -q -DskipTests package
	cd web && npm run build

test:
	./scripts/mvn.sh -q test
	cd agents/ai-runtime && .venv/bin/pytest -q

up:
	./scripts/dev-up.sh

infra:
	./scripts/infra-up.sh

up-mw:
	TESTHUB_INFRA=docker ./scripts/dev-up.sh

infra-down:
	./scripts/infra-down.sh

web:
	cd web && npm run dev

e2e:
	./scripts/e2e.sh

down:
	./scripts/dev-down.sh

clean:
	./scripts/mvn.sh -q clean
	rm -rf data logs web/dist
