#include "stream-engine/config.hpp"

namespace stream_engine {

Result StreamingServiceConfig::validate() const {
  if (host.empty()) {
    return {ErrorCode::kInvalidConfig, "host is required"};
  }
  if (stream_name.empty()) {
    return {ErrorCode::kInvalidConfig, "stream_name is required"};
  }
  if (connect_timeout_sec <= 0) {
    return {ErrorCode::kInvalidConfig, "connect_timeout_sec must be positive"};
  }
  if (reconnect_attempts < 0) {
    return {ErrorCode::kInvalidConfig, "reconnect_attempts cannot be negative"};
  }
  if (reconnect_backoff_ms < 0) {
    return {ErrorCode::kInvalidConfig, "reconnect_backoff_ms cannot be negative"};
  }
  return {};
}

}  // namespace stream_engine
