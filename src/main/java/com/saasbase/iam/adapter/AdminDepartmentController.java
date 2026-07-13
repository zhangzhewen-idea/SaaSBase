package com.saasbase.iam.adapter;

import com.saasbase.common.api.ApiResponse;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.iam.application.DepartmentApplicationService;
import com.saasbase.iam.application.dto.DepartmentCommands.CreateDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.MoveDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.ToggleDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.UpdateDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentViews.DepartmentTreeView;
import com.saasbase.iam.application.dto.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/admin/depts")
public class AdminDepartmentController {
    private final DepartmentApplicationService service;

    public AdminDepartmentController(DepartmentApplicationService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('tenant:dept:read')")
    public ApiResponse<List<DepartmentTreeView>> tree() {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.tree(context.tenantId()));
    }

    @GetMapping("/{deptId}")
    @PreAuthorize("hasAuthority('tenant:dept:read')")
    public ApiResponse<DepartmentTreeView> get(@PathVariable long deptId) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.get(context.tenantId(), deptId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:dept:create')")
    public ApiResponse<DepartmentTreeView> create(@Valid @RequestBody CreateDepartmentCommand command) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.create(context.tenantId(), context.userId(), command));
    }

    @PutMapping("/{deptId}")
    @PreAuthorize("hasAuthority('tenant:dept:update')")
    public ApiResponse<DepartmentTreeView> update(@PathVariable long deptId, @Valid @RequestBody UpdateDepartmentCommand command) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.update(context.tenantId(), context.userId(), deptId, command));
    }

    @PostMapping("/{deptId}/move")
    @PreAuthorize("hasAuthority('tenant:dept:move')")
    public ApiResponse<DepartmentTreeView> move(@PathVariable long deptId, @Valid @RequestBody MoveDepartmentCommand command) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.move(context.tenantId(), context.userId(), deptId, command));
    }

    @PostMapping("/{deptId}/disable")
    @PreAuthorize("hasAuthority('tenant:dept:disable')")
    public ApiResponse<DepartmentTreeView> disable(@PathVariable long deptId, @Valid @RequestBody ToggleDepartmentCommand command) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.disable(context.tenantId(), context.userId(), deptId, command));
    }

    @PostMapping("/{deptId}/enable")
    @PreAuthorize("hasAuthority('tenant:dept:enable')")
    public ApiResponse<DepartmentTreeView> enable(@PathVariable long deptId, @Valid @RequestBody ToggleDepartmentCommand command) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.enable(context.tenantId(), context.userId(), deptId, command));
    }

    @DeleteMapping("/{deptId}")
    @PreAuthorize("hasAuthority('tenant:dept:delete')")
    public ApiResponse<Void> delete(@PathVariable long deptId, @RequestParam @NotNull Long version) {
        TenantContext context = TenantContextHolder.require();
        service.delete(context.tenantId(), context.userId(), deptId, new ToggleDepartmentCommand(version));
        return ApiResponse.ok(null);
    }

    @GetMapping("/{deptId}/members")
    @PreAuthorize("hasAuthority('tenant:dept:member:read')")
    public ApiResponse<List<UserView>> members(@PathVariable long deptId,
                                               @RequestParam(defaultValue = "false") boolean descendants) {
        TenantContext context = TenantContextHolder.require();
        return ApiResponse.ok(service.members(context.tenantId(), deptId, descendants));
    }

}
