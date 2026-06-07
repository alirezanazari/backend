package com.redbank.card.application;

public record EligibleAccount(
        String accountNumber,
        String currency
) {
}
