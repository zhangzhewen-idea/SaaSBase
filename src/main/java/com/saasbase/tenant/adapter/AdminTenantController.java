package com.saasbase.tenant.adapter;

import com.saasbase.common.api.ApiResponse;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.tenant.application.TenantApplicationService;
import com.saasbase.tenant.application.dto.TenantResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tenant")
public class AdminTenantController {
    private final TenantApplicationService tenantApplicationService;

    public AdminTenantController(TenantApplicationService tenantApplicationService) {
        this.tenantApplicationService = tenantApplicationService;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('tenant:profile:read')")
    public ApiResponse<TenantResponse> profile() {
        return ApiResponse.ok(tenantApplicationService.currentProfile(TenantContextHolder.require().tenantId()));
    }
}
