package com.redbank.featureflag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI featureFlagOpenApi() {
        return new OpenAPI().info(new Info()
                .title("red-bank Feature Flag Service API")
                .version("v1")
                .description("Runtime operational feature controls and audit logging."));
    }
}
