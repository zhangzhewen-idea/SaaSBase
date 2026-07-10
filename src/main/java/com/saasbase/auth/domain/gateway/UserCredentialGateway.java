package com.saasbase.auth.domain.gateway;

import com.saasbase.auth.domain.UserCredential;

import java.util.Optional;

public interface UserCredentialGateway {
    Optional<UserCredential> findByTenantCodeAndUsername(String tenantCode, String username);
}
