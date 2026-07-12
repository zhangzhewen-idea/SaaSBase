package com.saasbase.auth.domain.gateway;

public interface TokenRevocationStore {
    default void revoke(String tokenId, long expiresAtEpochSecond) {
    }

    boolean isRevoked(String tokenId);
}
