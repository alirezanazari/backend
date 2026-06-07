package com.redbank.card.application;

public class CardBusinessRuleException extends RuntimeException {
    public CardBusinessRuleException(String message) {
        super(message);
    }
}
