package com.saasbase.iam.domain;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

public enum DataScope {
    ALL(4, false),
    DEPT_AND_CHILDREN(3, true),
    DEPT_ONLY(2, true),
    SELF(1, false);

    private final int rank;
    private final boolean requiresDepartment;

    DataScope(int rank, boolean requiresDepartment) {
        this.rank = rank;
        this.requiresDepartment = requiresDepartment;
    }

    public int rank() {
        return rank;
    }

    public static DataScope merge(Set<DataScope> scopes, boolean hasDepartment) {
        Objects.requireNonNull(scopes, "scopes must not be null");
        DataScope result = scopes.stream()
                .map(scope -> Objects.requireNonNull(scope, "scope must not be null"))
                .max(Comparator.comparingInt(DataScope::rank))
                .orElse(SELF);
        return result.requiresDepartment && !hasDepartment ? SELF : result;
    }
}
