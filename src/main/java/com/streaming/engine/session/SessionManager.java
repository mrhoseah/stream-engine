package com.streaming.engine.session;

import com.streaming.engine.ams.AntMediaService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SessionManager {

    private final AntMediaService antMediaService;
    private final SessionRepository sessions;
    private final StreamMutex streamMutex;

    public SessionManager(AntMediaService antMediaService, SessionRepository sessions, StreamMutex streamMutex) {
        this.antMediaService = antMediaService;
        this.sessions = sessions;
        this.streamMutex = streamMutex;
    }

    public List<StreamSession> list() {
        return sessions.findAll();
    }

    public SessionState status(String streamId) {
        return sessions.findByStreamId(streamId)
                .map(StreamSession::state)
                .orElse(SessionState.STOPPED);
    }

    public StartResult start(String streamId, String streamKey, String title) {
        return streamMutex.executeWithStreamLock(streamId, () -> {
            StreamSession existing = sessions.findByStreamId(streamId).orElse(null);
            if (existing != null && existing.state() == SessionState.RUNNING) {
                return StartResult.ALREADY_RUNNING;
            }
            boolean started = antMediaService.startStream(streamId, title);
            if (!started) {
                return StartResult.RED5_FAILED;
            }
            sessions.save(new StreamSession(streamId, streamKey, title, SessionState.RUNNING, Instant.now()));
            return StartResult.STARTED;
        });
    }

    public StopResult stop(String streamId) {
        return streamMutex.executeWithStreamLock(streamId, () -> {
            StreamSession existing = sessions.findByStreamId(streamId).orElse(null);
            if (existing == null) {
                return StopResult.of(StopStatus.NOT_FOUND);
            }
            if (existing.state() != SessionState.RUNNING) {
                return StopResult.of(StopStatus.NOT_RUNNING);
            }
            boolean stopped = antMediaService.stopStream(streamId);
            if (!stopped) {
                return StopResult.of(StopStatus.RED5_FAILED);
            }
            sessions.deleteByStreamId(streamId);
            int durationSec = (int) java.time.Duration.between(existing.startedAt(), Instant.now()).getSeconds();
            return StopResult.stopped(existing.streamKey(), Math.max(durationSec, 0));
        });
    }

    public enum StartResult {
        STARTED,
        ALREADY_RUNNING,
        RED5_FAILED
    }

    public record StopResult(StopStatus status, String streamKey, Integer durationSec) {
        public static StopResult stopped(String streamKey, Integer durationSec) {
            return new StopResult(StopStatus.STOPPED, streamKey, durationSec);
        }

        public static StopResult of(StopStatus status) {
            return new StopResult(status, null, null);
        }
    }

    public enum StopStatus {
        STOPPED,
        NOT_FOUND,
        NOT_RUNNING,
        RED5_FAILED
    }
}
