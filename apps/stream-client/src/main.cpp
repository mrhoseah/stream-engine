#include "stream-engine.hpp"
#include <iostream>
#include <thread>
#include <chrono>

int main(int argc, char* argv[]) {
  stream_engine::StreamingServiceConfig config;
  config.host = "localhost";
  config.stream_name = (argc > 1) ? argv[1] : "stream1";
  config.mode = stream_engine::StreamMode::kPublish;
  config.connect_timeout_sec = 30;

  auto service = stream_engine::StreamingService::create(config);
  if (!service) {
    std::cerr << "Invalid config.\n";
    return 1;
  }

  service->set_callbacks({
    .on_connected = [](const std::string& s) {
      std::cout << "[callback] Connected: " << s << '\n';
    },
    .on_error = [](stream_engine::ErrorCode code, const std::string& msg) {
      std::cerr << "[callback] Error: " << stream_engine::to_string(code) << " - " << msg << '\n';
    },
    .on_stream_ready = [](const std::string& s) {
      std::cout << "[callback] Stream ready: " << s << '\n';
    },
    .on_status_changed = [](stream_engine::ServiceStatus from, stream_engine::ServiceStatus to) {
      std::cout << "[callback] Status: " << stream_engine::to_string(from)
                << " -> " << stream_engine::to_string(to) << '\n';
    },
    .on_shutdown = [] { std::cout << "[callback] Shutdown complete.\n"; },
  });

  auto result = service->start();
  if (!result.ok()) {
    std::cerr << "Start failed: " << result.message << '\n';
    return 1;
  }

  std::cout << "Streaming. Press Enter to stop...\n";
  std::cin.get();

  service->shutdown();
  return 0;
}
