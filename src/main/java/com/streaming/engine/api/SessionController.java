package com.streaming.engine.api;

import com.streaming.engine.destination.DestinationRelay;
import com.streaming.engine.destination.StreamDestination;
import com.streaming.engine.session.SessionManager;
import com.streaming.engine.session.SessionManager.StartResult;
import com.streaming.engine.session.SessionManager.StopResult;
import com.streaming.engine.session.SessionState;
import com.streaming.engine.recastly.RecastlyClient;
import com.streaming.engine.recastly.RecastlyWebhookPublisher;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionManager sessionManager;
    private final RecastlyClient recastlyClient;
    private final RecastlyWebhookPublisher recastlyWebhookPublisher;
    private final InboundSecurityService inboundSecurityService;
    private final com.streaming.engine.analytics.AnalyticsEventProducer analyticsEventProducer;
    private final DestinationRelay destinationRelay;

    public SessionController(
            SessionManager sessionManager,
            RecastlyClient recastlyClient,
            RecastlyWebhookPublisher recastlyWebhookPublisher,
            InboundSecurityService inboundSecurityService,
            com.streaming.engine.analytics.AnalyticsEventProducer analyticsEventProducer,
            DestinationRelay destinationRelay
    ) {
        this.sessionManager = sessionManager;
        this.recastlyClient = recastlyClient;
        this.recastlyWebhookPublisher = recastlyWebhookPublisher;
        this.inboundSecurityService = inboundSecurityService;
        this.analyticsEventProducer = analyticsEventProducer;
        this.destinationRelay = destinationRelay;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest servletRequest) {
        InboundSecurityService.AuthResult auth = inboundSecurityService.authorize(servletRequest);
        if (!auth.allowed()) {
            return ResponseEntity.status(auth.status()).body(Map.of("ok", false, "error", auth.error()));
        }
        List<SessionSummary> sessions = sessionManager.list().stream()
                .map(session -> new SessionSummary(
                        session.streamId(),
                        session.title(),
                        session.state(),
                        session.startedAt()
                ))
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{streamId}/status")
    public ResponseEntity<Map<String, Object>> status(
            HttpServletRequest servletRequest,
            @PathVariable String streamId
    ) {
        InboundSecurityService.AuthResult auth = inboundSecurityService.authorize(servletRequest);
        if (!auth.allowed()) {
            return ResponseEntity.status(auth.status()).body(Map.of("ok", false, "error", auth.error()));
        }
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "streamId", streamId,
                "status", sessionManager.status(streamId).name()
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> start(
            HttpServletRequest servletRequest,
            @Valid @RequestBody StartSessionRequest request
    ) {
        InboundSecurityService.AuthResult auth = inboundSecurityService.authorize(servletRequest);
        if (!auth.allowed()) {
            return ResponseEntity.status(auth.status()).body(Map.of("ok", false, "error", auth.error()));
        }

        String streamKey = request.streamKey() == null || request.streamKey().isBlank()
                ? request.streamId()
                : request.streamKey();
        RecastlyClient.ValidationResult validation = recastlyClient.validateKey(streamKey);
        if (!validation.valid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "ok", false,
                    "error", validation.message().isBlank() ? "Invalid stream key" : validation.message()
            ));
        }

        List<StreamDestination> destinations = request.destinations() == null
                ? List.of()
                : request.destinations().stream()
                        .map(d -> new StreamDestination(d.platform(), d.rtmpUrl(), d.streamKey(), d.active()))
                        .toList();
        StartResult result = sessionManager.start(
                request.streamId(),
                streamKey,
                request.title(),
                destinations
        );
        return switch (result) {
            case STARTED -> {
                recastlyWebhookPublisher.publishStreamStarted(request.streamId(), streamKey);
                String eventJson = String.format(
                        "{\"event\":\"stream.started\",\"streamId\":\"%s\",\"streamKey\":\"%s\",\"title\":\"%s\",\"destinations\":%d,\"timestamp\":%d}",
                        request.streamId(),
                        streamKey,
                        request.title(),
                        destinations.size(),
                        System.currentTimeMillis()
                );
                analyticsEventProducer.sendEvent(eventJson);
                yield ResponseEntity.ok(Map.of("ok", true));
            }
            case ALREADY_RUNNING -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "ok", false,
                    "error", "Session is already running"
            ));
            case AMS_FAILED -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "ok", false,
                    "error", "AMS start failed"
            ));
        };
    }

    @PostMapping("/{streamId}/stop")
    public ResponseEntity<Map<String, Object>> stop(
            HttpServletRequest servletRequest,
            @PathVariable String streamId
    ) {
        InboundSecurityService.AuthResult auth = inboundSecurityService.authorize(servletRequest);
        if (!auth.allowed()) {
            return ResponseEntity.status(auth.status()).body(Map.of("ok", false, "error", auth.error()));
        }
        StopResult result = sessionManager.stop(streamId);
        return switch (result.status()) {
            case STOPPED -> {
                recastlyWebhookPublisher.publishStreamEnded(streamId, result.streamKey(), result.durationSec());
                // Emit analytics event to Kafka
                String eventJson = String.format("{\"event\":\"stream.ended\",\"streamId\":\"%s\",\"streamKey\":\"%s\",\"durationSec\":%d,\"timestamp\":%d}",
                        streamId, result.streamKey(), result.durationSec(), System.currentTimeMillis());
                analyticsEventProducer.sendEvent(eventJson);
                yield ResponseEntity.ok(Map.of("ok", true));
            }
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "ok", false,
                    "error", "Session not found"
            ));
            case NOT_RUNNING -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "ok", false,
                    "error", "Session is not running"
            ));
            case AMS_FAILED -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "ok", false,
                    "error", "AMS stop failed"
            ));
        };
    }

    @GetMapping("/{streamId}/destinations")
    public ResponseEntity<Map<String, Object>> destinations(
            HttpServletRequest servletRequest,
            @PathVariable String streamId
    ) {
        InboundSecurityService.AuthResult auth = inboundSecurityService.authorize(servletRequest);
        if (!auth.allowed()) {
            return ResponseEntity.status(auth.status()).body(Map.of("ok", false, "error", auth.error()));
        }
        List<com.streaming.engine.destination.RegisteredDestination> registered =
                destinationRelay.listWithStatus(streamId);
        long runningCount = registered.stream()
                .filter(r -> r.pushStatus() == com.streaming.engine.destination.PushStatus.RUNNING)
                .count();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "streamId", streamId,
                "destinations", registered,
                "activeCount", registered.size(),
                "runningCount", runningCount
        ));
    }

    @PutMapping("/{streamId}/destinations")
    public ResponseEntity<Map<String, Object>> syncDestinations(
            HttpServletRequest servletRequest,
            @PathVariable String streamId,
            @RequestBody(required = false) SyncDestinationsRequest request
    ) {
        InboundSecurityService.AuthResult auth = inboundSecurityService.authorize(servletRequest);
        if (!auth.allowed()) {
            return ResponseEntity.status(auth.status()).body(Map.of("ok", false, "error", auth.error()));
        }
        List<StreamDestination> destinations = request == null || request.destinations() == null
                ? List.of()
                : request.destinations().stream()
                        .map(d -> new StreamDestination(d.platform(), d.rtmpUrl(), d.streamKey(), d.active()))
                        .toList();
        destinationRelay.activate(streamId, destinations);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "streamId", streamId,
                "activeCount", destinations.size()
        ));
    }

    public record SyncDestinationsRequest(List<DestinationRequest> destinations) {
    }

    public record DestinationRequest(
            String platform,
            @JsonProperty("rtmp_url") @JsonAlias("rtmpUrl") String rtmpUrl,
            @JsonProperty("stream_key") @JsonAlias("streamKey") String streamKey,
            boolean active
    ) {
    }

    public record StartSessionRequest(
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9._:-]+$")
            @JsonProperty("stream_id") @JsonAlias("streamId")
            String streamId,
            @NotBlank
            @Size(max = 200)
            String title,
            @Size(max = 256)
            @Pattern(regexp = "^[A-Za-z0-9._:-]*$")
            @JsonProperty("stream_key") @JsonAlias("streamKey")
            String streamKey,
            List<DestinationRequest> destinations
    ) {
    }

    public record SessionSummary(
            String streamId,
            String title,
            SessionState state,
            Instant startedAt
    ) {
    }
}
