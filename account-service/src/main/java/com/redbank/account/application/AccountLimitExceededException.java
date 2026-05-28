package com.redbank.account.application;

public class AccountLimitExceededException extends RuntimeException {
    public AccountLimitExceededException(String message) {
        super(message);
    }
}
