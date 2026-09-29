package com.redbank.pfm.application;

public class PfmFeatureDisabledException extends RuntimeException {
    public PfmFeatureDisabledException(String message) {
        super(message);
    }
}
