package com.streaming.engine.recastly;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

@Service
public class RecastlyClient {

    private static final Logger log = LoggerFactory.getLogger(RecastlyClient.class);

    private final RecastlyProperties properties;
    private final RestClient restClient;

    public RecastlyClient(RecastlyProperties properties, CloseableHttpClient outboundHttpClient) {
        this.properties = properties;
        HttpComponentsClientHttpRequestFactory rf = new HttpComponentsClientHttpRequestFactory(outboundHttpClient);
        rf.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        rf.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(rf)
                .build();
    }

    @CircuitBreaker(name = "recastly", fallbackMethod = "validateKeyFallback")
    @Retry(name = "recastly")
    public ValidationResult validateKey(String streamKey) {
        if (!isConfigured()) {
            return ValidationResult.allow();
        }
        if (!isHttpsAllowed(properties.getBaseUrl(), properties.isEnforceHttps())) {
            log.error("Recastly validate-key denied because base URL is not HTTPS while enforce-https=true");
            return new ValidationResult(false, "insecure recastly endpoint");
        }
        String url = joinUrl(properties.getBaseUrl(), properties.getValidatePath());
        try {
            ValidationResponse response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.set(properties.getSecretHeader(), properties.getSharedSecret()))
                    .body(Map.of("streamKey", streamKey))
                    .retrieve()
                    .body(ValidationResponse.class);
            if (response == null || !response.valid()) {
                return new ValidationResult(false, response == null ? "stream key rejected" : response.message());
            }
            return ValidationResult.allow();
        } catch (RestClientException ex) {
            log.error("Recastly key validation request failed: {}", ex.getMessage());
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    private ValidationResult validateKeyFallback(String streamKey, Throwable t) {
        return new ValidationResult(false, "validation temporarily unavailable");
    }

    /**
     * Synchronous webhook POST. Callers that need resilience should use {@link RecastlyWebhookInvoker}.
     */
    public void webhook(String event, String streamId, String streamKey, Integer durationSec) {
        if (!isConfigured()) {
            return;
        }
        if (!isHttpsAllowed(properties.getBaseUrl(), properties.isEnforceHttps())) {
            log.error("Recastly webhook denied because base URL is not HTTPS while enforce-https=true event={} streamId={}", event, streamId);
            return;
        }
        String url = joinUrl(properties.getBaseUrl(), properties.getWebhookPath());
        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set(properties.getSecretHeader(), properties.getSharedSecret()))
                .body(Map.of(
                        "event", event,
                        "streamId", streamId,
                        "streamKey", streamKey,
                        "durationSec", durationSec
                ))
                .retrieve()
                .toBodilessEntity();
    }

    public boolean isInboundAuthorized(String incomingSecret) {
        String configuredSecret = properties.getSharedSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            if (properties.isInboundAuthRequired()) {
                log.warn("Inbound auth denied because RECASTLY_SHARED_SECRET is blank while inbound auth is required");
                return false;
            }
            return true;
        }
        if (incomingSecret == null) {
            return false;
        }
        byte[] expected = configuredSecret.trim().getBytes(StandardCharsets.UTF_8);
        byte[] provided = incomingSecret.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    private boolean isConfigured() {
        return properties.isEnabled()
                && !properties.getBaseUrl().isBlank()
                && !properties.getSharedSecret().isBlank();
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

    public record ValidationResult(boolean valid, String message) {
        private static ValidationResult allow() {
            return new ValidationResult(true, "");
        }
    }

    private record ValidationResponse(boolean valid, String message) {
    }
}
