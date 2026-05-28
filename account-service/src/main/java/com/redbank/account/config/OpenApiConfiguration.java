package com.redbank.account.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI accountOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("red-bank Account Service")
                        .version("0.1.0")
                        .description("Profiles and account metadata APIs."));
    }
}
