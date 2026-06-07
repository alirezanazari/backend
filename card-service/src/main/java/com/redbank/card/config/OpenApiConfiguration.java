package com.redbank.card.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI cardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("red-bank Card Service")
                        .version("0.1.0")
                        .description("Card lifecycle APIs."));
    }
}
