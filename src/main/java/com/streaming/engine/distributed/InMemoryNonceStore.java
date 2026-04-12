package com.streaming.engine.distributed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "stream-engine.distributed", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryNonceStore implements NonceStore {

    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    @Override
    public boolean tryRegister(String nonce, long nowEpochSec, long ttlSeconds) {
        seenNonces.entrySet().removeIf(entry -> nowEpochSec - entry.getValue() > ttlSeconds);
        return seenNonces.putIfAbsent(nonce, nowEpochSec) == null;
    }
}
