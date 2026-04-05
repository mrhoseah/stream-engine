package com.streaming.engine.red5stub;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping("/stream-engine")
@ConditionalOnProperty(prefix = "red5", name = "stub-enabled", havingValue = "true")
public class Red5StubController {

    private final Set<String> runningStreams = ConcurrentHashMap.newKeySet();

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@Valid @RequestBody Red5StartRequest request) {
        boolean added = runningStreams.add(request.streamId());
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "accepted", added,
                "streamId", request.streamId(),
                "title", request.title(),
                "startedAt", Instant.now().toString()
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(@Valid @RequestBody Red5StopRequest request) {
        boolean removed = runningStreams.remove(request.streamId());
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "accepted", removed,
                "streamId", request.streamId(),
                "stoppedAt", Instant.now().toString()
        ));
    }

    public record Red5StartRequest(
            @NotBlank String streamId,
            @NotBlank String title
    ) {
    }

    public record Red5StopRequest(@NotBlank String streamId) {
    }
}
