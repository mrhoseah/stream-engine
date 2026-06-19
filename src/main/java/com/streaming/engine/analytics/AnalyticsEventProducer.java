package com.streaming.engine.analytics;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Properties;
import java.util.concurrent.Future;

@Service
public class AnalyticsEventProducer {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventProducer.class);

    @Value("${analytics.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${analytics.kafka.topic:stream-lifecycle-events}")
    private String topic;

    private KafkaProducer<String, String> producer;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(props);
        log.info("AnalyticsEventProducer initialized for topic {} at {}", topic, bootstrapServers);
    }

    public void sendEvent(String eventJson) {
        try {
            String enriched = enrichWithTraceparent(eventJson);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, enriched);
            Future<RecordMetadata> future = producer.send(record);
            RecordMetadata metadata = future.get();
            log.info("Sent analytics event to {} partition {} offset {}", metadata.topic(), metadata.partition(), metadata.offset());
        } catch (Exception e) {
            log.error("Failed to send analytics event: {}", e.getMessage(), e);
        }
    }

    static String enrichWithTraceparent(String eventJson) {
        if (eventJson == null || eventJson.isBlank()) {
            return eventJson;
        }
        String traceparent = MDC.get(com.streaming.engine.api.TraceContextFilter.MDC_KEY);
        if (traceparent == null || traceparent.isBlank()) {
            return eventJson;
        }
        String trimmed = eventJson.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return eventJson;
        }
        String escaped = traceparent.replace("\\", "\\\\").replace("\"", "\\\"");
        if (trimmed.length() == 2) {
            return "{\"traceparent\":\"" + escaped + "\"}";
        }
        return trimmed.substring(0, trimmed.length() - 1) + ",\"traceparent\":\"" + escaped + "\"}";
    }

    @PreDestroy
    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
