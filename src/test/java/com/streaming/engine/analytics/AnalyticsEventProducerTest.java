package com.streaming.engine.analytics;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsEventProducerTest {

    @Test
    void enrichWithTraceparent_appendsFieldWhenMdcSet() {
        MDC.put(com.streaming.engine.api.TraceContextFilter.MDC_KEY, "00-abc-def-01");
        try {
            String out = AnalyticsEventProducer.enrichWithTraceparent(
                    "{\"event\":\"stream.ended\",\"streamId\":\"s1\"}"
            );
            assertTrue(out.contains("\"traceparent\":\"00-abc-def-01\""));
            assertTrue(out.endsWith("}"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void enrichWithTraceparent_noopWithoutMdc() {
        MDC.clear();
        String in = "{\"event\":\"stream.started\"}";
        assertEquals(in, AnalyticsEventProducer.enrichWithTraceparent(in));
    }
}
