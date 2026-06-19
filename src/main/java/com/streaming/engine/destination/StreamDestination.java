package com.streaming.engine.destination;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RTMP restream target forwarded from Recastly session start payload.
 */
public record StreamDestination(
        String platform,
        @JsonProperty("rtmp_url") String rtmpUrl,
        @JsonProperty("stream_key") String streamKey,
        boolean active
) {
    public boolean isUsable() {
        return active && rtmpUrl != null && !rtmpUrl.isBlank();
    }
}
