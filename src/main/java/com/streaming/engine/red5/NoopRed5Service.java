package com.streaming.engine.red5;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "red5", name = "mode", havingValue = "noop")
public class NoopRed5Service implements Red5Service {

    private static final Logger log = LoggerFactory.getLogger(NoopRed5Service.class);

    @Override
    public boolean startStream(String streamId, String title) {
        log.info("Noop Red5 start streamId={} title={}", streamId, title);
        return true;
    }

    @Override
    public boolean stopStream(String streamId) {
        log.info("Noop Red5 stop streamId={}", streamId);
        return true;
    }
}
