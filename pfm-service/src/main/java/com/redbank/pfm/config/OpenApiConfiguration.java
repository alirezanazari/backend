package com.redbank.pfm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI pfmOpenApi() {
        return new OpenAPI().info(new Info()
                .title("red-bank PFM Service API")
                .version("v1")
                .description("Personal finance views and vault-backed boxes."));
    }
}
