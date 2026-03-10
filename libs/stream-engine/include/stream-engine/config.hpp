#pragma once

#include "stream-engine/types.hpp"

#include <string>
#include <cstdint>

namespace stream_engine {

/// Enterprise-grade streaming service configuration.
/// Use with StreamingService::create() for sibling project integration.
struct StreamingServiceConfig {
  /// Red5 Stream Manager / server host (e.g., "live.example.com").
  std::string host;
  /// Port for RTSP/WebRTC (0 = default).
  std::uint16_t port = 0;
  /// Stream name / app context.
  std::string stream_name;
  /// Publish or subscribe.
  StreamMode mode = StreamMode::kPublish;
  /// Connection timeout in seconds.
  std::int32_t connect_timeout_sec = 30;
  /// Optional auth token or credentials.
  std::string auth_token;

  /// Validate configuration. Returns error if invalid.
  Result validate() const;
};

}  // namespace stream_engine
