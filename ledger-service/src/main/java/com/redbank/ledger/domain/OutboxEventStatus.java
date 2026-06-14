package com.redbank.ledger.domain;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    DEAD_LETTER
}
