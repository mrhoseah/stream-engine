package com.streaming.engine.destination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RtmpUrlHelperTest {

    @Test
    void appendsStreamKeyWithSlash() {
        assertEquals(
                "rtmp://a.rtmp.youtube.com/live2/my-key",
                RtmpUrlHelper.buildOutputUrl("rtmp://a.rtmp.youtube.com/live2", "my-key")
        );
    }

    @Test
    void appendsStreamKeyWhenBaseEndsWithSlash() {
        assertEquals(
                "rtmp://live.twitch.tv/app/key123",
                RtmpUrlHelper.buildOutputUrl("rtmp://live.twitch.tv/app/", "key123")
        );
    }

    @Test
    void replacesPlaceholder() {
        assertEquals(
                "rtmp://example.com/live/abc",
                RtmpUrlHelper.buildOutputUrl("rtmp://example.com/live/{stream_key}", "abc")
        );
    }

    @Test
    void returnsBaseWhenKeyBlank() {
        assertEquals("rtmp://example.com/live", RtmpUrlHelper.buildOutputUrl("rtmp://example.com/live", "  "));
    }

    @Test
    void rejectsBlankBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> RtmpUrlHelper.buildOutputUrl("", "key"));
    }
}
