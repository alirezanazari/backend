package com.redbank.ledger.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
