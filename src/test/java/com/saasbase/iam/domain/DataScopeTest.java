package com.saasbase.iam.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeTest {

    @Test
    void empty_scopes_fall_back_to_self() {
        assertThat(DataScope.merge(Set.of(), true)).isEqualTo(DataScope.SELF);
    }

    @Test
    void merge_selects_the_widest_scope() {
        assertThat(DataScope.merge(Set.of(DataScope.DEPT_ONLY, DataScope.ALL, DataScope.SELF), true))
                .isEqualTo(DataScope.ALL);
    }

    @Test
    void department_scope_falls_back_to_self_when_user_has_no_department() {
        assertThat(DataScope.merge(Set.of(DataScope.DEPT_AND_CHILDREN, DataScope.DEPT_ONLY), false))
                .isEqualTo(DataScope.SELF);
    }
}
