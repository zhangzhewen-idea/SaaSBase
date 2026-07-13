package com.saasbase.iam.domain;

import com.saasbase.common.error.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamUserTest {

    @Test
    void disableIncrementsSessionVersion() {
        IamUser user = activeUser(7L);

        user.disable();

        assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.sessionVersion()).isEqualTo(8L);
    }

    @Test
    void enableIncrementsSessionVersion() {
        IamUser user = disabledUser(8L);

        user.enable();

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.sessionVersion()).isEqualTo(9L);
    }

    @Test
    void resetPasswordUpdatesPasswordAndVersion() {
        IamUser user = activeUser(3L);

        user.resetPassword("encoded-password");

        assertThat(user.passwordHash()).isEqualTo("encoded-password");
        assertThat(user.mustChangePassword()).isTrue();
        assertThat(user.sessionVersion()).isEqualTo(4L);
    }

    @Test
    void disableRejectsRepeatedDisable() {
        IamUser user = disabledUser(2L);

        assertThatThrownBy(user::disable)
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(com.saasbase.common.error.ErrorCode.IAM_USER_STATUS_CONFLICT);
    }

    @Test
    void enableRejectsNonDisabledUser() {
        IamUser user = activeUser(1L);

        assertThatThrownBy(user::enable)
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(com.saasbase.common.error.ErrorCode.IAM_USER_STATUS_CONFLICT);
    }

    @Test
    void userPageQueryRejectsInvalidPagination() {
        assertThatThrownBy(() -> new UserPageQuery(0, 10, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserPageQuery(1, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserPageQuery(1, 101, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userPageQueryKeepsOnlyAllowedFilters() {
        UserPageQuery query = new UserPageQuery(1, 20, "alice", 9L, UserStatus.ACTIVE, "13800000000");

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.username()).isEqualTo("alice");
        assertThat(query.departmentId()).isEqualTo(9L);
        assertThat(query.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(query.phone()).isEqualTo("13800000000");
    }

    private static IamUser activeUser(long sessionVersion) {
        return new IamUser(
                1L,
                1L,
                "alice",
                "hash",
                UserStatus.ACTIVE,
                false,
                sessionVersion
        );
    }

    private static IamUser disabledUser(long sessionVersion) {
        return new IamUser(
                1L,
                1L,
                "alice",
                "hash",
                UserStatus.DISABLED,
                false,
                sessionVersion
        );
    }
}
