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
  return {};
}

}  // namespace stream_engine
