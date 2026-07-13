package com.saasbase.iam.adapter;

import com.saasbase.common.api.ApiResponse;
import com.saasbase.common.api.PageResponse;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.iam.application.UserApplicationService;
import com.saasbase.iam.application.dto.UserCommands.ChangePasswordCommand;
import com.saasbase.iam.application.dto.UserCommands.CreateUserCommand;
import com.saasbase.iam.application.dto.UserCommands.UpdateUserCommand;
import com.saasbase.iam.application.dto.UserView;
import com.saasbase.iam.domain.UserPageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final UserApplicationService service;

    public AdminUserController(UserApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:user:create')")
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserCommand command) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.create(context.tenantId(), context.userId(), command));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:user:read')")
    public ApiResponse<PageResponse<UserView>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) com.saasbase.iam.domain.UserStatus status,
            @RequestParam(required = false) String phone) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.page(context.tenantId(),
                new UserPageQuery(page, size, username, departmentId, status, phone)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('tenant:user:read')")
    public ApiResponse<UserView> get(@PathVariable long userId) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.get(context.tenantId(), userId));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('tenant:user:update')")
    public ApiResponse<UserView> update(@PathVariable long userId, @Valid @RequestBody UpdateUserCommand command) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.update(context.tenantId(), context.userId(), command));
    }

    @PostMapping("/{userId}/enable")
    @PreAuthorize("hasAuthority('tenant:user:enable')")
    public ApiResponse<UserView> enable(@PathVariable long userId, @NotNull @RequestParam long version) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.enable(context.tenantId(), context.userId(), userId, version));
    }

    @PostMapping("/{userId}/disable")
    @PreAuthorize("hasAuthority('tenant:user:disable')")
    public ApiResponse<UserView> disable(@PathVariable long userId, @NotNull @RequestParam long version) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.disable(context.tenantId(), context.userId(), userId, version));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority('tenant:user:reset-password')")
    public ApiResponse<UserView> resetPassword(@PathVariable long userId, @Valid @RequestBody ChangePasswordCommand command) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.resetPassword(context.tenantId(), context.userId(), userId, command));
    }
}
