#include "server_config.hpp"
#include <cstdlib>
#include <cstring>

namespace stream_engine::server {

namespace {

const char* getenv_safe(const char* name, const char* default_val) {
  const char* v = std::getenv(name);
  return v && v[0] != '\0' ? v : default_val;
}

std::uint16_t parse_port(const char* s) {
  if (!s) return 9090;
  int v = std::atoi(s);
  return (v > 0 && v <= 65535) ? static_cast<std::uint16_t>(v) : 9090;
}

std::size_t parse_size(const char* s, std::size_t default_val) {
  if (!s) return default_val;
  int v = std::atoi(s);
  return (v > 0) ? static_cast<std::size_t>(v) : default_val;
}

}  // namespace

ServerConfig ServerConfig::from_env() {
  ServerConfig cfg;
  cfg.port = parse_port(getenv_safe("PORT", "9090"));
  cfg.max_sessions = parse_size(getenv_safe("MAX_SESSIONS", "1000"), 1000);
  cfg.max_request_body_bytes =
      parse_size(getenv_safe("MAX_REQUEST_BODY_BYTES", "65536"), 64 * 1024);
  cfg.shutdown_timeout_sec =
      static_cast<int>(parse_size(getenv_safe("SHUTDOWN_TIMEOUT_SEC", "30"), 30));
  cfg.log_level = getenv_safe("LOG_LEVEL", "info");
  cfg.metrics_enabled = (std::strcmp(getenv_safe("METRICS_ENABLED", "1"), "0") != 0);
  cfg.graceful_shutdown =
      (std::strcmp(getenv_safe("GRACEFUL_SHUTDOWN", "1"), "0") != 0);
  return cfg;
}

}  // namespace stream_engine::server
