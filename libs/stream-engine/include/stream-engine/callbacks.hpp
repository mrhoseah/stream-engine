#pragma once

#include "stream-engine/types.hpp"

#include <functional>
#include <string>

namespace stream_engine {

/// Callbacks for StreamingService lifecycle and events.
/// Set via StreamingService::set_callbacks() for recastly integration.
struct StreamingServiceCallbacks {
  /// Called when service has connected to Red5 server.
  std::function<void(const std::string& stream_name)> on_connected;

  /// Called on connection or stream error.
  std::function<void(ErrorCode code, const std::string& message)> on_error;

  /// Called when stream is ready (publishing or receiving).
  std::function<void(const std::string& stream_name)> on_stream_ready;

  /// Called when stream stops.
  std::function<void(const std::string& stream_name)> on_stream_stopped;

  /// Called when service status changes.
  std::function<void(ServiceStatus from, ServiceStatus to)> on_status_changed;

  /// Called when shutdown completes.
  std::function<void()> on_shutdown;
};

}  // namespace stream_engine
