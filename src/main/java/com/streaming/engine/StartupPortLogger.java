package com.streaming.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupPortLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupPortLogger.class);
    private final Environment environment;

    public StartupPortLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener
    public void onWebServerReady(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String tlsEnabled = environment.getProperty("server.ssl.enabled", "false");
        String scheme = "true".equalsIgnoreCase(tlsEnabled) ? "https" : "http";
        log.info("Stream Engine is running on {}://localhost:{}", scheme, port);
    }
}
