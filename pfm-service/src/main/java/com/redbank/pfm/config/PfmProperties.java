package com.redbank.pfm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.pfm")
public record PfmProperties(
        String defaultCurrency,
        String defaultLanguage
) {
    public PfmProperties {
        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            defaultCurrency = "IRR";
        }
        if (defaultLanguage == null || defaultLanguage.isBlank()) {
            defaultLanguage = "en-US";
        }
    }
}
