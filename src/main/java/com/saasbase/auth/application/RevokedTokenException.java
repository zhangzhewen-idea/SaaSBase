package com.saasbase.auth.application;

public class RevokedTokenException extends RuntimeException {
    public RevokedTokenException() {
        super("token revoked");
    }
}
