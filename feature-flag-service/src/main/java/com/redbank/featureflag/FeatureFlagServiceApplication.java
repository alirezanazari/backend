package com.redbank.featureflag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FeatureFlagServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeatureFlagServiceApplication.class, args);
    }
}
