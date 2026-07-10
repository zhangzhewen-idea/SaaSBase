package com.saasbase.auth.application.dto;

public record LoginResponse(String tokenType, String accessToken, String refreshToken, long expiresInSeconds) {
}
