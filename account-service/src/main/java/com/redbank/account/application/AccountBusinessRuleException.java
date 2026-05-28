package com.redbank.account.application;

public class AccountBusinessRuleException extends RuntimeException {
    public AccountBusinessRuleException(String message) {
        super(message);
    }
}
