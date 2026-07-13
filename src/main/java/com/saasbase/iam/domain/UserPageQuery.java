package com.saasbase.iam.domain;

public record UserPageQuery(
        int page,
        int size,
        String username,
        Long departmentId,
        UserStatus status,
        String phone) {

    public UserPageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
