#pragma once

#include <cstdint>
#include <string>

namespace stream_engine::server {

/// Enterprise-grade server configuration.
struct ServerConfig {
  std::uint16_t port = 9090;
  std::size_t max_sessions = 1000;
  std::size_t max_request_body_bytes = 64 * 1024;  // 64 KB
  int shutdown_timeout_sec = 30;
  std::string log_level = "info";  // debug | info | warn | error
  bool metrics_enabled = true;
  bool graceful_shutdown = true;

  // Recastly webhook integration (optional)
  std::string recastly_base_url;   // e.g. http://localhost:8080/api/v1
  std::string recastly_shared_secret;

  /// Load from environment: PORT, MAX_SESSIONS, LOG_LEVEL, RECASTLY_BASE_URL, etc.
  static ServerConfig from_env();
};

}  // namespace stream_engine::server
