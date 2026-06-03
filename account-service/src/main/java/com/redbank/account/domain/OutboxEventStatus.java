package com.redbank.account.domain;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
