package com.streaming.engine.session;

import com.streaming.engine.ams.AntMediaService;
import com.streaming.engine.destination.DestinationRelay;
import com.streaming.engine.destination.RtmpPushManager;
import com.streaming.engine.destination.RtmpRelayProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerConcurrencyTests {

    private static SessionManager createManager(AntMediaService ams) {
        RtmpRelayProperties props = new RtmpRelayProperties();
        props.setEnabled(false);
        DestinationRelay destinationRelay = new DestinationRelay(new RtmpPushManager(props, command -> {
            throw new AssertionError("ffmpeg should not start in concurrency tests");
        }));
        return new SessionManager(ams, new InMemorySessionRepository(), new LocalStreamMutex(), destinationRelay);
    }

    @Test
    void concurrentStartSameStream_allowsOnlyOneStart() throws Exception {
        CountingAntMediaService ams = new CountingAntMediaService(150);
        SessionManager manager = createManager(ams);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Boolean> startTask = () -> {
            ready.countDown();
            assertTrue(go.await(1, TimeUnit.SECONDS), "workers did not start together");
            return manager.start("stream-1", "key-1", "test") == SessionManager.StartResult.STARTED;
        };

        Future<Boolean> f1 = pool.submit(startTask);
        Future<Boolean> f2 = pool.submit(startTask);

        assertTrue(ready.await(1, TimeUnit.SECONDS), "start workers not ready");
        go.countDown();

        boolean r1 = f1.get(2, TimeUnit.SECONDS);
        boolean r2 = f2.get(2, TimeUnit.SECONDS);
        pool.shutdownNow();

        int successCount = (r1 ? 1 : 0) + (r2 ? 1 : 0);
        assertEquals(1, successCount, "exactly one concurrent start should succeed");
        assertEquals(1, ams.startCalls.get(), "AMS start should be called only once");
    }

    @Test
    void concurrentStartDifferentStreams_runsInParallel() throws Exception {
        ParallelProbeAntMediaService ams = new ParallelProbeAntMediaService();
        SessionManager manager = createManager(ams);

        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<Boolean> f1 = pool.submit(() -> manager.start("stream-a", "key-a", "A") == SessionManager.StartResult.STARTED);
        Future<Boolean> f2 = pool.submit(() -> manager.start("stream-b", "key-b", "B") == SessionManager.StartResult.STARTED);

        assertTrue(
                ams.entered.await(1, TimeUnit.SECONDS),
                "different stream starts should enter AMS concurrently"
        );

        ams.release.countDown();

        assertTrue(f1.get(2, TimeUnit.SECONDS));
        assertTrue(f2.get(2, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertTrue(ams.maxConcurrent.get() >= 2, "expected concurrent AMS calls for different streams");
    }

    @Test
    void stopRemovesSession_allowingCleanRestart() {
        CountingAntMediaService ams = new CountingAntMediaService(0);
        SessionManager manager = createManager(ams);

        assertEquals(SessionManager.StartResult.STARTED, manager.start("stream-1", "key-1", "title"));
        assertEquals(SessionManager.StopStatus.STOPPED, manager.stop("stream-1").status());
        assertEquals(SessionManager.StartResult.STARTED, manager.start("stream-1", "key-2", "title-2"));
    }

    @Test
    void stopMissingSession_returnsNotFound() {
        SessionManager manager = createManager(new CountingAntMediaService(0));
        assertEquals(SessionManager.StopStatus.NOT_FOUND, manager.stop("missing").status());
    }

    @Test
    void stopFailurePreservesRunningState() {
        SessionManager manager = createManager(new FailingStopAntMediaService());

        assertEquals(SessionManager.StartResult.STARTED, manager.start("stream-x", "key-x", "title"));
        assertEquals(SessionManager.StopStatus.AMS_FAILED, manager.stop("stream-x").status());
        assertEquals(1, manager.list().size(), "session should still exist when upstream stop fails");
    }

    private static class CountingAntMediaService implements AntMediaService {
        private final long delayMs;
        private final AtomicInteger startCalls = new AtomicInteger();

        private CountingAntMediaService(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public boolean startStream(String streamId, String title) {
            startCalls.incrementAndGet();
            sleep(delayMs);
            return true;
        }

        @Override
        public boolean stopStream(String streamId) {
            return true;
        }
    }

    private static class ParallelProbeAntMediaService implements AntMediaService {
        private final CountDownLatch entered = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();

        @Override
        public boolean startStream(String streamId, String title) {
            int active = inFlight.incrementAndGet();
            maxConcurrent.updateAndGet(existing -> Math.max(existing, active));
            entered.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    return false;
                }
                return true;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override
        public boolean stopStream(String streamId) {
            return true;
        }
    }

    private static class FailingStopAntMediaService implements AntMediaService {
        @Override
        public boolean startStream(String streamId, String title) {
            return true;
        }

        @Override
        public boolean stopStream(String streamId) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail("unexpected interruption");
        }
    }
}
