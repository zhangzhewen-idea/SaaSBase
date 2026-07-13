package com.saasbase.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class UserCommands {
    private UserCommands() {
    }

    public record CreateUserCommand(
            @NotBlank String username,
            @NotBlank String initialPassword,
            @NotBlank String displayName,
            String phone,
            Long primaryDepartmentId,
            @NotEmpty Set<@NotNull Long> roleIds) {
    }

    public record UpdateUserCommand(
            @NotNull Long userId,
            @NotBlank String displayName,
            String phone,
            Long primaryDepartmentId,
            @NotEmpty Set<@NotNull Long> roleIds,
            @NotNull Long version) {
    }

    public record ChangePasswordCommand(
            @NotNull Long userId,
            @NotBlank String newPassword,
            @NotNull Long version) {
    }

    public record ToggleUserCommand(
            @NotNull Long userId,
            @NotNull Long version) {
    }
}
