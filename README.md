# Streaming Engine

**Enterprise-grade Java streaming service** built with **Spring Boot** and **Ant Media Server (AMS)**. Designed for production streaming and sibling project integration (e.g., **Recastly**).

## Features

- **Spring Boot microservice** – RESTful API for streaming session management
- **Ant Media Server** – broadcast create/stop via AMS REST API
- **RTMP simulcast** – optional FFmpeg relay to YouTube/Twitch (see `docs/rtmp-relay.md`)
- **Kafka analytics** – lifecycle events on `stream-lifecycle-events`
- **Recastly integration** – validate-key webhooks, shared-secret inbound auth
- **Prometheus metrics** – actuator endpoints
- **Resilience4j** – circuit breaker and retry for outbound Recastly/AMS calls

See [RECASTLY_INTEGRATION.md](RECASTLY_INTEGRATION.md) for integration details.

## Prerequisites

- **Java 21**
- **Maven 3.8+**
- **Ant Media Server** (or `AMS_STUB_ENABLED=true` for local/CI)
- **Kafka** (optional; for analytics events)

## Build & Run

```sh
mvn clean package
AMS_STUB_ENABLED=true RECASTLY_ENABLED=false ./mvnw spring-boot:run
```

## Testing

```sh
mvn test
```

## Analytics stack (Kafka + Flink)

From this repo with sibling `flink-server`:

```sh
docker compose -f docker-compose.yml -f docker-compose.analytics.yml up --build
```

## Enterprise features

- Graceful shutdown, health/readiness probes, structured errors
- Inbound HMAC/signature auth for Recastly API calls
