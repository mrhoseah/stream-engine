# stream-engine

**Enterprise-grade C++ streaming service** built with **Red5 Pro Core SDK**, designed for sibling project integration (e.g. **recastly**).

## Features

- **StreamingService** – High-level API for publish/subscribe via Red5
- **Config & callbacks** – Typed config, event callbacks (on_connected, on_error, etc.)
- **Migration-aware startup** – Retry attempts and ordered fallback hosts
- **Playback discovery** – Server endpoint for resolving the active playback URL
- **Result/ErrorCode** – Explicit error handling
- **recastly integration** – `add_subdirectory` or `find_package`

See [RECASTLY_INTEGRATION.md](RECASTLY_INTEGRATION.md) for integration details.

## Project Structure

```
stream-engine/
├── CMakeLists.txt
├── libs/stream-engine/      # Core library
│   ├── include/stream-engine/
│   │   ├── types.hpp        # ErrorCode, Result, ServiceStatus
│   │   ├── config.hpp       # StreamingServiceConfig
│   │   ├── callbacks.hpp    # Event callbacks
│   │   ├── streaming_service.hpp  # Main API
│   │   └── stream_engine.hpp      # Low-level engine
│   └── src/
├── apps/stream-client/      # Example app
├── tests/unit/
└── cmake/                   # Install config for find_package
```

## Prerequisites

- **CMake** 3.21+
- **C++17** compiler (MSVC, GCC, Clang)
- **Windows:** Visual Studio (latest)
- **Red5 Core SDK:** [Download](https://account.red5.net/) and extract to `C:\Users\<USER>\Red5Core\<distribution>\`

## Build

### Without Red5 SDK

The project builds without Red5. Red5 integration is enabled when the SDK is found.

```powershell
mkdir build
cd build
cmake ..
cmake --build . --config Release
```

### With Red5 Core SDK

1. Download the Red5 Pro Core SDK and unzip to `C:\Users\<USER>\Red5Core\<distribution>\`
2. Configure with the SDK cmake path:

```powershell
cmake -DCMAKE_PREFIX_PATH="C:/Users/<USER>/Red5Core/<distribution>/cmake" ..
cmake --build . --config Release
```

### With vcpkg

```powershell
cmake -DCMAKE_TOOLCHAIN_FILE="$env:VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake" ..
cmake --build . --config Release
```

## Tests

```powershell
cmake --build build --config Release --target test_stream_engine
ctest --test-dir build -C Release
```

## Red5 Core SDK Modules

- **r5core** – Server connection, IClient, RTSP/WebRTC
- **r5common** – Shared utilities, logger, media structures
- **r5device** – Camera, microphone, speakers
- **r5ffmpeg** – FFmpeg encoder/decoder
- **r5net** – HTTP, WebSocket
- **r5webrtc** – WebRTC signaling and connections

See [Red5 Core SDK docs](https://www.red5.net/docs/red5-pro/development/sdks/red5-core-sdk/red5-core-sdk-overview/).

## Recastly Integration

Build with vcpkg to enable `stream-engine-server` (requires `cpp-httplib`, `nlohmann-json`):

```powershell
cmake -DCMAKE_TOOLCHAIN_FILE="$env:VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake" ..
cmake --build . --config Release --target stream-engine-server
```

Run the server (default port 9090):

```powershell
./build/Release/stream-engine-server.exe [port]
```

Recastly config (add to `stream` section):

```yaml
stream:
  stream_engine_service:
    stream_engine_enabled: true
    stream_engine_base_url: "http://localhost:9090"
    stream_engine_timeout: 30
```

When enabled, Recastly delegates all streaming to stream-engine-server via `/api/v1/stream-engine`.

### Enterprise-Grade Features

- **Graceful shutdown** – SIGTERM/SIGINT drains sessions, then exits
- **Health** – `/health`, `/health/live`, `/health/ready` (liveness vs readiness)
- **Metrics** – `/metrics` (Prometheus format: `stream_engine_sessions_active`, `stream_engine_requests_total`)
- **Config** – `PORT`, `MAX_SESSIONS`, `MAX_REQUEST_BODY_BYTES`, `LOG_LEVEL`, `SHUTDOWN_TIMEOUT_SEC`, `METRICS_ENABLED`, `GRACEFUL_SHUTDOWN`, `ENVIRONMENT`, `RECASTLY_BASE_URL`, `RECASTLY_STREAM_ENGINE_SECRET`, `PLAYBACK_BASE_URL`, `WEBHOOK_RETRY_ATTEMPTS`, `WEBHOOK_RETRY_BACKOFF_MS`
- **Validation** – stream_id format, max body size, max sessions (returns 503 when at capacity)
- **Structured errors** – `{"ok":false,"error":{"code":"...","message":"..."},"request_id":"..."}`

### Runtime contracts

`StreamingServiceConfig::migration_hosts` is tried in order after the primary
host. `reconnect_attempts` controls retries per host and
`reconnect_backoff_ms` controls the delay between attempts. The engine passes
the selected host, port, timeout, and auth token through its transport boundary.

When `PLAYBACK_BASE_URL` is configured, clients can call
`GET /api/v1/sessions/:id/playback` to receive the playback URL for a live
session. Graceful shutdown sends `stream.ended` webhooks for drained sessions.

In `ENVIRONMENT=production`, Recastly URL and secret configuration is required
and stream-key validation fails closed. Webhooks include a stable event ID and
are retried with bounded backoff; the receiving API must deduplicate by
`Idempotency-Key`. Durable delivery across process failure still requires a
shared outbox or queue.

Session start requests may provide an `Idempotency-Key` header. Retries with
the same key on this engine node return the existing session without creating
another media session or duplicate `stream.started` webhook. Cross-node start
deduplication still requires a shared session registry.

When the Red5 SDK compile flag is enabled but its media adapter is not wired,
the engine fails initialization/start instead of reporting a false live state.

The Red5 SDK is optional at build time. This checkout does not contain the
proprietary SDK headers, so the SDK-specific media transport still needs to be
provided by the Red5 SDK adapter in the target deployment; the public engine
API and migration lifecycle are SDK-independent.
