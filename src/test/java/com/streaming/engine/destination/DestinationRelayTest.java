package com.streaming.engine.destination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestinationRelayTest {

    private static DestinationRelay relay() {
        RtmpRelayProperties props = new RtmpRelayProperties();
        props.setEnabled(false);
        return new DestinationRelay(new RtmpPushManager(props, command -> {
            throw new AssertionError("ffmpeg should not start when relay disabled");
        }));
    }

    @Test
    void activateFiltersInactiveAndBlankUrls() {
        DestinationRelay relay = relay();
        relay.activate(
                "stream-1",
                List.of(
                        new StreamDestination("youtube", "rtmp://a/live", "key", true),
                        new StreamDestination("twitch", "", "key", true),
                        new StreamDestination("facebook", "rtmp://b/live", "key", false)
                )
        );

        List<StreamDestination> list = relay.list("stream-1");
        assertEquals(1, list.size());
        assertEquals("youtube", list.get(0).platform());

        List<RegisteredDestination> withStatus = relay.listWithStatus("stream-1");
        assertEquals(PushStatus.DISABLED, withStatus.get(0).pushStatus());
    }

    @Test
    void deactivateClearsDestinations() {
        DestinationRelay relay = relay();
        relay.activate("stream-1", List.of(new StreamDestination("youtube", "rtmp://a/live", "key", true)));
        relay.deactivate("stream-1");
        assertTrue(relay.list("stream-1").isEmpty());
    }
}
