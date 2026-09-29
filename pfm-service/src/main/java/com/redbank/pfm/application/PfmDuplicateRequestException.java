package com.redbank.pfm.application;

public class PfmDuplicateRequestException extends RuntimeException {
    public PfmDuplicateRequestException(String message) {
        super(message);
    }
}
