#include "stream-engine.hpp"
#include <cassert>
#include <iostream>

int main() {
  stream_engine::StreamingServiceConfig config;
  config.host = "localhost";
  config.stream_name = "test_stream";
  config.mode = stream_engine::StreamMode::kSubscribe;

  auto r = config.validate();
  assert(r.ok());

  auto service = stream_engine::StreamingService::create(config);
  assert(service != nullptr);
  assert(service->status() == stream_engine::ServiceStatus::kStopped);

  auto start_result = service->start();
  assert(start_result.ok());
  assert(service->status() == stream_engine::ServiceStatus::kStreaming);

  service->shutdown();
  assert(service->status() == stream_engine::ServiceStatus::kStopped);

  // Invalid config
  stream_engine::StreamingServiceConfig bad_config;
  bad_config.host = "";
  assert(!bad_config.validate().ok());
  auto bad_service = stream_engine::StreamingService::create(bad_config);
  assert(bad_service == nullptr);

  std::cout << "StreamingService tests passed.\n";
  return 0;
}
