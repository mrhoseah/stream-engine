#pragma once

#include <string>
#include <memory>
#include <cstdint>

#include "stream-engine/types.hpp"

namespace stream_engine {

/// Core streaming engine API.
/// When built with Red5 SDK, provides RTSP/WebRTC connection management.
class StreamEngine {
 public:
  StreamEngine();
  ~StreamEngine();

  StreamEngine(const StreamEngine&) = delete;
  StreamEngine& operator=(const StreamEngine&) = delete;

  /// Initialize the engine (load config, connect to Red5 when available).
  bool initialize(const std::string& config_path = "");

  /// Initialize against a concrete host for migration-aware connections.
  bool initialize(const std::string& host, std::uint16_t port,
                  std::int32_t timeout_sec, const std::string& auth_token);

  /// Start publishing or subscribing. Returns false on failure.
  bool start(const std::string& stream_name);
  bool start(const std::string& stream_name, StreamMode mode);

  /// Stop streaming.
  void stop();

  /// Check if engine is ready (initialized and optionally connected).
  bool is_ready() const { return ready_; }

 private:
  bool ready_ = false;

#ifdef STREAM_ENGINE_HAS_RED5
  struct Red5Client;
  std::unique_ptr<Red5Client> red5_client_;
#endif
};

}  // namespace stream_engine
