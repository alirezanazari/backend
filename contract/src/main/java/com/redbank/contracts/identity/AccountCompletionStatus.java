package com.redbank.contracts.identity;

public enum AccountCompletionStatus {
    REGISTERED,
    DEVICE_VERIFICATION_REQUIRED,
    ACCOUNT_REQUIRED,
    ACCOUNT_RE_OPEN_REQUIRED,
    KYC_REQUIRED,
    KYC_PENDING,
    COMPLETED,
    SUSPENDED
}
