package com.redbank.account.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redbank.account")
public record AccountProperties(
        String ibanCountryCode,
        String bankCode,
        String defaultCurrency,
        int maxProfilesPerUser,
        List<String> allowedJobIds
) {
    public AccountProperties {
        if (ibanCountryCode == null || ibanCountryCode.isBlank()) {
            ibanCountryCode = "IR";
        }
        ibanCountryCode = ibanCountryCode.toUpperCase();
        if (bankCode == null || bankCode.isBlank()) {
            bankCode = "0620";
        }
        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            defaultCurrency = "IRR";
        }
        defaultCurrency = defaultCurrency.toUpperCase();
        if (maxProfilesPerUser <= 0) {
            maxProfilesPerUser = 5;
        }
        if (allowedJobIds == null || allowedJobIds.isEmpty()) {
            allowedJobIds = List.of("job-engineer", "job-student", "job-business-owner", "job-other");
        } else if (allowedJobIds.size() == 1 && allowedJobIds.getFirst().contains(",")) {
            allowedJobIds = Arrays.stream(allowedJobIds.getFirst().split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        } else {
            allowedJobIds = allowedJobIds.stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
    }
}
