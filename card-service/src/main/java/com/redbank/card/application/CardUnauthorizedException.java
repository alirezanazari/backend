package com.redbank.card.application;

public class CardUnauthorizedException extends RuntimeException {
    public CardUnauthorizedException(String message) {
        super(message);
    }
}
