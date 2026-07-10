package com.saasbase.auth.application;

import com.saasbase.auth.domain.gateway.TokenRevocationStore;

public class TokenRevocationPolicy {
    private final TokenRevocationStore tokenRevocationStore;

    public TokenRevocationPolicy(TokenRevocationStore tokenRevocationStore) {
        this.tokenRevocationStore = tokenRevocationStore;
    }

    public void ensureNotRevoked(String tokenId) {
        try {
            if (tokenRevocationStore.isRevoked(tokenId)) {
                throw new RevokedTokenException();
            }
        } catch (RevokedTokenException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TokenStateUnavailableException(exception);
        }
    }
}
