package com.saasbase.auth.application;

public class TokenStateUnavailableException extends RuntimeException {
    public TokenStateUnavailableException(Throwable cause) {
        super("token state unavailable", cause);
    }
}
