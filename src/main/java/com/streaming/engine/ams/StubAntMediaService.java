package com.streaming.engine.ams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-op AMS client for CI and local dev without Ant Media Server.
 */
@Service
@ConditionalOnProperty(prefix = "ams", name = "stub-enabled", havingValue = "true")
public class StubAntMediaService implements AntMediaService {

    private static final Logger log = LoggerFactory.getLogger(StubAntMediaService.class);

    @Override
    public boolean startStream(String streamId, String title) {
        log.info("AMS stub start streamId={} title={}", streamId, title);
        return true;
    }

    @Override
    public boolean stopStream(String streamId) {
        log.info("AMS stub stop streamId={}", streamId);
        return true;
    }
}
