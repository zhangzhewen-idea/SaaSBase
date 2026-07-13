package com.saasbase.iam.domain.gateway;

import com.saasbase.iam.domain.UserAuthState;

public interface UserSessionGateway {
    void put(UserAuthState state);
}
