package com.streaming.engine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI streamEngineOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stream Engine API")
                        .description("Session lifecycle API for Ant Media Server integration. Secured inbound calls require secret and signature headers.")
                        .version("v1"));
    }
}
