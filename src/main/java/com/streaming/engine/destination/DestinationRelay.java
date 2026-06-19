package com.streaming.engine.destination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active simulcast destinations per stream session and manages FFmpeg RTMP push processes.
 */
@Service
public class DestinationRelay {

    private static final Logger log = LoggerFactory.getLogger(DestinationRelay.class);

    private final RtmpPushManager pushManager;
    private final ConcurrentHashMap<String, List<StreamDestination>> activeByStream = new ConcurrentHashMap<>();

    public DestinationRelay(RtmpPushManager pushManager) {
        this.pushManager = pushManager;
    }

    public void activate(String streamId, List<StreamDestination> destinations) {
        if (streamId == null || streamId.isBlank()) {
            return;
        }
        List<StreamDestination> usable = destinations == null
                ? List.of()
                : destinations.stream().filter(StreamDestination::isUsable).toList();
        if (usable.isEmpty()) {
            deactivate(streamId);
            return;
        }
        activeByStream.put(streamId, List.copyOf(usable));
        pushManager.startPushes(streamId, usable);
        for (StreamDestination d : usable) {
            log.info(
                    "destination relay registered streamId={} platform={} rtmpUrl={}",
                    streamId,
                    d.platform(),
                    maskUrl(d.rtmpUrl())
            );
        }
    }

    public void deactivate(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return;
        }
        pushManager.stopPushes(streamId);
        List<StreamDestination> removed = activeByStream.remove(streamId);
        if (removed != null && !removed.isEmpty()) {
            log.info("destination relay cleared streamId={} count={}", streamId, removed.size());
        }
    }

    public List<StreamDestination> list(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return List.of();
        }
        return activeByStream.getOrDefault(streamId, Collections.emptyList());
    }

    public List<RegisteredDestination> listWithStatus(String streamId) {
        List<StreamDestination> destinations = list(streamId);
        return pushManager.registeredDestinations(streamId, destinations);
    }

    private static String maskUrl(String url) {
        if (url == null || url.length() <= 24) {
            return url;
        }
        return url.substring(0, 20) + "…";
    }
}
