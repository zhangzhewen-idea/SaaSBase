package com.saasbase.auth.domain.gateway;

public interface TokenRevocationStore {
    boolean isRevoked(String tokenId);
}
