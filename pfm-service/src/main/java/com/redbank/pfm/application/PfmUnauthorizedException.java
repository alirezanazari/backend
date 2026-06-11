package com.redbank.pfm.application;

public class PfmUnauthorizedException extends RuntimeException {
    public PfmUnauthorizedException(String message) {
        super(message);
    }
}
