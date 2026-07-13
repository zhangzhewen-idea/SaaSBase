package com.saasbase.iam.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.saasbase.iam.domain.Role;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RoleMapper {
    @InterceptorIgnore(tenantLine = "1")
    Optional<RoleRecord> findById(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @InterceptorIgnore(tenantLine = "1")
    boolean existsByCode(@Param("tenantId") long tenantId, @Param("roleCode") String roleCode);

    @InterceptorIgnore(tenantLine = "1")
    List<RoleRecord> page(@Param("tenantId") long tenantId,
                          @Param("keyword") String keyword,
                          @Param("status") RoleStatus status,
                          @Param("type") RoleType type,
                          @Param("offset") long offset,
                          @Param("pageSize") long pageSize);

    @InterceptorIgnore(tenantLine = "1")
    long countPage(@Param("tenantId") long tenantId,
                   @Param("keyword") String keyword,
                   @Param("status") RoleStatus status,
                   @Param("type") RoleType type);

    @InterceptorIgnore(tenantLine = "1")
    int insert(RoleRecord role);

    @InterceptorIgnore(tenantLine = "1")
    int update(RoleRecord role, @Param("expectedVersion") long expectedVersion);

    @InterceptorIgnore(tenantLine = "1")
    int softDelete(@Param("tenantId") long tenantId,
                   @Param("roleId") long roleId,
                   @Param("operatorId") long operatorId);
}
