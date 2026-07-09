# SaaS 核心后端基座实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 从空仓库构建第一阶段 SaaS 后端基座，使应用具备 Spring Boot 启动、数据库迁移、JWT 认证、租户隔离、API 分区、审计记录、OpenAPI、健康检查和 Docker Compose 起步部署能力。

**架构：** 使用单 Maven module 的模块化单体，源码按 COLA light 分层：`adapter -> application -> domain <- infrastructure`。生产数据库迁移由独立 Flyway migration 容器执行，应用生产账号不具备 DDL 权限，生产应用启动禁用自动迁移。

**技术栈：** Java 25 LTS、Alibaba Dragonwell 25 Standard Edition、Spring Boot 4.1.0、Spring Security、MyBatis-Plus、Flyway、MySQL 8.4 LTS、Redis/Tair、springdoc OpenAPI、Testcontainers、ArchUnit、Docker Compose。

---

## 范围说明

本计划覆盖“后端基座”而不是完整 SaaS 平台。第一轮实现以下可运行闭环：

- Spring Boot 项目脚手架和 COLA light 包结构。
- Flyway 初始化核心表、索引、内置权限点和默认系统配置。
- 生产迁移模型：独立 `migration` Compose profile，应用生产环境 `spring.flyway.enabled=false`。
- Spring Security + JWT access token + Redis refresh token。
- Redis 第一版从简：生产假设使用托管高可用 Redis/Tair；认证安全状态以 Redis 为准；Redis 不可用时认证链路 fail-closed。
- MyBatis-Plus 租户拦截器，普通租户 API 自动追加 `tenant_id`。
- API 分区：`/api/v1/auth/**`、`/api/v1/admin/**`、`/api/v1/platform/**`、`/api/v1/open/**`。
- 安全审计和管理操作审计最小写入链路。
- 本地文件存储最小网关，禁止业务代码直接使用用户传入路径。

完整 IAM 管理界面、复杂数据范围策略、OSS 适配器、消息通知、任务调度、支付订阅和开放平台不进入本计划。

## 文件结构

创建或修改的主要文件：

- `pom.xml`：Maven 项目、Spring Boot 4.1.0、Java 25、MyBatis-Plus、Flyway、Security、OpenAPI、Testcontainers、ArchUnit 依赖。
- `src/main/java/com/saasbase/SaaSBaseApplication.java`：应用入口。
- `src/main/java/com/saasbase/common/**`：通用错误码、统一响应、分页、trace、租户和用户上下文。
- `src/main/java/com/saasbase/auth/**`：认证 adapter/application/domain/infrastructure 分层代码。
- `src/main/java/com/saasbase/tenant/**`：租户实体、租户网关和平台租户 API。
- `src/main/java/com/saasbase/iam/**`：用户、部门、角色、权限最小域模型和权限校验入口。
- `src/main/java/com/saasbase/audit/**`：安全审计和管理操作审计写入网关。
- `src/main/java/com/saasbase/file/**`：`FileStorageGateway` 和本地适配器。
- `src/main/java/com/saasbase/system/**`：健康检查、系统配置查询。
- `src/main/resources/application.yml`：公共配置。
- `src/main/resources/application-local.yml`：本地开发配置，允许自动 Flyway。
- `src/main/resources/application-prod.yml`：生产配置，禁用应用启动 Flyway。
- `src/main/resources/db/migration/V1__init_core_schema.sql`：核心表、索引、内置权限点和默认配置。
- `src/test/resources/application-test.yml`：测试环境配置，使用 Testcontainers MySQL，避免测试误连本地或生产依赖。
- `docker-compose.local.yml`：本地 MySQL/Redis/应用依赖。
- `docker-compose.prod.yml`：生产应用和 migration profile，不包含自建 MySQL/Redis。
- `src/test/java/com/saasbase/**`：Domain、Application、Adapter、Infrastructure、Architecture 测试。

## 任务 1：创建 Maven 项目和 COLA light 骨架

**文件：**
- 创建：`pom.xml`
- 创建：`src/main/java/com/saasbase/SaaSBaseApplication.java`
- 创建：`src/test/java/com/saasbase/architecture/ColaArchitectureTest.java`

说明：不创建 `.gitkeep`。`.gitkeep` 常用于让 Git 跟踪空目录，但 Java package 会在后续任务创建真实类文件时自然出现，空目录占位没有实现价值。

- [ ] **步骤 1：编写失败的架构测试**

