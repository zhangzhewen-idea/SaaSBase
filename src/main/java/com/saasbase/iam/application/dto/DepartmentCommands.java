package com.saasbase.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class DepartmentCommands {
    private DepartmentCommands() {
    }

    public record CreateDepartmentCommand(
            @NotNull Long parentId,
            @NotBlank String deptCode,
            @NotBlank String deptName,
            long sortOrder) {
    }

    public record UpdateDepartmentCommand(
            @NotBlank String deptName,
            long sortOrder,
            @NotNull Long version) {
    }

    public record MoveDepartmentCommand(
            @NotNull Long newParentId,
            @NotNull Long version) {
    }

    public record ToggleDepartmentCommand(
            @NotNull Long version) {
    }

    public record TransferDepartmentCommand(
            @NotNull Long departmentId,
            @NotNull Long version) {
    }
}
