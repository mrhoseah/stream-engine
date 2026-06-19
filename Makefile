.PHONY: build test package dev run clean

MVN ?= ./mvnw
STREAM_ENGINE_PORT ?= 8085

build: ## Compile and package the JAR
	$(MVN) -q -DskipTests package

test: ## Run unit tests
	$(MVN) -q test

package: build ## Alias for build

dev: run ## Run locally (Spring Boot dev profile)

run: ## Run locally (Spring Boot dev profile)
	STREAM_ENGINE_PORT=$(STREAM_ENGINE_PORT) $(MVN) -q spring-boot:run -Dspring-boot.run.profiles=dev

clean: ## Remove build artifacts
	$(MVN) -q clean

help: ## Show targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
