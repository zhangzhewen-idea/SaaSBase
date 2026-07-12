package com.saasbase.auth.domain.gateway;

public interface RefreshTokenStore {
    void save(String tokenId, String tokenValue, long expiresAtEpochSecond);

    String find(String tokenId);

    boolean exists(String tokenId);

    void revoke(String tokenId);

    default boolean rotate(
            String oldTokenId,
            String oldTokenValue,
            String newTokenId,
            String newTokenValue,
            long expiresAtEpochSecond) {
        if (find(oldTokenId) == null) {
            return false;
        }
        revoke(oldTokenId);
        save(newTokenId, newTokenValue, expiresAtEpochSecond);
        return true;
    }
}
