# stream-engine

C++ streaming engine built with **Red5 Pro Core SDK**, using CMake and optional vcpkg for dependency management.

## Project Structure

```
stream-engine/
├── CMakeLists.txt           # Root build config
├── vcpkg.json               # vcpkg manifest (optional)
├── .clang-format            # Code formatting
├── .clang-tidy              # Static analysis
├── libs/
│   └── stream-engine/       # Core library
│       ├── include/         # Public headers
│       └── src/             # Implementation
├── apps/
│   └── stream-client/       # Example application
└── tests/
    └── unit/                # Unit tests
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
