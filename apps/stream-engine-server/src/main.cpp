/**
 * stream-engine-server – Enterprise-grade HTTP API for Recastly streaming.
 *
 * Features: graceful shutdown, liveness/readiness, Prometheus metrics,
 * configurable limits, request validation, structured error responses.
 */

#include "server_config.hpp"
#include "stream-engine/stream_engine.hpp"
#include <atomic>
#include <chrono>
#include <csignal>
#include <cstring>
#include <httplib.h>
#include <iostream>
#include <map>
#include <mutex>
#include <nlohmann/json.hpp>
#include <regex>
#include <string>
#include <thread>

using json = nlohmann::json;

namespace {

// Stream ID: UUID or alphanumeric-hyphen, 1-128 chars (enterprise-safe)
const std::regex kStreamIdRegex(R"(^[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}$)");

bool is_valid_stream_id(const std::string& s) {
  return !s.empty() && s.size() <= 128 && std::regex_match(s, kStreamIdRegex);
}

json error_response(const std::string& code, const std::string& message,
                   const std::string& request_id = "") {
  json j = {{"ok", false}, {"error", {{"code", code}, {"message", message}}}};
  if (!request_id.empty()) j["request_id"] = request_id;
  return j;
}

std::string get_request_id(const httplib::Request& req) {
  auto it = req.headers.find("X-Request-ID");
  if (it != req.headers.end() && !it->second.empty()) return it->second;
  return "";
}

std::string get_idempotency_key(const httplib::Request& req) {
  auto it = req.headers.find("Idempotency-Key");
  if (it != req.headers.end() && !it->second.empty()) return it->second;
  return get_request_id(req);
}

// Safe path param access; returns empty if missing
std::string get_path_param(const httplib::Request& req, const std::string& name) {
  auto it = req.path_params.find(name);
  if (it == req.path_params.end()) return "";
  return it->second;
}

// Parse base URL (e.g. http://localhost:8080/api/v1) into host, port, path.
bool parse_base_url(const std::string& url, std::string& host, int& port,
                    std::string& path) {
  if (url.empty()) return false;
  size_t p = url.find("://");
  if (p == std::string::npos) return false;
  std::string rest = url.substr(p + 3);
  size_t slash = rest.find('/');
  std::string host_port = (slash != std::string::npos) ? rest.substr(0, slash) : rest;
  path = (slash != std::string::npos && slash + 1 < rest.size())
             ? "/" + rest.substr(slash + 1)
             : "/api/v1";
  while (!path.empty() && path.back() == '/') path.pop_back();
  size_t colon = host_port.find(':');
  if (colon != std::string::npos) {
    host = host_port.substr(0, colon);
    port = std::atoi(host_port.substr(colon + 1).c_str());
    if (port <= 0 || port > 65535) port = 80;
  } else {
    host = host_port;
    port = (url.substr(0, p) == "https") ? 443 : 80;
  }
  return !host.empty();
}

struct ValidateKeyResult {
  bool valid = false;
  std::string stream_id;
  std::string message;
};

ValidateKeyResult validate_key_recastly(const stream_engine::server::ServerConfig& cfg,
                                        const std::string& stream_key) {
  if (cfg.recastly_base_url.empty() || cfg.recastly_shared_secret.empty()) {
    return cfg.production_mode
               ? ValidateKeyResult{false, "", "Recastly validation is required in production"}
               : ValidateKeyResult{true, "", ""};
  }
  std::string host;
  int port = 80;
  std::string base_path;
  if (!parse_base_url(cfg.recastly_base_url, host, port, base_path)) {
    return cfg.production_mode
               ? ValidateKeyResult{false, "", "invalid Recastly base URL"}
               : ValidateKeyResult{true, "", ""};
  }
  std::string path = base_path + "/stream-engine/validate-key";
  json body = {{"stream_key", stream_key}};
  std::string body_str = body.dump();
  try {
    httplib::Client cli(host.c_str(), port);
    cli.set_connection_timeout(5, 0);
    cli.set_read_timeout(5, 0);
    httplib::Headers headers = {{"X-Stream-Engine-Secret", cfg.recastly_shared_secret}};
    auto res = cli.Post(path, headers, body_str, "application/json");
    if (!res) {
      return {false, "", "recastly validation request failed"};
    }
    json j = json::parse(res->body);
    bool valid = j.value("valid", false);
    std::string stream_id = j.value("stream_id", "");
    std::string message = j.value("message", "");
    return {valid, stream_id, message};
  } catch (const std::exception& e) {
    return {false, "", std::string("recastly validation error: ") + e.what()};
  }
}

void send_recastly_webhook(const stream_engine::server::ServerConfig& cfg,
                          const std::string& event, const std::string& stream_id,
                          const std::string& stream_key, int duration_sec = 0) {
  if (cfg.recastly_base_url.empty() || cfg.recastly_shared_secret.empty()) return;
  std::string host;
  int port = 80;
  std::string base_path;
  if (!parse_base_url(cfg.recastly_base_url, host, port, base_path)) return;
  std::string path = base_path + "/stream-engine/webhook";
  const std::string event_id = event + ":" + stream_id;
  json body = {{"event", event}, {"event_id", event_id}, {"stream_id", stream_id}};
  if (!stream_key.empty()) body["stream_key"] = stream_key;
  if (duration_sec > 0) body["duration_sec"] = duration_sec;
  std::string body_str = body.dump();
  for (int attempt = 0; attempt <= cfg.webhook_retry_attempts; ++attempt) {
    try {
      httplib::Client cli(host.c_str(), port);
      cli.set_connection_timeout(5, 0);
      cli.set_read_timeout(5, 0);
      httplib::Headers headers = {{"X-Stream-Engine-Secret", cfg.recastly_shared_secret},
                                  {"Idempotency-Key", event_id}};
      auto res = cli.Post(path, headers, body_str, "application/json");
      if (res && res->status >= 200 && res->status < 300) {
        std::cout << "[stream-engine] Webhook " << event << " stream=" << stream_id
                  << " ok" << std::endl;
        return;
      }
      if (res) {
        std::cerr << "[stream-engine] Webhook " << event << " stream=" << stream_id
                  << " failed status=" << res->status << std::endl;
      }
    } catch (const std::exception& e) {
      std::cerr << "[stream-engine] Webhook " << event << " stream=" << stream_id
                << " error: " << e.what() << std::endl;
    }
    if (attempt < cfg.webhook_retry_attempts) {
      std::this_thread::sleep_for(std::chrono::milliseconds(
          cfg.webhook_retry_backoff_ms * (attempt + 1)));
    }
  }
}

// ---------------------------------------------------------------------------
// SessionManager – thread-safe, max sessions enforced
// ---------------------------------------------------------------------------

struct Session {
  std::string stream_id;
  std::string stream_key;
  std::string title;
  std::string idempotency_key;
  stream_engine::StreamEngine engine;
  bool active{false};
  std::chrono::steady_clock::time_point start_time{};
};

class SessionManager {
 public:
  explicit SessionManager(std::size_t max_sessions) : max_sessions_(max_sessions) {}

