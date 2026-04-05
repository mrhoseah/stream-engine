package com.streaming.engine.session;

import java.time.Instant;

public record StreamSession(
        String streamId,
        String streamKey,
        String title,
        SessionState state,
        Instant startedAt
) {
}
