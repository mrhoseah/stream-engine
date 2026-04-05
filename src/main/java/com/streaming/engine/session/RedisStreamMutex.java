package com.streaming.engine.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(prefix = "stream-engine.distributed", name = "enabled", havingValue = "true")
public class RedisStreamMutex implements StreamMutex {

    private static final String LOCK_PREFIX = "streaming-engine:lock:stream:";
    private static final int LOCK_TTL_SEC = 30;
    private static final int ACQUIRE_ATTEMPTS = 80;
    private static final long ACQUIRE_SLEEP_MS = 50L;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> unlockScript;

    public RedisStreamMutex(StringRedisTemplate redis) {
        this.redis = redis;
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setResultType(Long.class);
        this.unlockScript.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"
        );
    }

    @Override
    public <T> T executeWithStreamLock(String streamId, Supplier<T> action) {
        String key = LOCK_PREFIX + streamId;
        String token = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            for (int i = 0; i < ACQUIRE_ATTEMPTS; i++) {
                Boolean ok = redis.opsForValue().setIfAbsent(key, token, java.time.Duration.ofSeconds(LOCK_TTL_SEC));
                if (Boolean.TRUE.equals(ok)) {
                    locked = true;
                    break;
                }
                try {
                    Thread.sleep(ACQUIRE_SLEEP_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while acquiring stream lock", ex);
                }
            }
            if (!locked) {
                throw new IllegalStateException("Could not acquire distributed lock for streamId=" + streamId);
            }
            return action.get();
        } finally {
            if (locked) {
                redis.execute(unlockScript, Collections.singletonList(key), token);
            }
        }
    }
}
