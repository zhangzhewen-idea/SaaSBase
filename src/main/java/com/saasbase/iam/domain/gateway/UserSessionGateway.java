package com.saasbase.iam.domain.gateway;

import com.saasbase.iam.domain.UserAuthState;

import java.util.Optional;
import java.util.function.Supplier;

public interface UserSessionGateway {
    void put(UserAuthState state);

    Optional<UserAuthState> get(long tenantId, long userId);

    UserAuthState getOrLoad(long tenantId, long userId, Supplier<UserAuthState> loader);
}
