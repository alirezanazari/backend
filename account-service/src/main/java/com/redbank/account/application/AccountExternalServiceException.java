package com.redbank.account.application;

public class AccountExternalServiceException extends RuntimeException {
    public AccountExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
