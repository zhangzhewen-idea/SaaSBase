package com.saasbase.tenant.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "1")
interface TenantAdminMapper {
    int insertUser(TenantAdminRecord record);
    int insertRole(TenantAdminRecord record);
    int insertUserRole(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("roleId") long roleId);
    List<Long> findTenantPermissionIds();
    int insertRolePermissions(@Param("tenantId") long tenantId, @Param("roleId") long roleId, @Param("permissionIds") List<Long> permissionIds);
    boolean hasPermissionCode(@Param("code") String code);
}
