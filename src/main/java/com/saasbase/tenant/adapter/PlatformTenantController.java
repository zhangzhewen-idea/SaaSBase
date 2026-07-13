package com.saasbase.tenant.adapter;

import com.saasbase.common.api.ApiResponse;
import com.saasbase.tenant.application.TenantApplicationService;
import com.saasbase.tenant.application.dto.CreateTenantRequest;
import com.saasbase.tenant.application.dto.TenantQuery;
import com.saasbase.tenant.application.dto.TenantResponse;
import com.saasbase.tenant.application.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {
    private final TenantApplicationService tenantApplicationService;

    public PlatformTenantController(TenantApplicationService tenantApplicationService) {
        this.tenantApplicationService = tenantApplicationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('platform:tenant:create')")
    public ApiResponse<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request,
                                              @RequestParam("operatorId") Long operatorId) {
        return ApiResponse.ok(tenantApplicationService.create(request, operatorId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('platform:tenant:read')")
    public ApiResponse<com.saasbase.common.api.PageResponse<TenantResponse>> page(TenantQuery query) {
        return ApiResponse.ok(tenantApplicationService.page(query));
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('platform:tenant:read')")
    public ApiResponse<TenantResponse> get(@PathVariable Long tenantId) {
        return ApiResponse.ok(tenantApplicationService.get(tenantId));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('platform:tenant:update')")
    public ApiResponse<TenantResponse> update(@PathVariable Long tenantId,
                                              @Valid @RequestBody UpdateTenantRequest request,
                                              @RequestParam("operatorId") Long operatorId) {
        return ApiResponse.ok(tenantApplicationService.update(tenantId, request, operatorId));
    }

    @PostMapping("/{tenantId}/enable")
    @PreAuthorize("hasAuthority('platform:tenant:enable')")
    public ApiResponse<TenantResponse> enable(@PathVariable Long tenantId,
                                              @RequestParam("operatorId") Long operatorId) {
        return ApiResponse.ok(tenantApplicationService.enable(tenantId, operatorId));
    }

    @PostMapping("/{tenantId}/disable")
    @PreAuthorize("hasAuthority('platform:tenant:disable')")
    public ApiResponse<TenantResponse> disable(@PathVariable Long tenantId,
                                               @RequestParam("operatorId") Long operatorId) {
        return ApiResponse.ok(tenantApplicationService.disable(tenantId, operatorId));
    }
}
