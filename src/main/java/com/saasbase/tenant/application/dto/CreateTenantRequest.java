package com.saasbase.tenant.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(min = 3, max = 64)
                @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]{1,62}[a-z0-9])")
                String tenantCode,
        @NotBlank @Size(max = 128) String tenantName,
        @NotBlank @Size(max = 64) String adminUsername,
        @NotBlank @Size(max = 128) String adminDisplayName,
        @NotBlank @Size(min = 12, max = 72) String initialPassword) {
    @Override
    public String toString() {
        return "CreateTenantRequest[tenantCode=" + tenantCode
                + ", tenantName=" + tenantName
                + ", adminUsername=" + adminUsername
                + ", adminDisplayName=" + adminDisplayName
                + ", initialPassword=<redacted>]";
    }
}
