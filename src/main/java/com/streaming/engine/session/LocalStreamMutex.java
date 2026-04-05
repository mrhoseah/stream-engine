package com.streaming.engine.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(prefix = "stream-engine.distributed", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalStreamMutex implements StreamMutex {

    private final Map<String, LockEntry> streamLocks = new ConcurrentHashMap<>();

    @Override
    public <T> T executeWithStreamLock(String streamId, Supplier<T> action) {
        LockEntry entry = streamLocks.compute(streamId, (id, existing) -> {
            LockEntry lockEntry = existing == null ? new LockEntry() : existing;
            lockEntry.holders.incrementAndGet();
            return lockEntry;
        });

        entry.lock.lock();
        try {
            return action.get();
        } finally {
            entry.lock.unlock();
            streamLocks.computeIfPresent(streamId, (id, existing) -> {
                int remaining = existing.holders.decrementAndGet();
                if (remaining == 0 && !existing.lock.isLocked() && !existing.lock.hasQueuedThreads()) {
                    return null;
                }
                return existing;
            });
        }
    }

    private static class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger holders = new AtomicInteger();
    }
}
