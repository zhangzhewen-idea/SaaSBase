package com.saasbase.auth.domain.gateway;

import com.saasbase.auth.domain.UserPrincipal;

public interface TokenGateway {
    String issueAccessToken(UserPrincipal principal);

    UserPrincipal parseAccessToken(String token);

    default String parseTokenId(String token) {
        throw new IllegalArgumentException("Token id parsing is not supported");
    }
}
