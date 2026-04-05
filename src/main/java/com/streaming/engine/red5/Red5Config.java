package com.streaming.engine.red5;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Red5Properties.class)
public class Red5Config {
}
