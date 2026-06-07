package com.redbank.card.application;

public class CardConflictException extends RuntimeException {
    public CardConflictException(String message) {
        super(message);
    }
}
