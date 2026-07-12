package com.saasbase.tenant.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(@NotBlank @Size(max = 128) String tenantName) {}
