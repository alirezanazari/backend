package com.redbank.iam.domain;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    DEAD_LETTER
}