  struct StartResult {
    bool ok = false;
    std::string error_code;
    std::string error_message;
    bool already_started = false;
  };

  StartResult start(const json& cfg, const stream_engine::server::ServerConfig& svc_cfg,
                    const std::string& idempotency_key) {
    std::string stream_id = cfg.value("stream_id", "");
    std::string stream_key = cfg.value("stream_key", "");
    std::string title = cfg.value("title", "stream");

    if (stream_id.empty()) {
      return {false, "INVALID_REQUEST", "stream_id is required"};
    }
    if (!is_valid_stream_id(stream_id)) {
      return {false, "INVALID_STREAM_ID", "stream_id must be alphanumeric, 1-128 chars"};
    }

    if (svc_cfg.production_mode &&
        (svc_cfg.recastly_base_url.empty() || svc_cfg.recastly_shared_secret.empty())) {
      return {false, "CONFIGURATION_ERROR",
              "Recastly URL and shared secret are required in production"};
    }

    if (!stream_key.empty() && !svc_cfg.recastly_base_url.empty() &&
        !svc_cfg.recastly_shared_secret.empty()) {
      auto v = validate_key_recastly(svc_cfg, stream_key);
      if (!v.valid) {
        return {false, "INVALID_STREAM_KEY", v.message.empty() ? "invalid stream key" : v.message};
      }
    }

    std::lock_guard<std::mutex> lock(mu_);
    if (sessions_.count(stream_id)) {
      const auto& existing = sessions_.at(stream_id);
      if (!idempotency_key.empty() && existing.idempotency_key == idempotency_key) {
        return {true, "", "", true};
      }
      return {false, "STREAM_ALREADY_ACTIVE", "stream already active"};
    }
    if (sessions_.size() >= max_sessions_) {
      return {false, "CAPACITY_EXCEEDED", "max sessions reached"};
    }

    auto& s = sessions_[stream_id];
    s.stream_id = stream_id;
    s.stream_key = stream_key.empty() ? stream_id : stream_key;
    s.title = title;
    s.idempotency_key = idempotency_key;

    if (!s.engine.initialize()) {
      sessions_.erase(stream_id);
      return {false, "ENGINE_INIT_FAILED", "failed to initialize engine"};
    }
    if (!s.engine.start(s.stream_key)) {
      s.engine.stop();
      sessions_.erase(stream_id);
      return {false, "ENGINE_START_FAILED", "failed to start stream"};
    }

    s.active = true;
    s.start_time = std::chrono::steady_clock::now();
    return {true, "", ""};
  }

