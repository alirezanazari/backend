package com.redbank.transfer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI transferOpenApi() {
        return new OpenAPI().info(new Info()
                .title("red-bank Transfer Service API")
                .version("v1")
                .description("Idempotent transfer orchestration and successful transfer history."));
    }
}
