package com.streaming.engine.recastly;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class RecastlyWebhookInvoker {

    private static final Logger log = LoggerFactory.getLogger(RecastlyWebhookInvoker.class);

    private final RecastlyClient recastlyClient;

    public RecastlyWebhookInvoker(RecastlyClient recastlyClient) {
        this.recastlyClient = recastlyClient;
    }

    @Retry(name = "recastlyWebhook")
    @CircuitBreaker(name = "recastlyWebhook", fallbackMethod = "sendWebhookFallback")
    public void sendWebhook(String event, String streamId, String streamKey, Integer durationSec) {
        try {
            recastlyClient.webhook(event, streamId, streamKey, durationSec);
        } catch (RestClientException ex) {
            log.warn("Recastly webhook attempt failed event={} streamId={} reason={}", event, streamId, ex.getMessage());
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    private void sendWebhookFallback(String event, String streamId, String streamKey, Integer durationSec, Throwable t) {
        log.error("Recastly webhook exhausted retries event={} streamId={} reason={}", event, streamId, t.getMessage());
    }
}
