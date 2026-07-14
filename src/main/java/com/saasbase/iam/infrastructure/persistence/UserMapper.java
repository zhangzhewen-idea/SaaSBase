package com.saasbase.iam.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.UserPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mapper
public interface UserMapper {
    @InterceptorIgnore(tenantLine = "1")
    boolean existsByUsername(@Param("tenantId") long tenantId, @Param("username") String username);

    @InterceptorIgnore(tenantLine = "1")
    Optional<UserRecord> findById(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @InterceptorIgnore(tenantLine = "1")
    List<UserRecord> listPage(@Param("tenantId") long tenantId, @Param("excludeUserId") long excludeUserId, @Param("query") UserPageQuery query);

    long countPage(@Param("tenantId") long tenantId, @Param("excludeUserId") long excludeUserId, @Param("query") UserPageQuery query);

    @InterceptorIgnore(tenantLine = "1")
    int insert(@Param("user") IamUser user);

    @InterceptorIgnore(tenantLine = "1")
    int update(@Param("user") IamUser user);

    @InterceptorIgnore(tenantLine = "1")
    Optional<Long> findActiveDepartmentId(@Param("tenantId") long tenantId, @Param("departmentId") long departmentId);

    @InterceptorIgnore(tenantLine = "1")
    Optional<Long> findActiveRoleId(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @InterceptorIgnore(tenantLine = "1")
    void lockTenantAdminRole(@Param("tenantId") long tenantId);

    @InterceptorIgnore(tenantLine = "1")
    long countActiveAdministratorsExcludingUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @InterceptorIgnore(tenantLine = "1")
    void deleteRoles(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @InterceptorIgnore(tenantLine = "1")
    void insertRoles(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("roleIds") Set<Long> roleIds);

    @InterceptorIgnore(tenantLine = "1")
    Set<Long> findRoleIds(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
