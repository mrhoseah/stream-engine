package com.streaming.engine.destination;

/**
 * Active simulcast destination with FFmpeg push health.
 */
public record RegisteredDestination(
        StreamDestination destination,
        PushStatus pushStatus,
        String errorMessage
) {
}