```java
package com.saasbase.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.saasbase", importOptions = ImportOption.DoNotIncludeTests.class)
class ColaArchitectureTest {

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_layers =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..adapter..",
                            "..application..",
                            "..infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule adapter_does_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..adapter..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=ColaArchitectureTest test`

预期：FAIL，报错包含 `The goal you specified requires a project to execute but there is no POM in this directory`。

中文理解：当前目录没有 `pom.xml`，Maven 不知道这是一个项目，所以无法执行测试目标。这里失败是预期红灯，下一步创建 `pom.xml` 后再转绿。

- [ ] **步骤 3：创建最小 Maven 和应用入口**

`pom.xml` 使用以下关键配置：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.saasbase</groupId>
    <artifactId>saasbase</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>SaaSBase</name>

    <properties>
        <java.version>25</java.version>
        <mybatis-plus.version>3.5.14</mybatis-plus.version>
        <springdoc.version>3.0.0</springdoc.version>
        <archunit.version>1.4.1</archunit.version>
        <testcontainers.version>1.21.3</testcontainers.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>jdbc</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`SaaSBaseApplication.java`：

```java
package com.saasbase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SaaSBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaaSBaseApplication.class, args);
    }
}
```

- [ ] **步骤 4：运行架构测试验证通过**

运行：`mvn -q -Dtest=ColaArchitectureTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add pom.xml src/main/java src/test/java
git commit -m "初始化后端项目骨架"
```

## 任务 2：实现通用响应、错误码和全局异常处理

**文件：**
- 创建：`src/main/java/com/saasbase/common/api/ApiResponse.java`
- 创建：`src/main/java/com/saasbase/common/api/PageResponse.java`
- 创建：`src/main/java/com/saasbase/common/error/ErrorCode.java`
- 创建：`src/main/java/com/saasbase/common/error/BizException.java`
- 创建：`src/main/java/com/saasbase/common/error/GlobalExceptionHandler.java`
- 创建：`src/test/java/com/saasbase/common/error/GlobalExceptionHandlerTest.java`

- [ ] **步骤 1：编写失败的异常映射测试**

```java
package com.saasbase.common.error;

import com.saasbase.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void maps_biz_exception_to_declared_http_status_and_error_code() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBizException(new BizException(ErrorCode.TENANT_NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("TENANT_NOT_FOUND");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=GlobalExceptionHandlerTest test`

预期：FAIL，报错包含 `cannot find symbol class GlobalExceptionHandler`。

- [ ] **步骤 3：实现通用 API 与错误类型**

`ErrorCode.java`：

```java
package com.saasbase.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "登录状态已失效"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "租户不存在"),
    IAM_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "权限不足"),
    IAM_DATA_SCOPE_DENIED(HttpStatus.FORBIDDEN, "数据范围不足"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "资源冲突"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务端错误");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
```

`ApiResponse.java`：

```java
package com.saasbase.common.api;

public record ApiResponse<T>(boolean success, String code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "OK", data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
```

`BizException.java`：

```java
package com.saasbase.common.error;

public class BizException extends RuntimeException {
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
```

`GlobalExceptionHandler.java`：

```java
package com.saasbase.common.error;

import com.saasbase.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.fail(errorCode.name(), errorCode.message()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("VALIDATION_FAILED", exception.getMessage()));
    }
}
```

`PageResponse.java`：

```java
package com.saasbase.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, long pageNo, long pageSize) {
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -q -Dtest=GlobalExceptionHandlerTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/common src/test/java/com/saasbase/common
git commit -m "添加通用响应和异常处理"
```

## 任务 3：实现 Flyway 核心表和生产迁移模型

**文件：**
- 创建：`src/main/resources/application.yml`
- 创建：`src/main/resources/application-local.yml`
- 创建：`src/main/resources/application-prod.yml`
- 创建：`src/main/resources/db/migration/V1__init_core_schema.sql`
- 创建：`src/test/resources/application-test.yml`
- 创建：`docker-compose.local.yml`
- 创建：`docker-compose.prod.yml`
- 创建：`src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java`

- [ ] **步骤 1：编写失败的迁移集成测试**

```java
package com.saasbase.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("saasbase")
            .withUsername("root")
            .withPassword("rootpass");

    @Test
    void migrates_core_schema() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var result = connection.getMetaData().getTables(null, null, "iam_user", null)) {
            assertThat(result.next()).isTrue();
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=FlywayMigrationTest test`

预期：FAIL，报错包含 `Location 'classpath:db/migration' not found` 或 `Table iam_user` 不存在。

- [ ] **步骤 3：新增配置和核心迁移脚本**

`application.yml`：

