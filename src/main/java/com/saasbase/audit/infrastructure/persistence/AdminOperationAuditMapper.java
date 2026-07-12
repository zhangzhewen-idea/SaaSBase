package com.saasbase.audit.infrastructure.persistence;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminOperationAuditMapper {

    void insert(@Param("id") long id, @Param("event") AdminOperationAuditEvent event);
}
