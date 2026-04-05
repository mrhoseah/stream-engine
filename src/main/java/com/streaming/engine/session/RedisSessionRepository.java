package com.streaming.engine.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "stream-engine.distributed", name = "enabled", havingValue = "true")
public class RedisSessionRepository implements SessionRepository {

    private static final String HASH_KEY = "streaming-engine:sessions";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSessionRepository(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<StreamSession> findAll() {
        Map<Object, Object> entries = redis.opsForHash().entries(HASH_KEY);
        List<StreamSession> out = new ArrayList<>(entries.size());
        for (Object json : entries.values()) {
            if (json == null) {
                continue;
            }
            try {
                out.add(objectMapper.readValue(json.toString(), StreamSession.class));
            } catch (JsonProcessingException ignored) {
                // skip corrupt entries
            }
        }
        return out;
    }

    @Override
    public Optional<StreamSession> findByStreamId(String streamId) {
        Object raw = redis.opsForHash().get(HASH_KEY, streamId);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw.toString(), StreamSession.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public void save(StreamSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redis.opsForHash().put(HASH_KEY, session.streamId(), json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize session", e);
        }
    }

    @Override
    public void deleteByStreamId(String streamId) {
        redis.opsForHash().delete(HASH_KEY, streamId);
    }
}
