.PHONY: all build run run-jar dev dev-metrics dev-jar test clean deploy-preflight deploy-install deploy-update check-maven

# Default target
all: build

JAR ?= target/streaming-engine-0.1.0-SNAPSHOT.jar
ifeq ($(OS),Windows_NT)
MVN ?= .\mvnw.cmd
else
MVN ?= ./mvnw
endif

check-maven:
ifeq ($(OS),Windows_NT)
	@if not exist "$(MVN)" (echo Maven command '$(MVN)' not found. Ensure Maven Wrapper files exist in repo root. && exit 1)
else
	@command -v $(MVN) >/dev/null 2>&1 || [ -f "$(MVN)" ] || (echo "Maven command '$(MVN)' not found. Install Maven or run with MVN=./mvnw." && exit 1)
endif

# Build the application
build: check-maven
	$(MVN) clean package

# Run the application (builds first)
run: build
	$(MVN) spring-boot:run

# Run packaged jar directly (clean Ctrl+C on Windows)
run-jar: build
	java -jar $(JAR)

# Run without rebuild (fast iteration)
dev: check-maven
	$(MVN) spring-boot:run

# Run with dev profile (metrics/prometheus exposed)
dev-metrics: check-maven
	$(MVN) spring-boot:run -Dspring-boot.run.profiles=dev

# Run jar with dev profile (no mvnw/cmd prompt on Ctrl+C)
dev-jar: build
	java -Dspring.profiles.active=dev -jar $(JAR)

# Run tests
test: check-maven
	$(MVN) test

# Clean build artifacts
clean: check-maven
	$(MVN) clean

# Validate Linux deployment prerequisites
deploy-preflight:
	sudo bash deploy/preflight.sh

# Install systemd unit/env and optional jar
deploy-install:
	sudo bash deploy/install.sh $(JAR)

# Roll out new jar with health-check rollback
deploy-update:
	sudo bash deploy/update.sh $(JAR)
