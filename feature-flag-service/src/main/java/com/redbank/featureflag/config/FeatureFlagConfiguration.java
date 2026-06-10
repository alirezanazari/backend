package com.redbank.featureflag.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeatureFlagConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
