package com.redbank.kyc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI kycOpenApi() {
        return new OpenAPI().info(new Info()
                .title("red-bank KYC Service API")
                .version("v1")
                .description("Customer verification, jobs, and referral APIs."));
    }
}
