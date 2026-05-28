package com.redbank.account.application;

public class AccountUnauthorizedException extends RuntimeException {
    public AccountUnauthorizedException(String message) {
        super(message);
    }
}
