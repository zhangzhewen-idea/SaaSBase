# MyBatis 持久化层统一改造实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将认证凭证查询和审计写入从 `JdbcTemplate` 迁移到 MyBatis/MyBatis-Plus，保持领域接口和 HTTP 行为不变。

**架构：** 使用 MyBatis Mapper 接口承载数据库操作，使用 XML 管理多表关联、权限聚合和审计写入；使用基础设施适配器将 MyBatis 结果转换为领域对象。登录查询通过 `@InterceptorIgnore(tenantLine = "1")` 绕过自动租户注入，但在 SQL 中显式建立租户边界。

**技术栈：** Java 25、Spring Boot 4.1、MyBatis-Plus 3.5.14、MySQL 8.4、Flyway、JUnit 5、Testcontainers。

---

## 文件清单

**创建：**

- `src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialRecord.java`：承载登录查询的数据库投影。
- `src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialPersistenceAdapter.java`：实现 `UserCredentialGateway` 并转换领域对象。
- `src/main/java/com/saasbase/audit/infrastructure/persistence/SecurityAuditMapper.java`：安全审计写入接口。
- `src/main/java/com/saasbase/audit/infrastructure/persistence/AdminOperationAuditMapper.java`：管理操作审计写入接口。
- `src/main/java/com/saasbase/audit/infrastructure/persistence/AuditPersistenceAdapter.java`：实现 `AuditGateway` 并生成审计 ID。
- `src/main/resources/mapper/auth/UserCredentialMapper.xml`：登录查询 SQL 和结果映射。
- `src/main/resources/mapper/audit/SecurityAuditMapper.xml`：安全审计 INSERT SQL。
- `src/main/resources/mapper/audit/AdminOperationAuditMapper.xml`：管理操作审计 INSERT SQL。
- `src/test/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapperIntegrationTest.java`：认证查询集成测试。
- `src/test/java/com/saasbase/audit/infrastructure/persistence/AuditPersistenceAdapterIntegrationTest.java`：审计写入集成测试。

**修改：**

- `src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapper.java`：将当前 `JdbcTemplate` 实现替换为 MyBatis Mapper 接口。

**删除：**

- `src/main/java/com/saasbase/audit/infrastructure/persistence/AuditMapper.java`：由 `AuditPersistenceAdapter` 和两个 MyBatis Mapper 替代。

不修改领域网关、应用服务、HTTP Controller、Flyway 迁移和数据库结构。

### 任务 1：先建立集成测试夹具和失败测试

**文件：**

- 创建：`src/test/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapperIntegrationTest.java`
- 创建：`src/test/java/com/saasbase/audit/infrastructure/persistence/AuditPersistenceAdapterIntegrationTest.java`

- [ ] **步骤 1：创建 MySQL Testcontainers 测试基类配置**

测试类使用 `@Testcontainers` 和 `@Container MySQLContainer<>("mysql:8.4")`，通过 `@DynamicPropertySource` 设置：

```java
@DynamicPropertySource
static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.flyway.enabled", () -> true);
}
```

测试使用 `@SpringBootTest` 注入真正的 MyBatis Mapper 和领域网关；测试数据通过测试专用 `JdbcTemplate` 插入，生产代码不得重新依赖 `JdbcTemplate`。

- [ ] **步骤 2：编写用户凭证查询失败测试**

在 `UserCredentialMapperIntegrationTest` 中插入一个 `ACTIVE` 租户、一个 `ACTIVE` 用户、一个已逻辑删除用户和一个 `DISABLED` 用户，先调用尚不存在的 `UserCredentialGateway` MyBatis 实现：

```java
assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "alice")).isPresent();
assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "disabled")).isEmpty();
assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "deleted")).isEmpty();
assertThat(gateway.findByTenantCodeAndUsername("missing", "alice")).isEmpty();
```

同时插入两个租户下相同用户名，确认按 `tenantCode` 查询不会返回另一个租户的凭证。

- [ ] **步骤 3：编写审计写入失败测试**

在 `AuditPersistenceAdapterIntegrationTest` 中构造 `SecurityAuditEvent` 和 `AdminOperationAuditEvent`，调用尚不存在的 `AuditGateway` 实现，并查询数据库确认事件字段。测试应先因实现类不存在而失败。

- [ ] **步骤 4：运行新增测试确认失败**

运行：

```bash
mvn -q -Dtest=UserCredentialMapperIntegrationTest,AuditPersistenceAdapterIntegrationTest test
```

