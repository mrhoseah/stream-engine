package com.streaming.engine.ams;

public interface AntMediaService {
    boolean startStream(String streamId, String title);
    boolean stopStream(String streamId);
    // Add more methods as needed for AMS features (e.g., getStreamStatus, listStreams, etc.)
}
