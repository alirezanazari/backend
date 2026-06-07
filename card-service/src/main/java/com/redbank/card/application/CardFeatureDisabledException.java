package com.redbank.card.application;

public class CardFeatureDisabledException extends RuntimeException {
    public CardFeatureDisabledException(String message) {
        super(message);
    }
}
