#pragma once

#include "stream-engine/config.hpp"
#include "stream-engine/callbacks.hpp"
#include "stream-engine/types.hpp"

#include <memory>
#include <string>

namespace stream_engine {

/// High-level enterprise streaming service for sibling project integration (e.g. recastly).
///
/// Usage:
///   auto config = StreamingServiceConfig{...};
///   auto service = StreamingService::create(config);
///   service->set_callbacks({...});
///   auto r = service->start();
///   ...
///   service->shutdown();
class StreamingService {
 public:
  /// Create service from validated config. Returns nullptr if config invalid.
  static std::unique_ptr<StreamingService> create(const StreamingServiceConfig& config);

  ~StreamingService();

  StreamingService(const StreamingService&) = delete;
  StreamingService& operator=(const StreamingService&) = delete;

  /// Set event callbacks (safe to call before start).
  void set_callbacks(StreamingServiceCallbacks callbacks);

  /// Initialize and connect. Returns Result.
  Result start();

  /// Graceful shutdown. Idempotent.
  void shutdown();

  /// Current status.
  ServiceStatus status() const;

  /// Last error if status() == ServiceStatus::kError.
  Result last_error() const;

  /// Config used to create the service.
  const StreamingServiceConfig& config() const;

 private:
  explicit StreamingService(const StreamingServiceConfig& config);

  class Impl;
  std::unique_ptr<Impl> impl_;
};

}  // namespace stream_engine
