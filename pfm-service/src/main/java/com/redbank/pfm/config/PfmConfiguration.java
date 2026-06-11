package com.redbank.pfm.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PfmConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
