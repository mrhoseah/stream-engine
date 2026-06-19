package com.streaming.engine.destination;

/**
 * Builds outbound RTMP URLs from a base ingest URL and stream key.
 */
public final class RtmpUrlHelper {

    private RtmpUrlHelper() {
    }

    public static String buildOutputUrl(String rtmpUrl, String streamKey) {
        if (rtmpUrl == null || rtmpUrl.isBlank()) {
            throw new IllegalArgumentException("rtmpUrl is required");
        }
        String base = rtmpUrl.trim();
        if (streamKey == null || streamKey.isBlank()) {
            return base;
        }
        String key = streamKey.trim();
        if (base.contains("{stream_key}") || base.contains("{streamKey}")) {
            return base.replace("{stream_key}", key).replace("{streamKey}", key);
        }
        if (base.endsWith("/")) {
            return base + key;
        }
        return base + "/" + key;
    }
}
