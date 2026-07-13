package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TenantAdminPersistenceAdapterTest {
    private TenantAdminMapper mapper;
    private TenantAdminPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = mock(TenantAdminMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        adapter = new TenantAdminPersistenceAdapter(mapper, encoder, Clock.systemUTC());
    }

    @Test
    void rejectsInvalidInputsBeforeUsingMapper() {
        Object[][] invalid = {
                {null, "admin", "管理员", "secret", 1L},
                {0L, "admin", "管理员", "secret", 1L},
                {-1L, "admin", "管理员", "secret", 1L},
                {1L, null, "管理员", "secret", 1L},
                {1L, "  ", "管理员", "secret", 1L},
                {1L, "a".repeat(65), "管理员", "secret", 1L},
                {1L, "admin", null, "secret", 1L},
                {1L, "admin", "  ", "secret", 1L},
                {1L, "admin", "名".repeat(129), "secret", 1L},
                {1L, "admin", "管理员", null, 1L},
                {1L, "admin", "管理员", "  ", 1L},
                {1L, "admin", "管理员", "secret", null},
                {1L, "admin", "管理员", "secret", 0L},
                {1L, "admin", "管理员", "secret", -1L}
        };

        for (Object[] args : invalid) {
            assertThatThrownBy(() -> adapter.initialize((Long) args[0], (String) args[1], (String) args[2],
                    (String) args[3], (Long) args[4])).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void mapsOnlyNamedUsernameConstraintToBusinessError() {
        var duplicate = duplicate("Duplicate entry 'admin' for key 'iam_user.UK_IAM_USER_TENANT_USERNAME'");
        doThrow(duplicate).when(mapper).insertUser(any());

        assertThatThrownBy(() -> adapter.initialize(1L, "admin", "管理员", "secret", 1L))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.IAM_USERNAME_CONFLICT));
    }

    @Test
    void rethrowsDuplicateFromOtherConstraint() {
        for (String message : new String[]{"Duplicate entry '1' for key 'PRIMARY'",
                "Duplicate entry 'TENANT_ADMIN' for key 'uk_iam_role_tenant_code'",
                "Duplicate entry 'admin'"}) {
            mapper = mock(TenantAdminMapper.class);
            adapter = new TenantAdminPersistenceAdapter(mapper, mock(PasswordEncoder.class), Clock.systemUTC());
            var duplicate = duplicate(message);
            doThrow(duplicate).when(mapper).insertUser(any());

            assertThatThrownBy(() -> adapter.initialize(1L, "admin", "管理员", "secret", 1L))
                    .isSameAs(duplicate);
        }
    }

    private static DuplicateKeyException duplicate(String message) {
        return new DuplicateKeyException("outer", new SQLException(message));
    }
}
