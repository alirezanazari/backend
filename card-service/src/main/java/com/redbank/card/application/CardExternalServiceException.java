package com.redbank.card.application;

public class CardExternalServiceException extends RuntimeException {
    public CardExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
