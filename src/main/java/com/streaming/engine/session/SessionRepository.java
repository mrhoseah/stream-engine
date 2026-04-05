package com.streaming.engine.session;

import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    List<StreamSession> findAll();

    Optional<StreamSession> findByStreamId(String streamId);

    void save(StreamSession session);

    void deleteByStreamId(String streamId);
}
