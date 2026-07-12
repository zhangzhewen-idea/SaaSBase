package com.saasbase.auth.domain.gateway;

public interface RefreshTokenStore {
    void save(String tokenId, String tokenValue, long expiresAtEpochSecond);

    String find(String tokenId);

    boolean exists(String tokenId);

    void revoke(String tokenId);
}
