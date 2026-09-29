package com.redbank.pfm.application;

public class PfmInsufficientFundsException extends RuntimeException {
    public PfmInsufficientFundsException(String message) {
        super(message);
    }
}
