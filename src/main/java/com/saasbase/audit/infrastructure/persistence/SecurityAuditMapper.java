package com.saasbase.audit.infrastructure.persistence;

import com.saasbase.audit.domain.SecurityAuditEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SecurityAuditMapper {

    void insert(@Param("id") long id, @Param("event") SecurityAuditEvent event);
}
