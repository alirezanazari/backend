package com.redbank.account.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
