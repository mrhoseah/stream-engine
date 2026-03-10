#pragma once

#include <cstdint>
#include <string>

namespace stream_engine {

/// Error codes for streaming operations.
enum class ErrorCode : std::int32_t {
  kSuccess = 0,
  kNotInitialized,
  kAlreadyInitialized,
  kInvalidConfig,
  kConnectionFailed,
  kConnectionTimeout,
  kStreamNotFound,
  kStreamAlreadyActive,
  kAuthFailed,
  kNetworkError,
  kInternalError,
};

/// Human-readable message for ErrorCode.
inline const char* to_string(ErrorCode ec) {
  switch (ec) {
    case ErrorCode::kSuccess: return "success";
    case ErrorCode::kNotInitialized: return "not initialized";
    case ErrorCode::kAlreadyInitialized: return "already initialized";
    case ErrorCode::kInvalidConfig: return "invalid config";
    case ErrorCode::kConnectionFailed: return "connection failed";
    case ErrorCode::kConnectionTimeout: return "connection timeout";
    case ErrorCode::kStreamNotFound: return "stream not found";
    case ErrorCode::kStreamAlreadyActive: return "stream already active";
    case ErrorCode::kAuthFailed: return "authentication failed";
    case ErrorCode::kNetworkError: return "network error";
    case ErrorCode::kInternalError: return "internal error";
    default: return "unknown";
  }
}

/// Result type for operations that can fail.
struct Result {
  ErrorCode code = ErrorCode::kSuccess;
  std::string message;

  bool ok() const { return code == ErrorCode::kSuccess; }
  explicit operator bool() const { return ok(); }
};

/// Service lifecycle status.
enum class ServiceStatus : std::int32_t {
  kStopped,
  kInitializing,
  kReady,
  kConnecting,
  kConnected,
  kStreaming,
  kStopping,
  kError,
};

inline const char* to_string(ServiceStatus s) {
  switch (s) {
    case ServiceStatus::kStopped: return "stopped";
    case ServiceStatus::kInitializing: return "initializing";
    case ServiceStatus::kReady: return "ready";
    case ServiceStatus::kConnecting: return "connecting";
    case ServiceStatus::kConnected: return "connected";
    case ServiceStatus::kStreaming: return "streaming";
    case ServiceStatus::kStopping: return "stopping";
    case ServiceStatus::kError: return "error";
    default: return "unknown";
  }
}

/// Stream direction: publish or subscribe.
enum class StreamMode : std::int32_t {
  kPublish,
  kSubscribe,
};

}  // namespace stream_engine