预期：FAIL，原因是 MyBatis Mapper 和持久化适配器尚未实现。

- [ ] **步骤 5：Commit**

```bash
git add src/test/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapperIntegrationTest.java \
        src/test/java/com/saasbase/audit/infrastructure/persistence/AuditPersistenceAdapterIntegrationTest.java
git commit -m "补充 MyBatis 持久化集成测试"
```

### 任务 2：迁移用户凭证查询

**文件：**

- 创建：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialRecord.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapper.java`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialPersistenceAdapter.java`
- 创建：`src/main/resources/mapper/auth/UserCredentialMapper.xml`

- [ ] **步骤 1：定义查询投影**

`UserCredentialRecord` 使用不可变 `record`：

```java
public record UserCredentialRecord(
        Long userId,
        Long tenantId,
        String username,
        String passwordHash,
        String permissions) {
}
```

`permissions` 保留 SQL 聚合后的逗号分隔字符串，领域适配器负责转换为 `Set<String>`。

- [ ] **步骤 2：定义 MyBatis Mapper 接口**

```java
@Mapper
public interface UserCredentialMapper {
    @InterceptorIgnore(tenantLine = "1")
    Optional<UserCredentialRecord> findByTenantCodeAndUsername(
            @Param("tenantCode") String tenantCode,
            @Param("username") String username);
}
```

登录请求尚未建立 `TenantContext`，必须绕过租户插件的自动注入；SQL 自身必须显式连接 `tenant` 和 `iam_user` 并按 `tenant_code` 限定租户。

- [ ] **步骤 3：编写 XML 查询和结果映射**

XML 的 `namespace` 必须是 `com.saasbase.auth.infrastructure.persistence.UserCredentialMapper`，查询保持现有字段和权限聚合语义，并增加用户状态校验：

```sql
SELECT u.id AS user_id,
       u.tenant_id,
       u.username,
       u.password_hash,
       COALESCE(GROUP_CONCAT(p.permission_code ORDER BY p.permission_code SEPARATOR ','), '') AS permissions
  FROM tenant t
  JOIN iam_user u ON u.tenant_id = t.id
                 AND u.deleted = 0
                 AND u.status = 'ACTIVE'
 LEFT JOIN iam_user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
 LEFT JOIN iam_role_permission rp ON rp.tenant_id = ur.tenant_id AND rp.role_id = ur.role_id
 LEFT JOIN iam_permission p ON p.id = rp.permission_id
 WHERE t.tenant_code = #{tenantCode}
   AND t.status = 'ACTIVE'
   AND u.username = #{username}
 GROUP BY u.id, u.tenant_id, u.username, u.password_hash
```

使用显式 `<resultMap>` 将 `user_id`、`tenant_id`、`password_hash` 和 `permissions` 映射到 `UserCredentialRecord`。

- [ ] **步骤 4：实现领域网关适配器**

`UserCredentialPersistenceAdapter` 注入 MyBatis `UserCredentialMapper`，保持网关返回值：

```java
@Repository
public class UserCredentialPersistenceAdapter implements UserCredentialGateway {
    @Override
    public Optional<UserCredential> findByTenantCodeAndUsername(String tenantCode, String username) {
        return mapper.findByTenantCodeAndUsername(tenantCode, username)
                .map(this::toDomain);
    }
}
```

`toDomain` 将空权限字符串转换成 `Set.of()`，非空字符串按逗号拆分；密码哈希原样传递，不输出或记录明文密码。

- [ ] **步骤 5：运行用户查询测试确认通过**

运行：

```bash
mvn -q -Dtest=UserCredentialMapperIntegrationTest test
```

预期：所有用户状态、逻辑删除、租户边界和权限聚合断言 PASS。

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/com/saasbase/auth/infrastructure/persistence \
        src/main/resources/mapper/auth/UserCredentialMapper.xml
git commit -m "迁移用户凭证查询到 MyBatis"
```

### 任务 3：迁移安全审计和管理操作审计

**文件：**

- 创建：`src/main/java/com/saasbase/audit/infrastructure/persistence/SecurityAuditMapper.java`
- 创建：`src/main/java/com/saasbase/audit/infrastructure/persistence/AdminOperationAuditMapper.java`
- 创建：`src/main/java/com/saasbase/audit/infrastructure/persistence/AuditPersistenceAdapter.java`
- 创建：`src/main/resources/mapper/audit/SecurityAuditMapper.xml`
- 创建：`src/main/resources/mapper/audit/AdminOperationAuditMapper.xml`
- 删除：`src/main/java/com/saasbase/audit/infrastructure/persistence/AuditMapper.java`

- [ ] **步骤 1：定义两个 MyBatis 写入接口**

接口分别暴露一个写入方法，并标注 `@Mapper`：

```java
@Mapper
public interface SecurityAuditMapper {
    int insert(SecurityAuditEvent event, long id);
}

