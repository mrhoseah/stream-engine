# Integrating stream-engine with Recastly

stream-engine is a **Java Spring Boot** service that manages Ant Media Server sessions, optional FFmpeg RTMP simulcast, Kafka lifecycle events, and Recastly webhooks.

## Architecture

```
Recastly (Go)  ←→  stream-engine (Java)  ←→  Ant Media Server
                         ↓
                    Kafka (stream-lifecycle-events)
                         ↓
                    flink-server (stream lifecycle analytics)
```

Recastly enables the integration with `STREAM_ENGINE_ENABLED=true` and proxies session start/stop to stream-engine's REST API. stream-engine validates stream keys against Recastly and posts `stream.started` / `stream.ended` webhooks back. Lifecycle events are also published to Kafka for Flink analytics.

## Recastly configuration

| Env var | Description |
|---------|-------------|
| `STREAM_ENGINE_ENABLED` | `true` to register stream-engine as the default streaming backend |
| `STREAM_ENGINE_BASE_URL` | stream-engine base URL (e.g. `http://localhost:8085`) |
| `STREAM_ENGINE_SHARED_SECRET` | Shared secret for inbound API auth (`X-Stream-Engine-Secret`) |
| `STREAM_ENGINE_INGEST_HOST` | RTMP ingest host shown to encoders |
| `STREAM_ENGINE_PLAYBACK_BASE_URL` | HLS/WebRTC playback base URL |

## stream-engine configuration

| Env var | Description |
|---------|-------------|
| `RECASTLY_ENABLED` | `true` to validate keys and send webhooks |
| `RECASTLY_BASE_URL` | Recastly API base (e.g. `http://localhost:8080/api/v1`) |
| `RECASTLY_SHARED_SECRET` | Must match `STREAM_ENGINE_SHARED_SECRET` in Recastly |
| `AMS_BASE_URL` | Ant Media REST API base |
| `AMS_STUB_ENABLED` | `true` for local/CI without a real AMS instance |

See `docs/rtmp-relay.md` for FFmpeg simulcast settings.

## API contract

### Start session

```
POST /api/v1/sessions
X-Stream-Engine-Secret: <shared-secret>
Content-Type: application/json

{
  "stream_id": "my-stream-id",
  "stream_key": "encoder-key",
  "title": "My Stream",
  "destinations": [
    {
      "platform": "youtube",
      "rtmp_url": "rtmp://a.rtmp.youtube.com/live2",
      "stream_key": "youtube-key",
      "active": true
    }
  ]
}
```

`streamId` / `streamKey` camelCase aliases are also accepted.

### Stop session

```
POST /api/v1/sessions/{streamId}/stop
X-Stream-Engine-Secret: <shared-secret>
```

### List sessions

```
GET /api/v1/sessions
X-Stream-Engine-Secret: <shared-secret>
```

Returns `SessionSummary` objects with `streamId`, `title`, `state` (`RUNNING`, `STARTING`, `STOPPED`), and `startedAt`. Stream keys are omitted from list responses.

### Session status

```
GET /api/v1/sessions/{streamId}/status
X-Stream-Engine-Secret: <shared-secret>
```

Returns `{ "ok": true, "streamId": "...", "status": "RUNNING" }`.

### Destination health

```
GET /api/v1/sessions/{streamId}/destinations
```

See `docs/rtmp-relay.md`.

## Recastly webhook endpoints (stream-engine → Recastly)

| Endpoint | Purpose |
|----------|---------|
| `POST /api/v1/stream-engine/validate-key` | Validate encoder stream keys before AMS start |
| `POST /api/v1/stream-engine/webhook` | Lifecycle events (`stream.started`, `stream.ended`) |

## Local development

From the Recastly repo:

```bash
export STREAM_ENGINE_ENABLED=true
export STREAM_ENGINE_BASE_URL=http://localhost:8085
export STREAM_ENGINE_SHARED_SECRET=integration-test-secret

# stream-engine (sibling repo)
export RECASTLY_ENABLED=true
export RECASTLY_BASE_URL=http://localhost:8080/api/v1
export RECASTLY_SHARED_SECRET=integration-test-secret
export AMS_STUB_ENABLED=true
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

CI smoke: `recastly/scripts/ci-stream-engine-integration.sh`

### Analytics stack (Kafka + Flink)

```bash
docker compose -f docker-compose.yml -f docker-compose.analytics.yml up --build
```

Requires the sibling `flink-server` repo at `../flink-server`. Flink UI: http://localhost:8081

## Deprecated: C++ / CMake integration

An earlier design linked a C++ `stream-engine` library via CMake (`add_subdirectory`, `find_package`). The current repository is **Java-only**. Do not use CMake instructions from older docs; use the REST API above.