```yaml
spring:
  application:
    name: saasbase
  profiles:
    default: local
server:
  port: 8080
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

`application-local.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/saasbase?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: rootpass
  flyway:
    enabled: true
  data:
    redis:
      host: localhost
      port: 6379
```

`application-prod.yml`：

```yaml
spring:
  datasource:
    url: ${SAASBASE_DATASOURCE_URL}
    username: ${SAASBASE_DATASOURCE_USERNAME}
    password: ${SAASBASE_DATASOURCE_PASSWORD}
  flyway:
    enabled: false
  data:
    redis:
      host: ${SAASBASE_REDIS_HOST}
      port: ${SAASBASE_REDIS_PORT:6379}
      password: ${SAASBASE_REDIS_PASSWORD:}
```

`src/test/resources/application-test.yml`：

```yaml
spring:
  datasource:
    url: jdbc:tc:mysql:8.4:///saasbase
    username: root
    password: rootpass
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  flyway:
    enabled: true
  data:
    redis:
      host: localhost
      port: 6379
management:
  health:
    redis:
      enabled: false
```

说明：测试环境通过 Testcontainers JDBC 启动 MySQL 容器，验证 SQL 行为仍以 MySQL 为准；禁用 Redis health check，避免不涉及 Redis 的 Spring 上下文测试因为本机未启动 Redis 而失败。

`V1__init_core_schema.sql` 必须包含这些表和索引：

```sql
CREATE TABLE tenant (
    id BIGINT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_code)
);

CREATE TABLE iam_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_iam_user_tenant_username (tenant_id, username),
    KEY idx_iam_user_tenant_status (tenant_id, status)
);

CREATE TABLE iam_dept (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    dept_code VARCHAR(64) NOT NULL,
    dept_name VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_iam_dept_tenant_code (tenant_id, dept_code),
    KEY idx_iam_dept_tenant_parent (tenant_id, parent_id)
);

CREATE TABLE iam_role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_iam_role_tenant_code (tenant_id, role_code)
);

CREATE TABLE iam_permission (
    id BIGINT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_iam_permission_code (permission_code)
);

CREATE TABLE iam_user_role (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, user_id, role_id)
);

CREATE TABLE iam_role_permission (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, role_id, permission_id)
);

CREATE TABLE security_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NULL,
    user_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64) NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_security_audit_tenant_time (tenant_id, created_at),
    KEY idx_security_audit_user_time (user_id, created_at)
);

CREATE TABLE admin_operation_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NULL,
    user_id BIGINT NULL,
    operation_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_admin_audit_tenant_time (tenant_id, created_at),
    KEY idx_admin_audit_user_time (user_id, created_at)
);

CREATE TABLE system_config (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(1024) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_system_config_key (config_key)
);

CREATE TABLE file_object (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    storage_type VARCHAR(32) NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_file_object_tenant_key (tenant_id, object_key),
    KEY idx_file_object_tenant_time (tenant_id, created_at)
);
```

- [ ] **步骤 4：新增 Compose 迁移模型并验证**

`docker-compose.prod.yml` 必须有独立 migration profile：

```yaml
services:
  migration:
    image: eclipse-temurin:25-jre
    profiles: ["migration"]
    working_dir: /app
    volumes:
      - ./target/saasbase-0.1.0-SNAPSHOT.jar:/app/app.jar:ro
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SAASBASE_DATASOURCE_URL: ${SAASBASE_MIGRATION_DATASOURCE_URL}
      SAASBASE_DATASOURCE_USERNAME: ${SAASBASE_MIGRATION_DATASOURCE_USERNAME}
      SAASBASE_DATASOURCE_PASSWORD: ${SAASBASE_MIGRATION_DATASOURCE_PASSWORD}
    command: ["java", "-Dspring.flyway.enabled=true", "-Dspring.main.web-application-type=none", "-jar", "/app/app.jar"]

  app:
    image: saasbase:0.1.0
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SAASBASE_DATASOURCE_URL: ${SAASBASE_DATASOURCE_URL}
      SAASBASE_DATASOURCE_USERNAME: ${SAASBASE_DATASOURCE_USERNAME}
      SAASBASE_DATASOURCE_PASSWORD: ${SAASBASE_DATASOURCE_PASSWORD}
      SAASBASE_REDIS_HOST: ${SAASBASE_REDIS_HOST}
      SAASBASE_REDIS_PORT: ${SAASBASE_REDIS_PORT:-6379}
      SAASBASE_REDIS_PASSWORD: ${SAASBASE_REDIS_PASSWORD:-}
    ports:
      - "8080:8080"