  bool stop(const std::string& stream_id,
            const stream_engine::server::ServerConfig& svc_cfg) {
    std::string key;
    int duration_sec = 0;
    {
      std::lock_guard<std::mutex> lock(mu_);
      auto it = sessions_.find(stream_id);
      if (it == sessions_.end()) return false;
      if (it->second.active) {
        auto now = std::chrono::steady_clock::now();
        duration_sec = static_cast<int>(
            std::chrono::duration_cast<std::chrono::seconds>(now - it->second.start_time).count());
      }
      key = it->second.stream_key;
      it->second.engine.stop();
      it->second.active = false;
      sessions_.erase(it);
    }
    send_recastly_webhook(svc_cfg, "stream.ended", stream_id, key, duration_sec);
    return true;
  }

  void stop_all() {
    std::vector<std::pair<std::string, std::string>> stopped;
    {
      std::lock_guard<std::mutex> lock(mu_);
      for (auto& [id, s] : sessions_) {
        s.engine.stop();
        s.active = false;
        stopped.emplace_back(id, s.stream_key);
      }
      sessions_.clear();
    }
    for (const auto& [id, key] : stopped) {
      send_recastly_webhook(shutdown_config_, "stream.ended", id, key);
    }
  }

  void set_shutdown_config(const stream_engine::server::ServerConfig& config) {
    shutdown_config_ = config;
  }

  std::string playback_endpoint(const std::string& stream_id) const {
    if (shutdown_config_.playback_base_url.empty()) return "";
    std::string endpoint = shutdown_config_.playback_base_url;
    while (!endpoint.empty() && endpoint.back() == '/') endpoint.pop_back();
    return endpoint + "/" + stream_id;
  }

  std::vector<json> list() const {
    std::lock_guard<std::mutex> lock(mu_);
    std::vector<json> out;
    for (const auto& [id, s] : sessions_) {
      if (s.active) {
        out.push_back(json{{"stream_id", s.stream_id},
                           {"title", s.title},
                           {"status", "live"}});
      }
    }
    return out;
  }

  std::string status(const std::string& stream_id) const {
    std::lock_guard<std::mutex> lock(mu_);
    auto it = sessions_.find(stream_id);
    if (it == sessions_.end()) return "ended";
    return it->second.active ? "live" : "ended";
  }

  std::size_t count() const {
    std::lock_guard<std::mutex> lock(mu_);
    return sessions_.size();
  }

  bool is_ready() const { return !shutting_down_.load(); }
  void set_shutting_down() { shutting_down_ = true; }

  std::string get_stream_key(const std::string& stream_id) const {
    std::lock_guard<std::mutex> lock(mu_);
    auto it = sessions_.find(stream_id);
    return (it != sessions_.end()) ? it->second.stream_key : "";
  }

