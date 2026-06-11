package com.redbank.pfm.application;

public class PfmNotFoundException extends RuntimeException {
    public PfmNotFoundException(String message) {
        super(message);
    }
}
