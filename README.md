
# Streaming Engine

**Enterprise-grade Java streaming service** built with **Spring Boot** and **Red5 integration**. Designed for scalable, production-grade streaming and sibling project integration (e.g., **recastly**).


## Features

- **Spring Boot microservice** – RESTful API for streaming session management
- **Red5 integration** – HTTP-based control and monitoring
- **Redis support** – Distributed session and nonce management
- **Prometheus metrics** – Built-in actuator endpoints
- **Resilience4j** – Circuit breaker and retry for external calls
- **OpenAPI docs** – Interactive API documentation
- **Enterprise features** – Health checks, graceful shutdown, structured errors

See [RECASTLY_INTEGRATION.md](RECASTLY_INTEGRATION.md) for integration details.


## Project Structure

```
stream-engine/
├── src/
│   ├── main/
│   │   ├── java/com/streaming/engine/   # Java source code
│   │   └── resources/                   # Application configs
│   └── test/                            # Unit and integration tests
├── pom.xml                              # Maven build file
├── deploy/                              # Deployment scripts and configs
├── docs/                                # Documentation
└── README.md
```


## Prerequisites

- **Java 25** (or compatible)
- **Maven 3.8+**
- **Redis** (for distributed session/nonce support)

## Build & Run

Build the project:

```sh
mvn clean package
```

Run the application:

```sh
mvn spring-boot:run
```

Or run the packaged JAR:

```sh
java -jar target/streaming-engine-*.jar
```


## Testing

Run all tests:

```sh
mvn test
```


## Red5 Integration

The application integrates with Red5 via HTTP for streaming session management. See the [Red5 documentation](https://www.red5.net/docs/red5-pro/development/sdks/red5-core-sdk/red5-core-sdk-overview/) for more details.

## Recastly Integration

When enabled, Recastly delegates all streaming to the streaming-engine service via `/api/v1/stream-engine`.


### Enterprise-Grade Features

- **Graceful shutdown** – SIGTERM/SIGINT drains sessions, then exits
- **Health endpoints** – `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
- **Metrics** – `/actuator/metrics` (Prometheus format)
- **Config** – via `application.yml` or environment variables
- **Validation** – stream_id format, max body size, max sessions (returns 503 when at capacity)
- **Structured errors** – `{"ok":false,"error":{"code":"...","message":"..."},"request_id":"..."}`