@Mapper
public interface AdminOperationAuditMapper {
    int insert(AdminOperationAuditEvent event, long id);
}
```

如果 MyBatis 多参数绑定无法直接使用事件属性，使用 `@Param("event")` 和 `@Param("id")`，XML 统一通过 `#{event.tenantId}`、`#{id}` 绑定。

- [ ] **步骤 2：编写两个 XML INSERT**

`SecurityAuditMapper.xml` 写入 `security_audit_log` 的 `id、tenant_id、user_id、username、event_type、result、client_ip、created_at`；`AdminOperationAuditMapper.xml` 写入 `admin_operation_audit_log` 的现有八个字段。

两个 XML 的 `namespace` 必须分别对应两个 Mapper 全限定类名。审计表已经位于 `SaasTenantLineHandler.IGNORE_TABLES`，不需要依赖当前租户上下文才能写入失败登录审计。

- [ ] **步骤 3：实现审计领域网关适配器**

`AuditPersistenceAdapter` 实现 `AuditGateway`，注入两个 MyBatis Mapper，保留当前 ID 生成规则：

```java
private long id() {
    return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
}
```

适配器只负责生成 ID 和转发事件，不新增异步、重试或独立事务。

- [ ] **步骤 4：运行审计集成测试确认通过**

运行：

```bash
mvn -q -Dtest=AuditPersistenceAdapterIntegrationTest test
```

预期：两类审计记录均能写入，字段值与领域事件一致。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/saasbase/audit/infrastructure/persistence \
        src/main/resources/mapper/audit
git commit -m "迁移审计写入到 MyBatis"
```

### 任务 4：清理 JdbcTemplate 依赖并验证租户行为

**文件：**

- 修改：`src/test/java/com/saasbase/tenant/infrastructure/mybatis/TenantLineInterceptorTest.java`
- 修改：`src/test/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapperIntegrationTest.java`

- [ ] **步骤 1：确认生产代码不再使用 JdbcTemplate**

运行：

```bash
rg -n "JdbcTemplate|jdbcTemplate" src/main/java
```

预期：无输出。

- [ ] **步骤 2：补充登录查询的显式租户边界断言**

在集成测试中使用两个租户的相同用户名和不同密码哈希，分别按两个 `tenantCode` 查询，断言返回的 `tenantId` 和密码哈希分别对应目标租户。

- [ ] **步骤 3：确认普通租户和平台请求规则不被改动**

运行现有租户拦截器测试：

```bash
mvn -q -Dtest=TenantLineInterceptorTest test
```

预期：现有租户上下文、平台绕过和无上下文失败行为保持通过。

- [ ] **步骤 4：Commit**

```bash
git add src/test/java/com/saasbase/tenant/infrastructure/mybatis/TenantLineInterceptorTest.java \
        src/test/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapperIntegrationTest.java
git commit -m "校验 MyBatis 租户隔离行为"
```

### 任务 5：完成全量验证和交付检查

**文件：**

- 无新增业务文件；检查任务 2-4 的全部变更。

- [ ] **步骤 1：运行认证和审计相关测试**

```bash
mvn -q -Dtest=AuthApplicationServiceTest,AuthControllerTest,SecurityAuditEventTest,UserCredentialMapperIntegrationTest,AuditPersistenceAdapterIntegrationTest test
```

预期：测试进程退出码为 0。

- [ ] **步骤 2：运行完整测试套件**

```bash
mvn -q test
```

预期：所有测试通过，Flyway 能在 MySQL 8.4 中执行 `V1` 和 `V2`，没有 Mapper 初始化错误。

- [ ] **步骤 3：检查变更范围和格式**

```bash
git diff --check
rg -n "JdbcTemplate|jdbcTemplate" src/main/java
git status --short
```

预期：无空白错误、生产代码无 `JdbcTemplate` 引用，工作区只包含本次 MyBatis 改造相关文件及原有用户变更。

- [ ] **步骤 4：Commit**

```bash
git add src/main/java src/main/resources/mapper src/test/java
git commit -m "完成 MyBatis 持久化层统一改造"
```
