package com.streaming.engine.destination;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtmpPushManagerTest {

    @Test
    void startAndStopProcessWhenEnabled() throws Exception {
        RtmpRelayProperties props = new RtmpRelayProperties();
        props.setEnabled(true);
        props.setStartDelayMs(0);
        props.setSourceReadyMaxAttempts(1);
        props.setSourceType("rtmp");

        FakeProcess fakeProcess = new FakeProcess();
        RtmpPushManager manager = new RtmpPushManager(props, command -> fakeProcess);

        StreamDestination destination = new StreamDestination("youtube", "rtmp://out/live", "key", true);
        manager.startPushes("stream-1", List.of(destination));

        assertTrue(awaitPushStatus(manager, destination, PushStatus.RUNNING, 2_000));
        List<RegisteredDestination> registered = manager.registeredDestinations(
                "stream-1",
                List.of(destination)
        );
        assertEquals(PushStatus.RUNNING, registered.get(0).pushStatus());

        manager.stopPushes("stream-1");
        registered = manager.registeredDestinations("stream-1", List.of(destination));
        assertEquals(PushStatus.STOPPED, registered.get(0).pushStatus());
        assertTrue(fakeProcess.destroyed);
    }

    @Test
    void marksDisabledWhenRelayOff() {
        RtmpRelayProperties props = new RtmpRelayProperties();
        props.setEnabled(false);

        RtmpPushManager manager = new RtmpPushManager(props, command -> {
            throw new AssertionError("should not spawn ffmpeg when disabled");
        });

        StreamDestination destination = new StreamDestination("youtube", "rtmp://out/live", "key", true);
        manager.startPushes("stream-1", List.of(destination));

        List<RegisteredDestination> registered = manager.registeredDestinations(
                "stream-1",
                List.of(destination)
        );
        assertEquals(PushStatus.DISABLED, registered.get(0).pushStatus());
    }

    @Test
    void buildFfmpegCommandUsesConfiguredCodecs() {
        RtmpRelayProperties props = new RtmpRelayProperties();
        RtmpPushManager manager = new RtmpPushManager(props, ProcessStarter.DEFAULT);

        List<String> command = manager.buildFfmpegCommand(
                "http://localhost/streams/a.m3u8",
                "rtmp://dest/live/key"
        );

        assertTrue(command.contains("-c:v"));
        assertTrue(command.contains("copy"));
        assertTrue(command.contains("-c:a"));
        assertTrue(command.contains("aac"));
        assertTrue(command.contains("-f"));
        assertTrue(command.contains("flv"));
        assertEquals("rtmp://dest/live/key", command.get(command.size() - 1));
    }

    private static boolean awaitPushStatus(
            RtmpPushManager manager,
            StreamDestination destination,
            PushStatus expected,
            long timeoutMs
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            PushStatus status = manager.registeredDestinations("stream-1", List.of(destination))
                    .get(0)
                    .pushStatus();
            if (status == expected) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static final class FakeProcess extends Process {
        private volatile boolean destroyed;

        FakeProcess() {
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return destroyed ? 143 : 0;
        }

        @Override
        public int exitValue() {
            return destroyed ? 143 : 0;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !destroyed;
        }

        @Override
        public long pid() {
            return 1L;
        }
    }
}