 private:
  std::size_t max_sessions_;
  mutable std::mutex mu_;
  std::map<std::string, Session> sessions_;
  std::atomic<bool> shutting_down_{false};
  stream_engine::server::ServerConfig shutdown_config_;
};

// ---------------------------------------------------------------------------
// Metrics – Prometheus-compatible counters
// ---------------------------------------------------------------------------

class Metrics {
 public:
  void inc_requests_total(const std::string&, const std::string&, int) {
    std::lock_guard<std::mutex> lock(mu_);
    ++requests_total_;
  }

  std::string prometheus_text(std::size_t sessions_active) const {
    std::lock_guard<std::mutex> lock(mu_);
    std::string out;
    out += "# HELP stream_engine_sessions_active Current active stream sessions.\n";
    out += "# TYPE stream_engine_sessions_active gauge\n";
    out += "stream_engine_sessions_active " + std::to_string(sessions_active) + "\n";
    out += "# HELP stream_engine_requests_total Total HTTP requests.\n";
    out += "# TYPE stream_engine_requests_total counter\n";
    out += "stream_engine_requests_total " + std::to_string(requests_total_) + "\n";
    return out;
  }

 private:
  mutable std::mutex mu_;
  std::uint64_t requests_total_ = 0;
};

// ---------------------------------------------------------------------------
// Server setup
// ---------------------------------------------------------------------------

std::atomic<bool> g_running{true};

void signal_handler(int) { g_running = false; }

}  // namespace

