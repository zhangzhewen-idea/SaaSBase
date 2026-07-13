package com.saasbase.iam.domain;

public record UserAuthState(
        Long tenantId,
        Long userId,
        UserStatus status,
        long sessionVersion,
        boolean mustChangePassword) {

    public static UserAuthState from(IamUser user) {
        return new UserAuthState(user.tenantId(), user.id(), user.status(), user.sessionVersion(), user.mustChangePassword());
    }
}
