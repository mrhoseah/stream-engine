#include "stream-engine/stream_engine.hpp"

#ifdef STREAM_ENGINE_HAS_RED5
// Red5 Core SDK headers (paths depend on your SDK distribution)
// #include <r5/IClient.hpp>
#endif

namespace stream_engine {

StreamEngine::StreamEngine() = default;

StreamEngine::~StreamEngine() = default;

bool StreamEngine::initialize(const std::string& /*config_path*/) {
#ifdef STREAM_ENGINE_HAS_RED5
  // The SDK adapter must provide the actual client and connection lifecycle.
  // Do not report readiness while the adapter is still unavailable.
  return false;
#endif
  ready_ = true;
  return true;
}

bool StreamEngine::initialize(const std::string& /*host*/, std::uint16_t /*port*/,
                              std::int32_t /*timeout_sec*/, const std::string& /*auth_token*/) {
  return initialize();
}

bool StreamEngine::start(const std::string& /*stream_name*/) {
  if (!ready_) return false;
#ifdef STREAM_ENGINE_HAS_RED5
  // Publishing/subscribing must be implemented by the SDK adapter.
  return false;
#endif
  return true;
}

bool StreamEngine::start(const std::string& stream_name, StreamMode /*mode*/) {
  return start(stream_name);
}

void StreamEngine::stop() {
#ifdef STREAM_ENGINE_HAS_RED5
  // Disconnect must be implemented by the SDK adapter.
#endif
  ready_ = false;
}

}  // namespace stream_engine
