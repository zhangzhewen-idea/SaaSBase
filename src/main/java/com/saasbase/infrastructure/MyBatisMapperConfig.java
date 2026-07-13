package com.saasbase.infrastructure;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.saasbase")
public class MyBatisMapperConfig {
}
