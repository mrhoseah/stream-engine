#include "stream-engine/stream_engine.hpp"
#include <httplib.h>
#include <nlohmann/json.hpp>
#include <iostream>
#include <map>
#include <mutex>
#include <string>

using json = nlohmann::json;

struct Session {
  std::string stream_id;
  std::string stream_key;
  std::string title;
  stream_engine::StreamEngine engine;
  bool active{false};
};

class SessionManager {
 public:
  bool start(const json& cfg) {
    std::string stream_id = cfg.value("stream_id", "");
    std::string stream_key = cfg.value("stream_key", "");
    std::string title = cfg.value("title", "stream");

    if (stream_id.empty()) return false;

    std::lock_guard<std::mutex> lock(mu_);
    if (sessions_.count(stream_id)) return false;

    auto& s = sessions_[stream_id];
    s.stream_id = stream_id;
    s.stream_key = stream_key.empty() ? stream_id : stream_key;
    s.title = title;

    if (!s.engine.initialize()) return false;
    if (!s.engine.start(s.stream_key)) return false;

    s.active = true;
    return true;
  }

  void stop(const std::string& stream_id) {
    std::lock_guard<std::mutex> lock(mu_);
    auto it = sessions_.find(stream_id);
    if (it == sessions_.end()) return;
    it->second.engine.stop();
    it->second.active = false;
    sessions_.erase(it);
  }

  std::vector<json> list() const {
    std::lock_guard<std::mutex> lock(mu_);
    std::vector<json> out;
    for (const auto& [id, s] : sessions_) {
      if (s.active) {
        out.push_back(json{{"stream_id", s.stream_id},
                           {"stream_key", s.stream_key},
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

 private:
  mutable std::mutex mu_;
  std::map<std::string, Session> sessions_;
};

int main(int argc, char* argv[]) {
  int port = 9090;
  if (argc > 1) port = std::stoi(argv[1]);

  SessionManager mgr;
  httplib::Server svr;

  svr.Post("/api/v1/sessions", [&mgr](const httplib::Request& req, httplib::Response& res) {
    try {
      json body = json::parse(req.body);
      if (mgr.start(body)) {
        res.set_content(R"({"ok":true})", "application/json");
        res.status = 200;
      } else {
        res.set_content(R"({"ok":false,"error":"failed to start"})", "application/json");
        res.status = 400;
      }
    } catch (const std::exception& e) {
      res.set_content(json{{"ok", false}, {"error", e.what()}}.dump(), "application/json");
      res.status = 400;
    }
  });

  svr.Post("/api/v1/sessions/([^/]+)/stop", [&mgr](const httplib::Request& req, httplib::Response& res) {
    std::string id = req.path_params.at("1");
    mgr.stop(id);
    res.set_content(R"({"ok":true})", "application/json");
    res.status = 200;
  });

  svr.Get("/api/v1/sessions", [&mgr](const httplib::Request&, httplib::Response& res) {
    auto list = mgr.list();
    res.set_content(json(list).dump(), "application/json");
    res.status = 200;
  });

  svr.Get("/api/v1/sessions/([^/]+)/status", [&mgr](const httplib::Request& req, httplib::Response& res) {
    std::string id = req.path_params.at("1");
    std::string st = mgr.status(id);
    res.set_content(json{{"status", st}}.dump(), "application/json");
    res.status = 200;
  });

  svr.Get("/health", [](const httplib::Request&, httplib::Response& res) {
    res.set_content(R"({"status":"ok"})", "application/json");
    res.status = 200;
  });

  std::cout << "stream-engine-server listening on :" << port << std::endl;
  svr.listen("0.0.0.0", static_cast<int>(port));
  return 0;
}
