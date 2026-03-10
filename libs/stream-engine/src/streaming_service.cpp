#include "stream-engine/streaming_service.hpp"
#include "stream-engine/stream_engine.hpp"

#include <mutex>

namespace stream_engine {

class StreamingService::Impl {
 public:
  explicit Impl(const StreamingServiceConfig& config) : config_(config) {}

  void set_callbacks(StreamingServiceCallbacks cbs) {
    std::lock_guard lock(callbacks_mutex_);
    callbacks_ = std::move(cbs);
  }

  Result start() {
    auto r = config_.validate();
    if (!r.ok()) return r;

    set_status(ServiceStatus::kInitializing);

    engine_ = std::make_unique<StreamEngine>();
    if (!engine_->initialize()) {
      set_status(ServiceStatus::kError);
      last_error_ = {ErrorCode::kInternalError, "Failed to initialize engine"};
      invoke_on_error(last_error_.code, last_error_.message);
      return last_error_;
    }

    set_status(ServiceStatus::kReady);

    set_status(ServiceStatus::kConnecting);
#ifdef STREAM_ENGINE_HAS_RED5
    // TODO: Connect to Red5 Stream Manager using config_.host, config_.stream_name
#endif
    set_status(ServiceStatus::kConnected);
    invoke_on_connected(config_.stream_name);

    if (!engine_->start(config_.stream_name)) {
      set_status(ServiceStatus::kError);
      last_error_ = {ErrorCode::kConnectionFailed, "Failed to start stream"};
      invoke_on_error(last_error_.code, last_error_.message);
      return last_error_;
    }

    set_status(ServiceStatus::kStreaming);
    invoke_on_stream_ready(config_.stream_name);
    return {};
  }

  void shutdown() {
    if (status_ == ServiceStatus::kStopped || status_ == ServiceStatus::kStopping) {
      return;
    }
    set_status(ServiceStatus::kStopping);
    if (engine_) {
      engine_->stop();
      engine_.reset();
    }
    set_status(ServiceStatus::kStopped);
    invoke_on_shutdown();
  }

  ServiceStatus status() const {
    std::lock_guard lock(status_mutex_);
    return status_;
  }

  Result last_error() const {
    std::lock_guard lock(status_mutex_);
    return last_error_;
  }

  const StreamingServiceConfig& config() const { return config_; }

 private:
  void set_status(ServiceStatus to) {
    ServiceStatus from;
    {
      std::lock_guard lock(status_mutex_);
      from = status_;
      status_ = to;
    }
    invoke_on_status_changed(from, to);
  }

  void invoke_on_connected(const std::string& s) {
    std::lock_guard lock(callbacks_mutex_);
    if (callbacks_.on_connected) callbacks_.on_connected(s);
  }
  void invoke_on_error(ErrorCode c, const std::string& m) {
    std::lock_guard lock(callbacks_mutex_);
    if (callbacks_.on_error) callbacks_.on_error(c, m);
  }
  void invoke_on_stream_ready(const std::string& s) {
    std::lock_guard lock(callbacks_mutex_);
    if (callbacks_.on_stream_ready) callbacks_.on_stream_ready(s);
  }
  void invoke_on_stream_stopped(const std::string& s) {
    std::lock_guard lock(callbacks_mutex_);
    if (callbacks_.on_stream_stopped) callbacks_.on_stream_stopped(s);
  }
  void invoke_on_status_changed(ServiceStatus from, ServiceStatus to) {
    std::lock_guard lock(callbacks_mutex_);
    if (callbacks_.on_status_changed) callbacks_.on_status_changed(from, to);
  }
  void invoke_on_shutdown() {
    std::lock_guard lock(callbacks_mutex_);
    if (callbacks_.on_shutdown) callbacks_.on_shutdown();
  }

  StreamingServiceConfig config_;
  std::unique_ptr<StreamEngine> engine_;

  mutable std::mutex status_mutex_;
  ServiceStatus status_ = ServiceStatus::kStopped;
  Result last_error_;

  mutable std::mutex callbacks_mutex_;
  StreamingServiceCallbacks callbacks_;
};

// --- StreamingService ---

std::unique_ptr<StreamingService> StreamingService::create(const StreamingServiceConfig& config) {
  if (!config.validate().ok()) return nullptr;
  return std::unique_ptr<StreamingService>(new StreamingService(config));
}

StreamingService::StreamingService(const StreamingServiceConfig& config)
    : impl_(std::make_unique<Impl>(config)) {}

StreamingService::~StreamingService() {
  impl_->shutdown();
}

void StreamingService::set_callbacks(StreamingServiceCallbacks callbacks) {
  impl_->set_callbacks(std::move(callbacks));
}

Result StreamingService::start() { return impl_->start(); }

void StreamingService::shutdown() { impl_->shutdown(); }

ServiceStatus StreamingService::status() const { return impl_->status(); }

Result StreamingService::last_error() const { return impl_->last_error(); }

const StreamingServiceConfig& StreamingService::config() const { return impl_->config(); }

}  // namespace stream_engine
