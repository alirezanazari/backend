package com.redbank.pfm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PfmServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PfmServiceApplication.class, args);
    }
}
