package com.streaming.engine.destination;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rtmp-relay")
public class RtmpRelayProperties {

    /**
     * When false, destinations are tracked but no FFmpeg processes are started.
     */
    private boolean enabled = false;

    private String ffmpegPath = "ffmpeg";

    /**
     * HLS playback URL template; {@code {streamId}} is replaced with the session id.
     */
    private String sourceHlsUrlTemplate = "http://localhost:5080/LiveApp/streams/{streamId}.m3u8";

    /**
     * Optional RTMP ingest URL template used when {@link #sourceType} is {@code rtmp}.
     */
    private String sourceRtmpUrlTemplate = "rtmp://localhost/LiveApp/{streamId}";

    /**
     * {@code hls} (default) or {@code rtmp}.
     */
    private String sourceType = "hls";

    /** Delay before the first FFmpeg launch attempt (allows AMS to publish). */
    private long startDelayMs = 3_000;

    private long sourceReadyPollIntervalMs = 2_000;

    private int sourceReadyMaxAttempts = 30;

    private String videoCodec = "copy";

    private String audioCodec = "aac";

    private String audioBitrate = "128k";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getSourceHlsUrlTemplate() {
        return sourceHlsUrlTemplate;
    }

    public void setSourceHlsUrlTemplate(String sourceHlsUrlTemplate) {
        this.sourceHlsUrlTemplate = sourceHlsUrlTemplate;
    }

    public String getSourceRtmpUrlTemplate() {
        return sourceRtmpUrlTemplate;
    }

    public void setSourceRtmpUrlTemplate(String sourceRtmpUrlTemplate) {
        this.sourceRtmpUrlTemplate = sourceRtmpUrlTemplate;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public long getStartDelayMs() {
        return startDelayMs;
    }

    public void setStartDelayMs(long startDelayMs) {
        this.startDelayMs = startDelayMs;
    }

    public long getSourceReadyPollIntervalMs() {
        return sourceReadyPollIntervalMs;
    }

    public void setSourceReadyPollIntervalMs(long sourceReadyPollIntervalMs) {
        this.sourceReadyPollIntervalMs = sourceReadyPollIntervalMs;
    }

    public int getSourceReadyMaxAttempts() {
        return sourceReadyMaxAttempts;
    }

    public void setSourceReadyMaxAttempts(int sourceReadyMaxAttempts) {
        this.sourceReadyMaxAttempts = sourceReadyMaxAttempts;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public void setVideoCodec(String videoCodec) {
        this.videoCodec = videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    public void setAudioCodec(String audioCodec) {
        this.audioCodec = audioCodec;
    }

    public String getAudioBitrate() {
        return audioBitrate;
    }

    public void setAudioBitrate(String audioBitrate) {
        this.audioBitrate = audioBitrate;
    }

    public String resolveSourceUrl(String streamId) {
        String template = "rtmp".equalsIgnoreCase(sourceType)
                ? sourceRtmpUrlTemplate
                : sourceHlsUrlTemplate;
        return template.replace("{streamId}", streamId);
    }
}
