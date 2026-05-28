package com.redbank.account.application;

public class AccountConflictException extends RuntimeException {
    public AccountConflictException(String message) {
        super(message);
    }
}
