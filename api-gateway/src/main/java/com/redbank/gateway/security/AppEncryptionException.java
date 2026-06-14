package com.redbank.gateway.security;

public class AppEncryptionException extends RuntimeException {
    public AppEncryptionException(String message) {
        super(message);
    }

    public AppEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
