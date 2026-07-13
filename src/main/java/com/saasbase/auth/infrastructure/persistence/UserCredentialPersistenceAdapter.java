package com.saasbase.auth.infrastructure.persistence;

import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

@Repository
public class UserCredentialPersistenceAdapter implements UserCredentialGateway {
    private final UserCredentialMapper mapper;

    public UserCredentialPersistenceAdapter(UserCredentialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserCredential> findByTenantCodeAndUsername(String tenantCode, String username) {
        return mapper.findByTenantCodeAndUsername(tenantCode, username)
                .map(record -> new UserCredential(record.userId(), record.tenantId(), record.username(),
                        record.passwordHash(), toPermissions(record.permissions()),
                        record.sessionVersion(), Boolean.TRUE.equals(record.mustChangePassword()), record.status()));
    }

    private Set<String> toPermissions(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.asList(permissions.split(",")));
    }
}
