package com.streaming.engine.destination;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RtmpPushManager {

    private static final Logger log = LoggerFactory.getLogger(RtmpPushManager.class);

    private final RtmpRelayProperties properties;
    private final ProcessStarter processStarter;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PushHandle>> byStream = new ConcurrentHashMap<>();

    @Autowired
    public RtmpPushManager(RtmpRelayProperties properties) {
        this(properties, ProcessStarter.DEFAULT);
    }

    /** Visible for tests that inject a fake {@link ProcessStarter}. */
    public RtmpPushManager(RtmpRelayProperties properties, ProcessStarter processStarter) {
        this.properties = properties;
        this.processStarter = processStarter;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "rtmp-push");
            t.setDaemon(true);
            return t;
        });
    }

    public void startPushes(String streamId, List<StreamDestination> destinations) {
        stopPushes(streamId);
        if (streamId == null || streamId.isBlank() || destinations == null || destinations.isEmpty()) {
            return;
        }
        ConcurrentHashMap<String, PushHandle> handles = new ConcurrentHashMap<>();
        for (StreamDestination destination : destinations) {
            String key = destinationKey(destination);
            PushHandle handle = new PushHandle(destination);
            handles.put(key, handle);
            if (properties.isEnabled()) {
                executor.submit(() -> runPush(streamId, handle));
            } else {
                handle.pushStatus.set(PushStatus.DISABLED);
            }
        }
        byStream.put(streamId, handles);
    }

    public void stopPushes(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return;
        }
        ConcurrentHashMap<String, PushHandle> removed = byStream.remove(streamId);
        if (removed == null) {
            return;
        }
        for (PushHandle handle : removed.values()) {
            handle.cancel();
            destroyProcess(handle);
            if (handle.pushStatus.get() == PushStatus.RUNNING || handle.pushStatus.get() == PushStatus.STARTING) {
                handle.pushStatus.set(PushStatus.STOPPED);
            }
        }
    }

    public List<RegisteredDestination> registeredDestinations(String streamId, List<StreamDestination> destinations) {
        if (destinations == null || destinations.isEmpty()) {
            return List.of();
        }
        Map<String, PushHandle> handles = byStream.getOrDefault(streamId, new ConcurrentHashMap<>());
        List<RegisteredDestination> result = new ArrayList<>(destinations.size());
        for (StreamDestination destination : destinations) {
            PushHandle handle = handles.get(destinationKey(destination));
            if (handle == null) {
                PushStatus status = properties.isEnabled() ? PushStatus.STOPPED : PushStatus.DISABLED;
                result.add(new RegisteredDestination(destination, status, null));
            } else {
                result.add(new RegisteredDestination(
                        destination,
                        handle.pushStatus.get(),
                        handle.errorMessage.get()
                ));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @PreDestroy
    void shutdown() {
        for (String streamId : List.copyOf(byStream.keySet())) {
            stopPushes(streamId);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void runPush(String streamId, PushHandle handle) {
        if (handle.cancelled.get()) {
            return;
        }
        handle.pushStatus.set(PushStatus.STARTING);
        sleepQuietly(properties.getStartDelayMs());
        if (handle.cancelled.get()) {
            return;
        }

        String sourceUrl = properties.resolveSourceUrl(streamId);
        if (!waitForSourceReady(sourceUrl, handle)) {
            return;
        }
        if (handle.cancelled.get()) {
            return;
        }

        String outputUrl;
        try {
            outputUrl = RtmpUrlHelper.buildOutputUrl(handle.destination.rtmpUrl(), handle.destination.streamKey());
        } catch (IllegalArgumentException e) {
            handle.fail(e.getMessage());
            return;
        }

        List<String> command = buildFfmpegCommand(sourceUrl, outputUrl);
        log.info(
                "starting rtmp push streamId={} platform={} source={} output={}",
                streamId,
                handle.destination.platform(),
                maskUrl(sourceUrl),
                maskUrl(outputUrl)
        );

        try {
            Process process = processStarter.start(command);
            handle.process.set(process);
            handle.pushStatus.set(PushStatus.RUNNING);
            drainStderrAsync(process, streamId, handle.destination.platform());
            int exitCode = process.waitFor();
            if (handle.cancelled.get()) {
                handle.pushStatus.set(PushStatus.STOPPED);
                return;
            }
            if (exitCode != 0) {
                handle.fail("ffmpeg exited with code " + exitCode);
            } else {
                handle.pushStatus.set(PushStatus.STOPPED);
            }
        } catch (IOException e) {
            handle.fail("failed to start ffmpeg: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handle.pushStatus.set(PushStatus.STOPPED);
        }
    }

    private boolean waitForSourceReady(String sourceUrl, PushHandle handle) {
        if ("rtmp".equalsIgnoreCase(properties.getSourceType())) {
            return true;
        }
        for (int attempt = 0; attempt < properties.getSourceReadyMaxAttempts(); attempt++) {
            if (handle.cancelled.get()) {
                return false;
            }
            if (isHlsSourceReachable(sourceUrl)) {
                return true;
            }
            sleepQuietly(properties.getSourceReadyPollIntervalMs());
        }
        handle.fail("source not ready: " + maskUrl(sourceUrl));
        return false;
    }

    private boolean isHlsSourceReachable(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3_000);
            connection.setReadTimeout(3_000);
            int code = connection.getResponseCode();
            return code >= 200 && code < 400;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    List<String> buildFfmpegCommand(String sourceUrl, String outputUrl) {
        List<String> command = new ArrayList<>();
        command.add(properties.getFfmpegPath());
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-reconnect");
        command.add("1");
        command.add("-reconnect_streamed");
        command.add("1");
        command.add("-reconnect_delay_max");
        command.add("5");
        command.add("-i");
        command.add(sourceUrl);
        command.add("-c:v");
        command.add(properties.getVideoCodec());
        command.add("-c:a");
        command.add(properties.getAudioCodec());
        if (!"copy".equalsIgnoreCase(properties.getAudioCodec())) {
            command.add("-b:a");
            command.add(properties.getAudioBitrate());
        }
        command.add("-f");
        command.add("flv");
        command.add(outputUrl);
        return List.copyOf(command);
    }

    private void drainStderrAsync(Process process, String streamId, String platform) {
        executor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("ffmpeg streamId={} platform={} {}", streamId, platform, line);
                }
            } catch (IOException e) {
                log.debug("ffmpeg stderr drain ended streamId={} platform={}: {}", streamId, platform, e.getMessage());
            }
        });
    }

    private static void destroyProcess(PushHandle handle) {
        Process process = handle.process.getAndSet(null);
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String destinationKey(StreamDestination destination) {
        String platform = destination.platform() == null ? "unknown" : destination.platform();
        String url = destination.rtmpUrl() == null ? "" : destination.rtmpUrl();
        return platform + "|" + url;
    }

    private static String maskUrl(String url) {
        if (url == null || url.length() <= 24) {
            return url;
        }
        return url.substring(0, 20) + "…";
    }

    static final class PushHandle {
        private final StreamDestination destination;
        private final AtomicReference<PushStatus> pushStatus = new AtomicReference<>(PushStatus.STARTING);
        private final AtomicReference<String> errorMessage = new AtomicReference<>();
        private final AtomicReference<Process> process = new AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();

        PushHandle(StreamDestination destination) {
            this.destination = destination;
        }

        void cancel() {
            cancelled.set(true);
        }

        void fail(String message) {
            errorMessage.set(message);
            pushStatus.set(PushStatus.FAILED);
            log.warn(
                    "rtmp push failed platform={} error={}",
                    destination.platform(),
                    message
            );
        }
    }
}
