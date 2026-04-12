# Ant Media Server Integration Guide

This guide explains how to set up and integrate Ant Media Server (AMS) as the streaming backend for the stream-engine service.

---

## 1. Install Ant Media Server (Community Edition)


### a. Download and Install (Linux)

```sh
wget https://github.com/ant-media/Ant-Media-Server/releases/download/ams-v2.8.2/ant-media-server-community-2.8.2.zip
unzip ant-media-server-community-2.8.2.zip
cd ant-media-server
# For Community Edition (no license required):
sudo ./install.sh
# For Enterprise Edition (requires license key):
# sudo ./install_ant-media-server.sh -l 'your-license-key'
```

> **Note:** The Community Edition does not require a license key. The Enterprise Edition requires a valid license key during installation.

- See the [official installation guide](https://github.com/ant-media/Ant-Media-Server/wiki/Installation) for more OS options.

### b. Start the Server

```sh
sudo service antmedia start
```

- Default web panel: http://localhost:5080

---

## 2. Configure AMS for REST API and Streaming

- REST API docs: https://resources.antmedia.io/rest/
- Default REST API port: 5080
- Example: `http://localhost:5080/LiveApp/rest/v2/broadcasts`

### a. Create a Stream via REST API

```sh
curl -X POST "http://localhost:5080/LiveApp/rest/v2/broadcasts/create" \
     -H "Content-Type: application/json" \
     -d '{"name":"test-stream"}'
```

### b. Ingest RTMP

- RTMP URL: `rtmp://<server-ip>/LiveApp/<streamId>`

### c. Playback HLS/WebRTC

- HLS: `http://<server-ip>:5080/LiveApp/streams/<streamId>.m3u8`
- WebRTC: Use AMS web panel or API

---

## 3. Integrate with stream-engine (Java)


stream-engine now uses a dedicated AntMediaService for all streaming operations. Configure AMS integration in `src/main/resources/application.yml`:

```yaml
ams:
  base-url: http://localhost:5080/LiveApp/rest/v2
  start-path: /broadcasts/create
  stop-path: /broadcasts/stop
  # ...other options as needed
```

No Red5 configuration is required. All stream/session management is handled via AntMediaService and AMS REST API.

---


## 4. Integrate Recastly with stream-engine (Recommended)

To use Ant Media Server securely and flexibly with Recastly, route all stream/session management through stream-engine:

### a. Configure stream-engine to use Ant Media Server

In `src/main/resources/application.yml` (or via environment variables):

```yaml
red5:
  base-url: http://localhost:5080/LiveApp/rest/v2
  start-path: /broadcasts/create
  stop-path: /broadcasts/stop
  # Adjust other settings as needed
```

This ensures stream-engine's SessionManager and Red5Service interact with AMS REST API endpoints for stream management.

### b. Expose stream-engine API to Recastly

- Make sure stream-engine is accessible to Recastly (set `STREAM_ENGINE_BASE_URL` in Recastly to your stream-engine endpoint, e.g., `http://localhost:8085`).
- Recastly should use stream-engine's REST endpoints (e.g., `/api/v1/sessions`) to create, start, stop, and query streams.

### c. Why use this architecture?

- Centralizes business logic, security, and access control in stream-engine
- Allows for future backend changes without affecting Recastly
- Enables custom features, logging, and analytics

### d. Example: Creating a stream from Recastly

Recastly sends a POST request to stream-engine:

```
POST http://<stream-engine-host>:8085/api/v1/sessions
Content-Type: application/json
{
  "streamId": "my-stream-id",
  "title": "My Stream Title"
}
```

stream-engine then calls AMS via its REST API to create/manage the stream.

---

## 5. (Alternative) Direct Recastly → AMS integration

You may connect Recastly directly to AMS for simple setups, but this is not recommended for production or enterprise use. See above for the preferred architecture.

---

## 5. Security & Production Notes

- Secure AMS admin panel and REST API (see AMS docs).
- Use HTTPS in production.
- Monitor AMS health and logs.

---

## References
- [Ant Media Server GitHub](https://github.com/ant-media/Ant-Media-Server)
- [REST API Docs](https://resources.antmedia.io/rest/)
- [Webhook Docs](https://github.com/ant-media/Ant-Media-Server/wiki/Webhook-Integration)
