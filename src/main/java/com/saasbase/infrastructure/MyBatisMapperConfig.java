package com.saasbase.infrastructure;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
        "com.saasbase.auth.infrastructure.persistence",
        "com.saasbase.iam.infrastructure.persistence",
        "com.saasbase.audit.infrastructure.persistence",
        "com.saasbase.tenant.infrastructure.persistence",
        "com.saasbase.file.infrastructure.persistence"
})
public class MyBatisMapperConfig {
}
