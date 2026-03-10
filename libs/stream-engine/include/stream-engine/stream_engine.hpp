#pragma once

#include <string>
#include <memory>

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

  /// Start publishing or subscribing. Returns false on failure.
  bool start(const std::string& stream_name);

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
