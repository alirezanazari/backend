package com.redbank.account.domain;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    DEAD_LETTER
}
