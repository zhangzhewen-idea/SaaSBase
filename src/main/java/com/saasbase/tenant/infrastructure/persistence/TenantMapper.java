package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.tenant.domain.TenantStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TenantMapper {
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TenantRecord record);

    boolean existsByCode(@Param("tenantCode") String tenantCode);

    Optional<TenantRecord> findById(@Param("id") long id);

    long count(@Param("tenantCode") String tenantCode,
               @Param("tenantNamePattern") String tenantNamePattern,
               @Param("status") TenantStatus status);

    List<TenantRecord> page(@Param("tenantCode") String tenantCode,
                            @Param("tenantNamePattern") String tenantNamePattern,
                            @Param("status") TenantStatus status,
                            @Param("offset") long offset,
                            @Param("pageSize") long pageSize);

    int update(@Param("record") TenantRecord record, @Param("operatorId") Long operatorId);
}
