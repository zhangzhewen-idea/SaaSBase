package com.saasbase.tenant.adapter;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/admin/tenants")
public class AdminTenantController {
    @GetMapping("/ping")
    @PreAuthorize("hasAuthority('tenant:tenant:read')")
    public String ping() {
        return "tenant";
    }
}
