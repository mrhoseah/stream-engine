package com.streaming.engine.red5;

public interface Red5Service {

    boolean startStream(String streamId, String title);

    boolean stopStream(String streamId);
}
