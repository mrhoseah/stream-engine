package com.streaming.engine.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "stream-engine.distributed", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemorySessionRepository implements SessionRepository {

    private final Map<String, StreamSession> sessions = new ConcurrentHashMap<>();

    @Override
    public List<StreamSession> findAll() {
        return sessions.values().stream().toList();
    }

    @Override
    public Optional<StreamSession> findByStreamId(String streamId) {
        return Optional.ofNullable(sessions.get(streamId));
    }

    @Override
    public void save(StreamSession session) {
        sessions.put(session.streamId(), session);
    }

    @Override
    public void deleteByStreamId(String streamId) {
        sessions.remove(streamId);
    }
}
