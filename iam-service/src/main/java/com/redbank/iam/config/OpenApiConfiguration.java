package com.redbank.iam.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI iamOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("red-bank IAM Service")
                        .version("0.1.0")
                        .description("Identity, login, and device verification APIs."));
    }
}
