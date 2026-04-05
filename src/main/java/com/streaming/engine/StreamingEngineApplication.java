package com.streaming.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
        exclude = {
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class
        }
)
@EnableAsync
public class StreamingEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamingEngineApplication.class, args);
    }
}
