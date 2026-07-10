package com.saasbase.auth.infrastructure.persistence;

import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserCredentialMapper implements UserCredentialGateway {
    @Override
    public Optional<UserCredential> findByTenantCodeAndUsername(String tenantCode, String username) {
        return Optional.empty();
    }
}
