package com.redbank.gateway.security;

public class GatewayTokenException extends RuntimeException {
    public GatewayTokenException() {
        super("Unauthorized");
    }
}
