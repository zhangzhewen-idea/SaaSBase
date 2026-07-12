package com.saasbase.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserCredentialMapper {

    @InterceptorIgnore(tenantLine = "1")
    Optional<UserCredentialRecord> findByTenantCodeAndUsername(
            @Param("tenantCode") String tenantCode,
            @Param("username") String username);
}
