# RTMP simulcast relay

stream-engine can restream an active AMS session to external RTMP ingest endpoints (YouTube, Twitch, custom RTMP) using **FFmpeg**.

## Flow

1. Recastly starts a session with `destinations[]` (`platform`, `rtmp_url`, `stream_key`, `active`).
2. AMS creates the primary broadcast.
3. When `rtmp-relay.enabled=true`, stream-engine spawns one FFmpeg process per active destination:
   - **Source**: HLS playback from AMS (default) or RTMP ingest URL.
   - **Output**: `{rtmp_url}/{stream_key}` (or `{stream_key}` placeholder in the base URL).
4. On session stop, FFmpeg processes are terminated.

## Configuration

Set in `application.yml` or environment:

| Property | Env var | Default |
|----------|---------|---------|
| `rtmp-relay.enabled` | `RTMP_RELAY_ENABLED` | `false` |
| `rtmp-relay.ffmpeg-path` | `RTMP_RELAY_FFMPEG_PATH` | `ffmpeg` |
| `rtmp-relay.source-type` | `RTMP_RELAY_SOURCE_TYPE` | `hls` |
| `rtmp-relay.source-hls-url-template` | `RTMP_RELAY_SOURCE_HLS_URL` | `http://localhost:5080/LiveApp/streams/{streamId}.m3u8` |
| `rtmp-relay.source-rtmp-url-template` | `RTMP_RELAY_SOURCE_RTMP_URL` | `rtmp://localhost/LiveApp/{streamId}` |
| `rtmp-relay.start-delay-ms` | `RTMP_RELAY_START_DELAY_MS` | `3000` |

**Requirements**: FFmpeg must be on `PATH` (or set `ffmpeg-path`). AMS HLS must be reachable from the stream-engine host when using `source-type: hls`.

## Health API

`GET /api/v1/sessions/{streamId}/destinations` returns registered destinations with push status:

- `pushStatus`: `DISABLED`, `STARTING`, `RUNNING`, `STOPPED`, `FAILED`
- `errorMessage`: set when `FAILED`
- `runningCount`: destinations currently pushing

## Local dev

```sh
# Install ffmpeg, then:
export RTMP_RELAY_ENABLED=true
export RTMP_RELAY_SOURCE_HLS_URL=http://localhost:5080/LiveApp/streams/{streamId}.m3u8
mvn spring-boot:run
```

When disabled (default), destinations are tracked for API visibility but no FFmpeg processes are started — safe for CI and unit tests.
