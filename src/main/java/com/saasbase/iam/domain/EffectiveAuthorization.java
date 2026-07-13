package com.saasbase.iam.domain;

import java.util.Objects;
import java.util.Set;

public record EffectiveAuthorization(Set<String> permissions, DataScope dataScope) {

    public EffectiveAuthorization {
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
        Objects.requireNonNull(dataScope, "dataScope must not be null");
    }
}