```

运行：`mvn -q -Dtest=FlywayMigrationTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/resources src/test/resources docker-compose.local.yml docker-compose.prod.yml src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java
git commit -m "添加数据库迁移和生产迁移模型"
```

## 任务 4：实现 Spring Security 与 JWT 基础设施

**文件：**
- 创建：`src/main/java/com/saasbase/auth/domain/UserPrincipal.java`
- 创建：`src/main/java/com/saasbase/auth/domain/gateway/TokenGateway.java`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/security/JwtTokenGateway.java`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/security/SecurityConfig.java`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilter.java`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/security/SecurityErrorHandler.java`
- 创建：`src/test/java/com/saasbase/auth/infrastructure/security/JwtTokenGatewayTest.java`

- [ ] **步骤 1：编写失败的 JWT 测试**

```java
package com.saasbase.auth.infrastructure.security;

import com.saasbase.auth.domain.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenGatewayTest {

    @Test
    void signs_and_parses_access_token() {
        JwtTokenGateway gateway = new JwtTokenGateway("01234567890123456789012345678901", Duration.ofMinutes(15));
        UserPrincipal principal = new UserPrincipal(1001L, 2001L, "alice", Set.of("tenant:user:read"));

        String token = gateway.issueAccessToken(principal);
        UserPrincipal parsed = gateway.parseAccessToken(token);

        assertThat(parsed.userId()).isEqualTo(1001L);
        assertThat(parsed.tenantId()).isEqualTo(2001L);
        assertThat(parsed.permissions()).containsExactly("tenant:user:read");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=JwtTokenGatewayTest test`

预期：FAIL，报错包含 `cannot find symbol class JwtTokenGateway`。

- [ ] **步骤 3：实现 JWT 网关和 Spring Security 配置**

`UserPrincipal.java`：

```java
package com.saasbase.auth.domain;

import java.util.Set;

public record UserPrincipal(Long userId, Long tenantId, String username, Set<String> permissions) {
}
```

说明：`record` 是 Java 16+ 的数据类语法，适合只承载数据的不可变对象。上面的代码会自动生成构造器、`userId()`、`tenantId()`、`username()`、`permissions()` 访问方法，以及 `equals`、`hashCode` 和 `toString`。

`TokenGateway.java`：

```java
package com.saasbase.auth.domain.gateway;

import com.saasbase.auth.domain.UserPrincipal;

public interface TokenGateway {
    String issueAccessToken(UserPrincipal principal);

    UserPrincipal parseAccessToken(String token);
}
```

`SecurityConfig.java` 必须满足：

```java
package com.saasbase.auth.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            SecurityErrorHandler securityErrorHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/v1/auth/login", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/open/**").denyAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
```

`JwtTokenGateway` 可先使用 HMAC SHA-256 和 JDK 标准 crypto 实现，不引入授权服务器。实现要求：

```java
public String issueAccessToken(UserPrincipal principal)
public UserPrincipal parseAccessToken(String token)
```

payload 至少包含：

```json
{
  "sub": "1001",
  "tenant_id": 2001,
  "username": "alice",
  "permissions": ["tenant:user:read"],
  "exp": 900
}
```

- [ ] **步骤 4：运行 JWT 测试验证通过**

运行：`mvn -q -Dtest=JwtTokenGatewayTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/auth src/test/java/com/saasbase/auth
git commit -m "添加Spring Security和JWT基础设施"
```

## 任务 5：实现认证 Redis 状态和 fail-closed 行为

**文件：**
- 创建：`src/main/java/com/saasbase/auth/domain/gateway/RefreshTokenStore.java`
- 创建：`src/main/java/com/saasbase/auth/domain/gateway/TokenRevocationStore.java`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/redis/RedisRefreshTokenStore.java`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/redis/RedisTokenRevocationStore.java`
- 创建：`src/test/java/com/saasbase/auth/application/TokenRevocationPolicyTest.java`

- [ ] **步骤 1：编写失败的 fail-closed 测试**

```java
package com.saasbase.auth.application;

import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenRevocationPolicyTest {

    @Test
    void denies_request_when_revocation_store_is_unavailable() {
        TokenRevocationStore unavailableStore = tokenId -> {
            throw new IllegalStateException("redis unavailable");
        };
        TokenRevocationPolicy policy = new TokenRevocationPolicy(unavailableStore);

        assertThatThrownBy(() -> policy.ensureNotRevoked("token-1"))
                .isInstanceOf(TokenStateUnavailableException.class);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=TokenRevocationPolicyTest test`

预期：FAIL，报错包含 `cannot find symbol class TokenRevocationPolicy`。

- [ ] **步骤 3：实现 Redis 安全状态接口和 fail-closed 策略**

`TokenRevocationStore.java`：

```java
package com.saasbase.auth.domain.gateway;

public interface TokenRevocationStore {
    boolean isRevoked(String tokenId);
}
```

`TokenStateUnavailableException.java`：

```java
package com.saasbase.auth.application;

public class TokenStateUnavailableException extends RuntimeException {
    public TokenStateUnavailableException(Throwable cause) {
        super("token state unavailable", cause);
    }
}
```

`TokenRevocationPolicy.java`：

```java
package com.saasbase.auth.application;

import com.saasbase.auth.domain.gateway.TokenRevocationStore;

public class TokenRevocationPolicy {
    private final TokenRevocationStore tokenRevocationStore;

    public TokenRevocationPolicy(TokenRevocationStore tokenRevocationStore) {
        this.tokenRevocationStore = tokenRevocationStore;
    }

    public void ensureNotRevoked(String tokenId) {
        try {
            if (tokenRevocationStore.isRevoked(tokenId)) {
                throw new RevokedTokenException();
            }
        } catch (RevokedTokenException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TokenStateUnavailableException(exception);
        }
    }
}
```

`RevokedTokenException.java`：

```java
package com.saasbase.auth.application;

public class RevokedTokenException extends RuntimeException {
    public RevokedTokenException() {
        super("token revoked");
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -q -Dtest=TokenRevocationPolicyTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/auth src/test/java/com/saasbase/auth
git commit -m "添加Redis认证状态和失败拒绝策略"
```

## 任务 6：实现租户上下文和 MyBatis-Plus 租户拦截

**文件：**
- 创建：`src/main/java/com/saasbase/common/tenant/TenantContext.java`
- 创建：`src/main/java/com/saasbase/common/tenant/TenantContextHolder.java`
- 创建：`src/main/java/com/saasbase/tenant/infrastructure/mybatis/MyBatisPlusTenantConfig.java`
- 创建：`src/main/java/com/saasbase/tenant/infrastructure/mybatis/PlatformTenantBypass.java`
- 创建：`src/test/java/com/saasbase/tenant/infrastructure/mybatis/TenantLineInterceptorTest.java`

- [ ] **步骤 1：编写失败的租户 SQL 测试**

```java
package com.saasbase.tenant.infrastructure.mybatis;

import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantLineInterceptorTest {

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void supplies_current_tenant_id_to_mybatis_plus_handler() {
        TenantContextHolder.set(new TenantContext(2001L, 1001L, false));
        SaasTenantLineHandler handler = new SaasTenantLineHandler();

        assertThat(handler.getTenantId().toString()).isEqualTo("2001");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=TenantLineInterceptorTest test`

预期：FAIL，报错包含 `cannot find symbol class TenantContextHolder`。

- [ ] **步骤 3：实现租户上下文和拦截器配置**

`TenantContext.java`：

```java
package com.saasbase.common.tenant;

public record TenantContext(Long tenantId, Long userId, boolean platformRequest) {
}
```

`TenantContextHolder.java`：

```java
package com.saasbase.common.tenant;

public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    public static TenantContext require() {
        TenantContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("tenant context is required");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
```

`MyBatisPlusTenantConfig.java` 要配置：

```java
package com.saasbase.tenant.infrastructure.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusTenantConfig {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new SaasTenantLineHandler()));
        return interceptor;
    }
}
```

`SaasTenantLineHandler` 规则：

```java
package com.saasbase.tenant.infrastructure.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.saasbase.common.tenant.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Set;

public class SaasTenantLineHandler implements TenantLineHandler {
    private static final Set<String> IGNORE_TABLES = Set.of(
            "tenant",
            "iam_permission",
            "security_audit_log",
            "admin_operation_audit_log",
            "system_config");

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContextHolder.require().tenantId());
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -q -Dtest=TenantLineInterceptorTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/common/tenant src/main/java/com/saasbase/tenant src/test/java/com/saasbase/tenant
git commit -m "添加租户上下文和SQL租户拦截"
```

## 任务 7：实现 API 分区和 OpenAPI 分组

**文件：**
- 创建：`src/main/java/com/saasbase/auth/adapter/AuthController.java`
- 创建：`src/main/java/com/saasbase/tenant/adapter/AdminTenantController.java`
- 创建：`src/main/java/com/saasbase/tenant/adapter/PlatformTenantController.java`
- 创建：`src/main/java/com/saasbase/system/infrastructure/openapi/OpenApiConfig.java`
- 创建：`src/test/java/com/saasbase/api/ApiPathPartitionTest.java`

- [ ] **步骤 1：编写失败的路径分区测试**

```java
package com.saasbase.api;

import com.saasbase.SaaSBaseApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SaaSBaseApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiPathPartitionTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void open_api_reserved_path_is_not_implemented_in_phase_one() throws Exception {
        mockMvc.perform(get("/api/v1/open/ping"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=ApiPathPartitionTest test`

预期：FAIL，可能因为 Spring 上下文缺少配置或 `/api/v1/open/ping` 未被 Security deny。

- [ ] **步骤 3：实现控制器路径和 OpenAPI 配置**

控制器路径必须遵守：

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
}
```

```java
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class AdminTenantController {
}
```

```java
@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {
}
```

`OpenApiConfig.java`：

```java
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
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -q -Dtest=ApiPathPartitionTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/auth/adapter src/main/java/com/saasbase/tenant/adapter src/main/java/com/saasbase/system src/test/java/com/saasbase/api
git commit -m "划分API路径和OpenAPI分组"
```

## 任务 8：实现最小登录、刷新和退出用例

**文件：**
- 创建：`src/main/java/com/saasbase/auth/application/AuthApplicationService.java`
- 创建：`src/main/java/com/saasbase/auth/application/dto/LoginRequest.java`
- 创建：`src/main/java/com/saasbase/auth/application/dto/LoginResponse.java`
- 创建：`src/main/java/com/saasbase/auth/domain/gateway/UserCredentialGateway.java`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapper.java`
- 修改：`src/main/java/com/saasbase/auth/adapter/AuthController.java`
- 创建：`src/test/java/com/saasbase/auth/application/AuthApplicationServiceTest.java`

- [ ] **步骤 1：编写失败的登录用例测试**

```java
package com.saasbase.auth.application;

import com.saasbase.auth.application.dto.LoginRequest;
import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApplicationServiceTest {

    @Test
    void login_returns_access_and_refresh_token_when_password_matches() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        UserCredentialGateway userGateway = (tenantCode, username) -> Optional.of(
                new UserCredential(1001L, 2001L, "alice", encoder.encode("pass123"), Set.of("tenant:user:read")));
        TokenGateway tokenGateway = new TokenGateway() {
            @Override
            public String issueAccessToken(UserPrincipal principal) {
                return "access-token";
            }

            @Override
            public UserPrincipal parseAccessToken(String token) {
                return new UserPrincipal(1001L, 2001L, "alice", Set.of("tenant:user:read"));
            }
        };
        AuthApplicationService service = new AuthApplicationService(userGateway, tokenGateway, encoder);

        var response = service.login(new LoginRequest("tenant-a", "alice", "pass123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=AuthApplicationServiceTest test`

预期：FAIL，报错包含 `cannot find symbol class AuthApplicationService`。

- [ ] **步骤 3：实现登录应用服务和 DTO**

`LoginRequest.java`：

```java
package com.saasbase.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String tenantCode,
        @NotBlank String username,
        @NotBlank String password) {
}
```

`LoginResponse.java`：

```java
package com.saasbase.auth.application.dto;

public record LoginResponse(String tokenType, String accessToken, String refreshToken, long expiresInSeconds) {
}
```

`AuthApplicationService.java`：

```java
package com.saasbase.auth.application;

import com.saasbase.auth.application.dto.LoginRequest;
import com.saasbase.auth.application.dto.LoginResponse;
import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthApplicationService {
    private final UserCredentialGateway userCredentialGateway;
    private final TokenGateway tokenGateway;
    private final PasswordEncoder passwordEncoder;

    public AuthApplicationService(
            UserCredentialGateway userCredentialGateway,
            TokenGateway tokenGateway,
            PasswordEncoder passwordEncoder) {
        this.userCredentialGateway = userCredentialGateway;
        this.tokenGateway = tokenGateway;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        var credential = userCredentialGateway.findByTenantCodeAndUsername(request.tenantCode(), request.username())
                .orElseThrow(() -> new BizException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), credential.passwordHash())) {
            throw new BizException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        String accessToken = tokenGateway.issueAccessToken(new UserPrincipal(
                credential.userId(),
                credential.tenantId(),
                credential.username(),
                credential.permissions()));
        return new LoginResponse("Bearer", accessToken, UUID.randomUUID().toString(), 900);
    }
}
```

- [ ] **步骤 4：运行登录测试验证通过**

运行：`mvn -q -Dtest=AuthApplicationServiceTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/auth src/test/java/com/saasbase/auth
git commit -m "实现最小认证用例"
```

## 任务 9：实现审计写入网关

**文件：**
- 创建：`src/main/java/com/saasbase/audit/domain/SecurityAuditEvent.java`
- 创建：`src/main/java/com/saasbase/audit/domain/AdminOperationAuditEvent.java`
- 创建：`src/main/java/com/saasbase/audit/domain/gateway/AuditGateway.java`
- 创建：`src/main/java/com/saasbase/audit/infrastructure/persistence/AuditMapper.java`
- 创建：`src/test/java/com/saasbase/audit/domain/SecurityAuditEventTest.java`

- [ ] **步骤 1：编写失败的安全审计事件测试**

```java
package com.saasbase.audit.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditEventTest {

    @Test
    void creates_login_failure_event() {
        SecurityAuditEvent event = SecurityAuditEvent.loginFailure(2001L, "alice", "127.0.0.1");

        assertThat(event.tenantId()).isEqualTo(2001L);
        assertThat(event.eventType()).isEqualTo("LOGIN_FAILURE");
        assertThat(event.result()).isEqualTo("FAILURE");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=SecurityAuditEventTest test`

预期：FAIL，报错包含 `cannot find symbol class SecurityAuditEvent`。

- [ ] **步骤 3：实现审计领域对象和网关接口**

`SecurityAuditEvent.java`：

```java
package com.saasbase.audit.domain;

import java.time.Instant;

public record SecurityAuditEvent(
        Long tenantId,
        Long userId,
        String username,
        String eventType,
        String result,
        String clientIp,
        Instant createdAt) {

    public static SecurityAuditEvent loginFailure(Long tenantId, String username, String clientIp) {
        return new SecurityAuditEvent(tenantId, null, username, "LOGIN_FAILURE", "FAILURE", clientIp, Instant.now());
    }

    public static SecurityAuditEvent loginSuccess(Long tenantId, Long userId, String username, String clientIp) {
        return new SecurityAuditEvent(tenantId, userId, username, "LOGIN", "SUCCESS", clientIp, Instant.now());
    }
}
```

`AuditGateway.java`：

```java
package com.saasbase.audit.domain.gateway;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.SecurityAuditEvent;

public interface AuditGateway {
    void appendSecurityAudit(SecurityAuditEvent event);

    void appendAdminOperationAudit(AdminOperationAuditEvent event);
}
```

- [ ] **步骤 4：运行审计测试验证通过**

运行：`mvn -q -Dtest=SecurityAuditEventTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/audit src/test/java/com/saasbase/audit
git commit -m "添加审计事件和写入网关"
```

## 任务 10：实现最小文件存储网关

**文件：**
- 创建：`src/main/java/com/saasbase/file/domain/FileObject.java`
- 创建：`src/main/java/com/saasbase/file/domain/gateway/FileStorageGateway.java`
- 创建：`src/main/java/com/saasbase/file/infrastructure/storage/LocalFileStorageGateway.java`
- 创建：`src/main/java/com/saasbase/file/infrastructure/storage/FileStorageProperties.java`
- 创建：`src/test/java/com/saasbase/file/infrastructure/storage/LocalFileStorageGatewayTest.java`

- [ ] **步骤 1：编写失败的 object key 测试**

```java
package com.saasbase.file.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageGatewayTest {

    @TempDir
    Path tempDir;

    @Test
    void stores_file_with_server_generated_object_key() throws Exception {
        LocalFileStorageGateway gateway = new LocalFileStorageGateway(new FileStorageProperties(tempDir));

        var stored = gateway.store(2001L, "report.txt", "text/plain", new ByteArrayInputStream("hello".getBytes()));

        assertThat(stored.objectKey()).isNotEqualTo("report.txt");
        assertThat(stored.objectKey()).doesNotContain("..");
        assertThat(tempDir.resolve(stored.objectKey())).exists();
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -q -Dtest=LocalFileStorageGatewayTest test`

预期：FAIL，报错包含 `cannot find symbol class LocalFileStorageGateway`。

- [ ] **步骤 3：实现最小本地文件网关**

`FileStorageGateway.java`：

```java
package com.saasbase.file.domain.gateway;

import com.saasbase.file.domain.FileObject;

import java.io.InputStream;

public interface FileStorageGateway {
    FileObject store(Long tenantId, String filename, String contentType, InputStream inputStream);

    InputStream load(Long tenantId, String objectKey);
}
```

`FileObject.java`：

```java
package com.saasbase.file.domain;

public record FileObject(Long tenantId, String storageType, String objectKey, String filename, String contentType, long size) {
}
```

`LocalFileStorageGateway` 要求：

```java
String objectKey = tenantId + "/" + UUID.randomUUID();
Path target = rootPath.resolve(objectKey).normalize();
if (!target.startsWith(rootPath)) {
    throw new IllegalArgumentException("invalid object key");
}
```

不要使用用户传入的 `filename` 拼接存储路径。

- [ ] **步骤 4：运行文件网关测试验证通过**

运行：`mvn -q -Dtest=LocalFileStorageGatewayTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/file src/test/java/com/saasbase/file
git commit -m "添加本地文件存储网关"
```

## 任务 11：完成启动、健康检查和整体验证

**文件：**
- 修改：`src/main/resources/application.yml`
- 检查：`src/test/resources/application-test.yml`
- 创建：`src/test/java/com/saasbase/SaaSBaseApplicationSmokeTest.java`
- 创建：`README.md`

- [ ] **步骤 1：编写失败的启动冒烟测试**

```java
package com.saasbase;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SaaSBaseApplicationSmokeTest {

    @Test
    void context_loads() {
    }
}
```

说明：`context_loads()` 方法体为空是 Spring Boot 冒烟测试的常见写法。真正的断言发生在测试方法执行前：`@SpringBootTest` 会启动 Spring 应用上下文；如果配置、Bean 注入、数据库迁移或安全配置有错误，测试会在进入空方法前失败。能执行完空方法就表示上下文加载成功。

- [ ] **步骤 2：运行测试验证失败或暴露缺失配置**

运行：`mvn -q -Dtest=SaaSBaseApplicationSmokeTest test`

预期：如果缺少 `application-test.yml` 或未启用 `test` profile，则 FAIL，错误包含缺失连接配置或误连本地依赖。

- [ ] **步骤 3：补齐测试配置和 README**

如果任务 3 尚未创建 `src/test/resources/application-test.yml`，按以下内容创建；如果已经存在，确认内容一致：

```yaml
spring:
  datasource:
    url: jdbc:tc:mysql:8.4:///saasbase
    username: root
    password: rootpass
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  flyway:
    enabled: true
  data:
    redis:
      host: localhost
      port: 6379
management:
  health:
    redis:
      enabled: false
```

`README.md` 至少包含：

````markdown
# SaaSBase

## 本地启动

```bash
docker compose -f docker-compose.local.yml up -d
mvn spring-boot:run
```

## 生产迁移

生产应用账号不具备 DDL 权限。发布时先运行独立迁移步骤：

```bash
docker compose -f docker-compose.prod.yml --profile migration run --rm migration
docker compose -f docker-compose.prod.yml up -d app
```

生产环境应用启动时 `spring.flyway.enabled=false`。
````

- [ ] **步骤 4：运行完整验证**

运行：

```bash
mvn test
mvn -q -DskipTests package
```

预期：全部 PASS，`target/saasbase-0.1.0-SNAPSHOT.jar` 生成。

- [ ] **步骤 5：Commit**

```bash
git add README.md src/main/resources src/test/resources/application-test.yml src/test/java/com/saasbase/SaaSBaseApplicationSmokeTest.java
git commit -m "补齐启动验证和使用说明"
```

## 自检清单

- [ ] 规格覆盖度：计划覆盖项目脚手架、COLA light、Flyway、生产迁移模型、Spring Security、JWT、Redis fail-closed、MyBatis 租户拦截、API 分区、审计、文件网关、OpenAPI、健康检查和 Docker Compose。
- [ ] 范围控制：计划没有实现支付、订阅、开放平台、OSS、消息通知、任务调度、行为分析、Kubernetes、OAuth2/OIDC 和 ABAC policy engine。
- [ ] 占位符扫描：计划中没有使用禁用占位词。
- [ ] 类型一致性：`UserPrincipal`、`TokenGateway`、`TenantContextHolder`、`AuditGateway`、`FileStorageGateway` 在首次任务定义后，后续引用名称保持一致。
- [ ] 测试路径：每个任务都有对应的精确测试命令和预期结果。

## 执行方式

计划已完成并保存到 `docs/superpowers/plans/2026-07-10-saas-core-backend-foundation.md`。两种执行方式：

1. **子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代。
2. **内联执行** - 在当前会话中使用 `executing-plans` 执行任务，批量执行并设有检查点。
