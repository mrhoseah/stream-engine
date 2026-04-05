package com.streaming.engine.recastly;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RecastlyWebhookPublisher {

    private final RecastlyWebhookInvoker invoker;

    public RecastlyWebhookPublisher(RecastlyWebhookInvoker invoker) {
        this.invoker = invoker;
    }

    @Async("webhooks")
    public void publishStreamStarted(String streamId, String streamKey) {
        invoker.sendWebhook("stream.started", streamId, streamKey, null);
    }

    @Async("webhooks")
    public void publishStreamEnded(String streamId, String streamKey, Integer durationSec) {
        invoker.sendWebhook("stream.ended", streamId, streamKey, durationSec);
    }
}