int main(int argc, char* argv[]) {
  auto config = stream_engine::server::ServerConfig::from_env();
  if (argc > 1) {
    int p = std::atoi(argv[1]);
    if (p > 0 && p <= 65535) config.port = static_cast<std::uint16_t>(p);
  }

  SessionManager mgr(config.max_sessions);
  mgr.set_shutdown_config(config);
  Metrics metrics;
  httplib::Server svr;

  svr.set_payload_max_length(config.max_request_body_bytes);

  // Request logging / metrics hook
  svr.set_post_routing_handler([&metrics](const httplib::Request& req,
                                          httplib::Response& res) {
    metrics.inc_requests_total(req.method, req.path, res.status);
    return httplib::Server::HandlerResponse::Unhandled;
  });

  // POST /api/v1/sessions
  svr.Post("/api/v1/sessions", [&mgr, &config](const httplib::Request& req,
                                               httplib::Response& res) {
    if (!mgr.is_ready()) {
      res.status = 503;
      res.set_content(
          error_response("SERVICE_UNAVAILABLE", "server shutting down",
                         get_request_id(req))
              .dump(),
          "application/json");
      return;
    }
    try {
      if (req.body.size() > config.max_request_body_bytes) {
        res.status = 413;
        res.set_content(
            error_response("PAYLOAD_TOO_LARGE", "request body too large",
                           get_request_id(req))
                .dump(),
            "application/json");
        return;
      }
      json body = json::parse(req.body);
      auto r = mgr.start(body, config, get_idempotency_key(req));
      if (r.ok) {
        if (!r.already_started) {
          send_recastly_webhook(config, "stream.started",
                               body.value("stream_id", ""),
                               body.value("stream_key", ""), 0);
        }
        res.status = 200;
        res.set_content(json{{"ok", true}, {"already_started", r.already_started}}.dump(),
                        "application/json");
      } else {
        res.status = (r.error_code == "CAPACITY_EXCEEDED") ? 503 : 400;
        res.set_content(
            error_response(r.error_code, r.error_message, get_request_id(req))
                .dump(),
            "application/json");
      }
    } catch (const json::exception& e) {
      res.status = 400;
      res.set_content(
          error_response("INVALID_JSON", std::string(e.what()), get_request_id(req))
              .dump(),
          "application/json");
    } catch (const std::exception& e) {
      res.status = 500;
      res.set_content(
          error_response("INTERNAL_ERROR", std::string(e.what()), get_request_id(req))
              .dump(),
          "application/json");
    }
  });

  // POST /api/v1/sessions/:id/stop
  svr.Post("/api/v1/sessions/:id/stop",
           [&mgr, &config](const httplib::Request& req, httplib::Response& res) {
             std::string id = get_path_param(req, "id");
             if (id.empty()) {
               res.status = 400;
               res.set_content(
                   error_response("INVALID_REQUEST", "missing path param: id",
                                  get_request_id(req))
                       .dump(),
                   "application/json");
               return;
             }
             if (!mgr.stop(id, config)) {
               res.status = 404;
               res.set_content(
                   error_response("NOT_FOUND", "stream not found",
                                  get_request_id(req))
                       .dump(),
                   "application/json");
               return;
             }
             res.status = 200;
             res.set_content(R"({"ok":true})", "application/json");
           });

  // GET /api/v1/sessions
  svr.Get("/api/v1/sessions",
          [&mgr](const httplib::Request&, httplib::Response& res) {
            auto list = mgr.list();
            res.set_content(json(list).dump(), "application/json");
            res.status = 200;
          });

  // GET /api/v1/sessions/:id/status
  svr.Get("/api/v1/sessions/:id/status",
          [&mgr](const httplib::Request& req, httplib::Response& res) {
            std::string id = get_path_param(req, "id");
            if (id.empty()) {
              res.status = 400;
              res.set_content(
                  error_response("INVALID_REQUEST", "missing path param: id",
                                 get_request_id(req))
                      .dump(),
                  "application/json");
              return;
            }
            std::string st = mgr.status(id);
            res.set_content(json{{"status", st}}.dump(), "application/json");
            res.status = 200;
          });

  // GET /api/v1/sessions/:id/playback
  svr.Get("/api/v1/sessions/:id/playback",
          [&mgr](const httplib::Request& req, httplib::Response& res) {
            std::string id = get_path_param(req, "id");
            if (id.empty()) {
              res.status = 400;
              res.set_content(error_response("INVALID_REQUEST", "missing path param: id",
                                             get_request_id(req)).dump(),
                              "application/json");
              return;
            }
            if (mgr.status(id) != "live") {
              res.status = 404;
              res.set_content(error_response("NOT_FOUND", "stream not found",
                                             get_request_id(req)).dump(),
                              "application/json");
              return;
            }
            res.set_content(json{{"stream_id", id},
                                 {"playback_url", mgr.playback_endpoint(id)}}.dump(),
                            "application/json");
            res.status = 200;
          });

  // Health: liveness (process alive)
  svr.Get("/health", [](const httplib::Request&, httplib::Response& res) {
    res.set_content(R"({"status":"ok"})", "application/json");
    res.status = 200;
  });

  svr.Get("/health/live", [](const httplib::Request&, httplib::Response& res) {
    res.set_content(R"({"status":"ok"})", "application/json");
    res.status = 200;
  });

  // Readiness (accepting traffic, not shutting down)
  svr.Get("/health/ready", [&mgr](const httplib::Request&, httplib::Response& res) {
    if (mgr.is_ready()) {
      res.set_content(R"({"status":"ready"})", "application/json");
      res.status = 200;
    } else {
      res.set_content(R"({"status":"not_ready"})", "application/json");
      res.status = 503;
    }
  });

  // Metrics (Prometheus)
  svr.Get("/metrics", [&mgr, &metrics, &config](const httplib::Request&,
                                                httplib::Response& res) {
    if (!config.metrics_enabled) {
      res.status = 404;
      return;
    }
    res.set_content(metrics.prometheus_text(mgr.count()), "text/plain; charset=utf-8");
    res.status = 200;
  });

  std::signal(SIGINT, signal_handler);
  std::signal(SIGTERM, signal_handler);

  std::cout << "[stream-engine] listening on 0.0.0.0:" << config.port
            << " max_sessions=" << config.max_sessions << std::endl;

  std::thread server_thread([&svr, &config]() {
    svr.listen("0.0.0.0", static_cast<int>(config.port));
  });

  while (g_running) {
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
  }

  std::cout << "[stream-engine] shutdown signal received, draining..." << std::endl;
  mgr.set_shutting_down();
  svr.stop();
  if (server_thread.joinable()) server_thread.join();
  mgr.stop_all();
  std::cout << "[stream-engine] shutdown complete" << std::endl;
  return 0;
}
