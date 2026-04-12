# Integrating stream-engine in recastly

Use stream-engine as an enterprise-grade streaming service in the **recastly** sibling project.

## Option A: add_subdirectory (same workspace)

In recastly's `CMakeLists.txt`:

```cmake
set(STREAM_ENGINE_SUBPROJECT ON CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../stream-engine ${CMAKE_BINARY_DIR}/stream-engine)
```

Then link:

```cmake
target_link_libraries(recastly_app PRIVATE stream-engine)
```

`STREAM_ENGINE_SUBPROJECT` skips apps and tests so only the library is built.

## Option B: find_package (installed)

1. Install stream-engine:

   ```powershell
   cd stream-engine
   cmake -B build
   cmake --build build --config Release
   cmake --install build --prefix install
   ```

2. In recastly's CMake:

   ```cmake
   set(stream-engine_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../stream-engine/install/lib/cmake/stream-engine")
   find_package(stream-engine REQUIRED)
   target_link_libraries(recastly_app PRIVATE stream-engine::stream-engine)
   ```

## C++ usage in recastly

```cpp
#include <stream-engine.hpp>

void run_stream() {
  stream_engine::StreamingServiceConfig config;
  config.host = "live.example.com";
  config.stream_name = "recastly_stream";
  config.mode = stream_engine::StreamMode::kPublish;
  config.auth_token = "...";

  auto service = stream_engine::StreamingService::create(config);
  if (!service) { /* invalid config */ }

  service->set_callbacks({
    .on_connected = [](const std::string& s) { /* recastly logic */ },
    .on_error = [](auto code, const std::string& msg) { /* log, retry */ },
    .on_stream_ready = [](const std::string& s) { /* UI update */ },
  });

  auto result = service->start();
  if (!result.ok()) { /* handle */ }

  // ... later
  service->shutdown();
}
```

## stream-engine-server → Recastly webhooks
## Using stream-engine as Recastly's streaming backend (Recommended)


stream-engine now uses a dedicated AntMediaService for all streaming operations. Configure AMS integration in `src/main/resources/application.yml`:

```yaml
ams:
  base-url: http://localhost:5080/LiveApp/rest/v2
  start-path: /broadcasts/create
  stop-path: /broadcasts/stop
  # ...other options as needed
```

No Red5 configuration is required. All stream/session management is handled via AntMediaService and AMS REST API.

To use stream-engine as a proxy for Ant Media Server, configure Recastly to send all stream/session management requests to stream-engine's REST API.

### 1. Set the stream-engine endpoint in Recastly

Set the following environment variable in Recastly:

| Env var | Description |
|---------|-------------|
| `STREAM_ENGINE_BASE_URL` | URL of your stream-engine instance (e.g. `http://localhost:8085`) |

### 2. Example API usage from Recastly

To start a stream:

```
POST $STREAM_ENGINE_BASE_URL/api/v1/sessions
Content-Type: application/json
{
  "streamId": "my-stream-id",
  "title": "My Stream Title"
}
```

To stop a stream:

```
POST $STREAM_ENGINE_BASE_URL/api/v1/sessions/my-stream-id/stop
```

To get stream status:

```
GET $STREAM_ENGINE_BASE_URL/api/v1/sessions/my-stream-id/status
```

### 3. Security

stream-engine enforces authentication and signature checks for inbound requests from Recastly. Ensure your secrets and allowed IPs are configured in `application.yml` or via environment variables.

### 4. Why use this architecture?

- Centralizes business logic and security
- Allows backend changes without affecting Recastly
- Enables custom features, logging, and analytics

When sessions start or stop, stream-engine-server POSTs to Recastly's webhook so Recastly can update stream status. Set:

| Env var | Description |
|---------|-------------|
| `RECASTLY_BASE_URL` | Recastly API base (e.g. `http://localhost:8080/api/v1`) |
| `RECASTLY_STREAM_ENGINE_SECRET` | Must match `stream_engine_shared_secret` in Recastly config |

If both are set, stream-engine sends `stream.started` on session create and `stream.ended` on session stop. If unset, webhooks are skipped.
