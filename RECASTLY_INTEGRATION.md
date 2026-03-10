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
