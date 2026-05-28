package com.redbank.account.application;

import com.redbank.account.config.AccountProperties;
import org.springframework.stereotype.Component;

@Component
public class KycJobCatalog {
    private final AccountProperties accountProperties;

    public KycJobCatalog(AccountProperties accountProperties) {
        this.accountProperties = accountProperties;
    }

    public boolean exists(String jobId) {
        return accountProperties.allowedJobIds().contains(jobId);
    }
}
