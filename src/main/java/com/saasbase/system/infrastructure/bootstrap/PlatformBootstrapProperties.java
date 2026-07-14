package com.saasbase.system.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saasbase.bootstrap")
public class PlatformBootstrapProperties {
    private boolean enabled = false;
    private String platformTenantCode = "platform";
    private String platformTenantName = "平台管理";
    private String platformAdminUsername = "platform-admin";
    private String platformAdminDisplayName = "超级管理员";
    private String platformAdminPassword = "Platform123!";
    private String tenantCode = "demo";
    private String tenantName = "演示租户";
    private String tenantAdminUsername = "admin";
    private String tenantAdminDisplayName = "管理员";
    private String tenantAdminPassword = "Tenant123!";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPlatformTenantCode() {
        return platformTenantCode;
    }

    public void setPlatformTenantCode(String platformTenantCode) {
        this.platformTenantCode = platformTenantCode;
    }

    public String getPlatformTenantName() {
        return platformTenantName;
    }

    public void setPlatformTenantName(String platformTenantName) {
        this.platformTenantName = platformTenantName;
    }

    public String getPlatformAdminUsername() {
        return platformAdminUsername;
    }

    public void setPlatformAdminUsername(String platformAdminUsername) {
        this.platformAdminUsername = platformAdminUsername;
    }

    public String getPlatformAdminDisplayName() {
        return platformAdminDisplayName;
    }

    public void setPlatformAdminDisplayName(String platformAdminDisplayName) {
        this.platformAdminDisplayName = platformAdminDisplayName;
    }

    public String getPlatformAdminPassword() {
        return platformAdminPassword;
    }

    public void setPlatformAdminPassword(String platformAdminPassword) {
        this.platformAdminPassword = platformAdminPassword;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getTenantAdminUsername() {
        return tenantAdminUsername;
    }

    public void setTenantAdminUsername(String tenantAdminUsername) {
        this.tenantAdminUsername = tenantAdminUsername;
    }

    public String getTenantAdminDisplayName() {
        return tenantAdminDisplayName;
    }

    public void setTenantAdminDisplayName(String tenantAdminDisplayName) {
        this.tenantAdminDisplayName = tenantAdminDisplayName;
    }

    public String getTenantAdminPassword() {
        return tenantAdminPassword;
    }

    public void setTenantAdminPassword(String tenantAdminPassword) {
        this.tenantAdminPassword = tenantAdminPassword;
    }
}
