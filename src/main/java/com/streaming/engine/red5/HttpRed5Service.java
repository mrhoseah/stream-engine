package com.streaming.engine.red5;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "red5", name = "mode", havingValue = "http", matchIfMissing = true)
public class HttpRed5Service implements Red5Service {

    private static final Logger log = LoggerFactory.getLogger(HttpRed5Service.class);

    private final Red5Properties properties;
    private final RestClient restClient;

    public HttpRed5Service(Red5Properties properties, CloseableHttpClient outboundHttpClient) {
        this.properties = properties;
        HttpComponentsClientHttpRequestFactory rf = new HttpComponentsClientHttpRequestFactory(outboundHttpClient);
        rf.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        rf.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(rf)
                .build();
    }

    @Override
    @CircuitBreaker(name = "red5", fallbackMethod = "startStreamFallback")
    @Retry(name = "red5")
    public boolean startStream(String streamId, String title) {
        if (properties.getBaseUrl().isBlank()) {
            log.error("red5.base-url is required for HTTP mode");
            return false;
        }
        if (!isHttpsAllowed(properties.getBaseUrl(), properties.isEnforceHttps())) {
            log.error("Red5 start denied because base URL is not HTTPS while red5.enforce-https=true");
            return false;
        }
        postToRed5(
                properties.getStartPath(),
                Map.of("streamId", streamId, "title", title),
                "start",
                streamId
        );
        return true;
    }

    @SuppressWarnings("unused")
    private boolean startStreamFallback(String streamId, String title, Throwable t) {
        log.warn("Red5 start degraded streamId={} reason={}", streamId, t.getMessage());
        return false;
    }

    @Override
    @CircuitBreaker(name = "red5", fallbackMethod = "stopStreamFallback")
    @Retry(name = "red5")
    public boolean stopStream(String streamId) {
        if (properties.getBaseUrl().isBlank()) {
            log.error("red5.base-url is required for HTTP mode");
            return false;
        }
        if (!isHttpsAllowed(properties.getBaseUrl(), properties.isEnforceHttps())) {
            log.error("Red5 stop denied because base URL is not HTTPS while red5.enforce-https=true");
            return false;
        }
        postToRed5(
                properties.getStopPath(),
                Map.of("streamId", streamId),
                "stop",
                streamId
        );
        return true;
    }

    @SuppressWarnings("unused")
    private boolean stopStreamFallback(String streamId, Throwable t) {
        log.warn("Red5 stop degraded streamId={} reason={}", streamId, t.getMessage());
        return false;
    }

    private void postToRed5(String path, Map<String, Object> payload, String action, String streamId) {
        String url = joinUrl(properties.getBaseUrl(), path);
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!properties.getSharedSecret().isBlank()) {
                            headers.set(properties.getSecretHeader(), properties.getSharedSecret());
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Red5 {} failed for streamId={} url={} reason={}", action, streamId, url, ex.getMessage());
            throw ex;
        }
    }

    private static String joinUrl(String baseUrl, String path) {
        String left = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String right = path.startsWith("/") ? path : "/" + path;
        return left + right;
    }

    private static boolean isHttpsAllowed(String baseUrl, boolean enforceHttps) {
        if (!enforceHttps) {
            return true;
        }
        return baseUrl != null && baseUrl.startsWith("https://");
    }
}
