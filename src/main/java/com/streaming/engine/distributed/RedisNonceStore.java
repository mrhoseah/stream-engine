package com.streaming.engine.distributed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "stream-engine.distributed", name = "enabled", havingValue = "true")
public class RedisNonceStore implements NonceStore {

    private static final String KEY_PREFIX = "streaming-engine:nonce:";

    private final StringRedisTemplate redis;

    public RedisNonceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryRegister(String nonce, long nowEpochSec, long ttlSeconds) {
        long ttl = Math.max(ttlSeconds, 1L);
        Boolean ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + nonce, "1", Duration.ofSeconds(ttl));
        return Boolean.TRUE.equals(ok);
    }
}
