package com.saasbase.system.infrastructure.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI saasBaseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SaaSBase API v1")
                .version("v1"));
    }

    @Bean
    GroupedOpenApi authApi() {
        return GroupedOpenApi.builder().group("auth").pathsToMatch("/api/v1/auth/**").build();
    }

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("admin").pathsToMatch("/api/v1/admin/**").build();
    }

    @Bean
    GroupedOpenApi platformApi() {
        return GroupedOpenApi.builder().group("platform").pathsToMatch("/api/v1/platform/**").build();
    }
}
