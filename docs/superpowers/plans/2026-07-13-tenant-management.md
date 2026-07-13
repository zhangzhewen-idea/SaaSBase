# 租户管理闭环实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现平台租户管理、租户资料查询、首个管理员原子初始化，以及基于 `sessionVersion` 的租户停用即时失效能力。

**架构：** 租户模块采用 `adapter → application → domain ← infrastructure` 分层，应用层通过 `TenantGateway`、`TenantAdminInitializer`、`TenantAuthStateGateway` 和 `AuditGateway` 编排事务。MySQL 保存租户状态与会话版本权威值，Redis 缓存 5 秒，JWT 和 Refresh Token 保存版本快照。

**技术栈：** Java 25、Spring Boot 4.1、Spring Security、MyBatis-Plus、MyBatis XML、MySQL 8.4、Redis、Flyway、JUnit 5、Mockito、MockMvc、Testcontainers、ArchUnit。

---

## 文件结构

### 新建文件

- `src/main/java/com/saasbase/tenant/domain/Tenant.java`：租户状态、会话版本和状态转换规则。
- `src/main/java/com/saasbase/tenant/domain/TenantStatus.java`：`ACTIVE`、`DISABLED` 枚举。
- `src/main/java/com/saasbase/tenant/domain/TenantAuthState.java`：认证状态值对象。
- `src/main/java/com/saasbase/tenant/domain/gateway/TenantGateway.java`：租户持久化接口。
- `src/main/java/com/saasbase/tenant/domain/gateway/TenantAdminInitializer.java`：首个管理员和角色初始化接口。
- `src/main/java/com/saasbase/tenant/domain/gateway/TenantAuthStateGateway.java`：租户认证状态读取与缓存接口。
- `src/main/java/com/saasbase/tenant/application/TenantApplicationService.java`：租户用例与事务编排。
- `src/main/java/com/saasbase/tenant/application/dto/*.java`：创建、编辑、查询和响应 DTO。
- `src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantMapper.java`：租户 MyBatis Mapper。
- `src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantPersistenceAdapter.java`：`TenantGateway` 实现。
- `src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantAdminPersistenceAdapter.java`：IAM 最小初始化实现。
- `src/main/java/com/saasbase/tenant/infrastructure/redis/RedisTenantAuthStateGateway.java`：5 秒认证状态缓存。
- `src/main/java/com/saasbase/tenant/adapter/AdminTenantProfileController.java`：当前租户资料接口。
- `src/main/resources/mapper/tenant/TenantMapper.xml`：跨租户分页、详情和乐观锁 SQL。
- 对应领域、应用、Adapter、基础设施测试文件。

### 修改文件

- `src/main/resources/db/migration/V3__tenant_management.sql`：增加 `session_version` 并插入内置权限模板。
- `src/main/java/com/saasbase/auth/domain/UserPrincipal.java`：增加 `sessionVersion`。
- `src/main/java/com/saasbase/auth/application/AuthApplicationService.java`：登录和刷新携带、校验租户会话版本。
- `src/main/java/com/saasbase/auth/infrastructure/security/JwtTokenGateway.java`：读写 `session_version` claim。
- `src/main/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilter.java`：每次请求校验租户认证状态并识别全部平台租户权限。
- `src/main/java/com/saasbase/common/error/ErrorCode.java`：增加租户管理错误码。
- `src/main/java/com/saasbase/tenant/adapter/PlatformTenantController.java`：替换占位 `/ping` 为真实 API。
- `src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java`：验证新列与权限种子。
- `src/test/java/com/saasbase/api/ApiPathPartitionTest.java`：验证新增路径分区。

## 任务 1：扩展数据库基线和错误码

**文件：**
- 创建：`src/main/resources/db/migration/V3__tenant_management.sql`
- 修改：`src/main/java/com/saasbase/common/error/ErrorCode.java`
- 修改：`src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java`

- [ ] **步骤 1：编写失败的 Flyway 测试**

在 `FlywayMigrationTest` 增加断言：

```java
assertThat(columnExists(connection, "tenant", "session_version")).isTrue();
assertThat(queryLong(connection,
        "SELECT COUNT(*) FROM iam_permission WHERE permission_code IN (" +
        "'platform:tenant:create','platform:tenant:read','platform:tenant:update'," +
        "'platform:tenant:enable','platform:tenant:disable','tenant:profile:read')"))
        .isEqualTo(6L);
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=FlywayMigrationTest test`

预期：FAIL，缺少 `tenant.session_version` 或权限记录数不是 6。

- [ ] **步骤 3：修改迁移和错误码**

新增迁移，不修改已经发布的 `V1`、`V2`：

```sql
ALTER TABLE tenant
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0
    COMMENT '租户认证会话版本' AFTER status;
```

在建表完成后插入 6 个稳定 ID 的 `API` 权限，使用项目统一 ID 规则并保证可重复从空库迁移。向 `ErrorCode` 增加：

```java
AUTH_TENANT_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "租户登录状态已失效"),
TENANT_DISABLED(HttpStatus.FORBIDDEN, "租户已停用"),
TENANT_CODE_CONFLICT(HttpStatus.CONFLICT, "租户编码已存在"),
TENANT_STATUS_CONFLICT(HttpStatus.CONFLICT, "租户状态冲突"),
TENANT_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "租户数据已被修改"),
IAM_USERNAME_CONFLICT(HttpStatus.CONFLICT, "用户名已存在"),
IAM_PERMISSION_TEMPLATE_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "租户权限模板缺失"),
TENANT_INITIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "租户初始化失败"),
```

- [ ] **步骤 4：运行迁移测试验证通过**

运行：`mvn -Dtest=FlywayMigrationTest test`

预期：PASS，空 MySQL 可以迁移且 6 个权限存在。

- [ ] **步骤 5：提交**

```bash
git add src/main/resources/db/migration/V3__tenant_management.sql src/main/java/com/saasbase/common/error/ErrorCode.java src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java
git commit -m "增加租户会话版本和权限基线"
```

## 任务 2：实现租户领域模型

**文件：**
- 创建：`src/main/java/com/saasbase/tenant/domain/TenantStatus.java`
- 创建：`src/main/java/com/saasbase/tenant/domain/TenantAuthState.java`
- 创建：`src/main/java/com/saasbase/tenant/domain/Tenant.java`
- 测试：`src/test/java/com/saasbase/tenant/domain/TenantTest.java`

- [ ] **步骤 1：编写失败的领域测试**

```java
@Test
void disable_increments_session_version_and_enable_keeps_it() {
    Tenant tenant = Tenant.reconstitute(1L, "acme", "Acme", TenantStatus.ACTIVE, 3L, 0L);
    tenant.disable();
    assertThat(tenant.status()).isEqualTo(TenantStatus.DISABLED);
    assertThat(tenant.sessionVersion()).isEqualTo(4L);
    tenant.enable();
    assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
    assertThat(tenant.sessionVersion()).isEqualTo(4L);
}

@Test
void rejects_repeated_disable() {
    Tenant tenant = Tenant.reconstitute(1L, "acme", "Acme", TenantStatus.DISABLED, 4L, 0L);
    assertThatThrownBy(tenant::disable)
            .isInstanceOf(BizException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.TENANT_STATUS_CONFLICT);
}
```

同时覆盖空名称、创建后编码不可变和重复启用。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=TenantTest test`

预期：FAIL，领域类型尚不存在。

- [ ] **步骤 3：实现最小领域模型**

```java
public final class Tenant {
    private final Long id;
    private final String tenantCode;
    private String tenantName;
    private TenantStatus status;
    private long sessionVersion;
    private long version;

    public void rename(String tenantName) { this.tenantName = requireName(tenantName); }
    public void disable() {
        requireStatus(TenantStatus.ACTIVE);
        status = TenantStatus.DISABLED;
        sessionVersion++;
    }
    public void enable() {
        requireStatus(TenantStatus.DISABLED);
        status = TenantStatus.ACTIVE;
    }
    public TenantAuthState authState() {
        return new TenantAuthState(id, status, sessionVersion);
    }
}
```

提供 `create(...)`、`reconstitute(...)` 工厂和只读访问器，不提供修改 `tenantCode` 的方法。

- [ ] **步骤 4：运行领域测试验证通过**

运行：`mvn -Dtest=TenantTest test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/tenant/domain src/test/java/com/saasbase/tenant/domain
git commit -m "实现租户领域状态规则"
```

## 任务 3：定义租户网关和 DTO

**文件：**
- 创建：`src/main/java/com/saasbase/tenant/domain/gateway/TenantGateway.java`
- 创建：`src/main/java/com/saasbase/tenant/domain/gateway/TenantAdminInitializer.java`
- 创建：`src/main/java/com/saasbase/tenant/domain/gateway/TenantAuthStateGateway.java`
- 创建：`src/main/java/com/saasbase/tenant/application/dto/CreateTenantRequest.java`
- 创建：`src/main/java/com/saasbase/tenant/application/dto/UpdateTenantRequest.java`
- 创建：`src/main/java/com/saasbase/tenant/application/dto/TenantQuery.java`
- 创建：`src/main/java/com/saasbase/tenant/application/dto/TenantResponse.java`

- [ ] **步骤 1：定义可编译的接口契约**

```java
public interface TenantGateway {
    boolean existsByCode(String tenantCode);
    Tenant insert(Tenant tenant, Long operatorId);
    Optional<Tenant> findById(Long tenantId);
    PageResponse<Tenant> page(TenantQuery query);
    boolean update(Tenant tenant, Long operatorId);
}

public interface TenantAdminInitializer {
    void initialize(Long tenantId, String username, String displayName, String rawPassword, Long operatorId);
}

public interface TenantAuthStateGateway {
    TenantAuthState requireCurrent(Long tenantId);
    void cache(TenantAuthState state);
}
```

请求 DTO 使用 Jakarta Validation：`tenantCode` 只允许 `[a-z0-9][a-z0-9-]{1,62}[a-z0-9]`，名称和显示名非空且限制长度，初始密码长度为 12–72。

- [ ] **步骤 2：增加 DTO 校验测试**

创建 `src/test/java/com/saasbase/tenant/application/dto/CreateTenantRequestValidationTest.java`，使用 `jakarta.validation.Validator` 验证非法编码和短密码被拒绝、合法请求无错误。

- [ ] **步骤 3：运行契约测试**

运行：`mvn -Dtest=CreateTenantRequestValidationTest test`

预期：PASS。

- [ ] **步骤 4：提交**

```bash
git add src/main/java/com/saasbase/tenant/domain/gateway src/main/java/com/saasbase/tenant/application/dto src/test/java/com/saasbase/tenant/application/dto
git commit -m "定义租户管理接口契约"
```

## 任务 4：实现租户 MyBatis 持久化

**文件：**
- 创建：`src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantRecord.java`
- 创建：`src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantMapper.java`
- 创建：`src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantPersistenceAdapter.java`
- 创建：`src/main/resources/mapper/tenant/TenantMapper.xml`
- 测试：`src/test/java/com/saasbase/tenant/infrastructure/persistence/TenantPersistenceAdapterIntegrationTest.java`

- [ ] **步骤 1：编写失败的 Testcontainers 集成测试**

覆盖以下场景：插入后按 ID 查询；`tenantCode` 唯一；按编码、名称、状态分页；固定按 `created_at DESC, id DESC`；旧 `version` 更新返回 `false`。

```java
Tenant inserted = gateway.insert(Tenant.create("acme", "Acme"), 9001L);
Tenant stale = gateway.findById(inserted.id()).orElseThrow();
Tenant fresh = gateway.findById(inserted.id()).orElseThrow();
fresh.rename("Acme One");
assertThat(gateway.update(fresh, 9001L)).isTrue();
stale.rename("Stale");
assertThat(gateway.update(stale, 9001L)).isFalse();
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=TenantPersistenceAdapterIntegrationTest test`

预期：FAIL，Mapper 和适配器尚不存在。

- [ ] **步骤 3：实现 Mapper 和 XML**

更新 SQL 必须包含版本条件并原子递增：

```sql
UPDATE tenant
SET tenant_name = #{tenantName}, status = #{status},
    session_version = #{sessionVersion}, version = version + 1,
    updated_at = #{updatedAt}, updated_by = #{updatedBy}
WHERE id = #{id} AND version = #{version} AND deleted = 0
```

分页 SQL 只拼接白名单条件，不接受客户端排序表达式。

- [ ] **步骤 4：运行集成测试验证通过**

运行：`mvn -Dtest=TenantPersistenceAdapterIntegrationTest test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/tenant/infrastructure/persistence src/main/resources/mapper/tenant src/test/java/com/saasbase/tenant/infrastructure/persistence
git commit -m "实现租户持久化和分页查询"
```

## 任务 5：实现首个管理员原子初始化

**文件：**
- 创建：`src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantAdminMapper.java`
- 创建：`src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantAdminPersistenceAdapter.java`
- 创建：`src/main/resources/mapper/tenant/TenantAdminMapper.xml`
- 测试：`src/test/java/com/saasbase/tenant/infrastructure/persistence/TenantAdminPersistenceAdapterIntegrationTest.java`

- [ ] **步骤 1：编写失败的初始化集成测试**

调用 `initialize(tenantId, "admin", "管理员", "ValidPassword1!", operatorId)` 后断言：

```java
assertThat(queryLong("SELECT COUNT(*) FROM iam_user WHERE tenant_id=? AND username='admin'", tenantId)).isEqualTo(1);
assertThat(queryLong("SELECT COUNT(*) FROM iam_role WHERE tenant_id=? AND role_code='TENANT_ADMIN'", tenantId)).isEqualTo(1);
assertThat(queryLong("SELECT COUNT(*) FROM iam_user_role WHERE tenant_id=?", tenantId)).isEqualTo(1);
assertThat(queryLong("SELECT COUNT(*) FROM iam_role_permission WHERE tenant_id=?", tenantId)).isEqualTo(1);
assertThat(passwordEncoder.matches("ValidPassword1!", queryString("SELECT password_hash ..."))).isTrue();
```

另测用户名冲突映射为 `IAM_USERNAME_CONFLICT`，缺少 `tenant:profile:read` 模板时映射为 `IAM_PERMISSION_TEMPLATE_MISSING`。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=TenantAdminPersistenceAdapterIntegrationTest test`

预期：FAIL，初始化适配器不存在。

- [ ] **步骤 3：实现最小 IAM 初始化**

适配器依次插入 `iam_user`、`iam_role`、`iam_user_role`，查询内置租户权限 ID 后批量插入 `iam_role_permission`。只复制 `tenant:*` 权限，不把 `platform:*` 权限赋给租户管理员。所有 ID 在应用进程生成，密码先 BCrypt 哈希。

- [ ] **步骤 4：运行初始化集成测试验证通过**

运行：`mvn -Dtest=TenantAdminPersistenceAdapterIntegrationTest test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/tenant/infrastructure/persistence/TenantAdmin* src/main/resources/mapper/tenant/TenantAdminMapper.xml src/test/java/com/saasbase/tenant/infrastructure/persistence/TenantAdminPersistenceAdapterIntegrationTest.java
git commit -m "实现租户首个管理员初始化"
```

## 任务 6：实现 5 秒租户认证状态缓存

**文件：**
- 创建：`src/main/java/com/saasbase/tenant/infrastructure/redis/RedisTenantAuthStateGateway.java`
- 测试：`src/test/java/com/saasbase/tenant/infrastructure/redis/RedisTenantAuthStateGatewayTest.java`

- [ ] **步骤 1：编写失败的缓存测试**

使用 Mockito 验证键、TTL、命中解析、未命中回源和 Redis 异常回源：

```java
TenantAuthState state = gateway.requireCurrent(2001L);
assertThat(state).isEqualTo(new TenantAuthState(2001L, TenantStatus.ACTIVE, 3L));
verify(valueOperations).set(
        eq("saasbase:tenant:auth-state:2001"),
        eq("ACTIVE:3"), eq(5L), eq(TimeUnit.SECONDS));
```

MySQL 回源也失败时异常必须向上抛出，由认证过滤器 fail-closed。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=RedisTenantAuthStateGatewayTest test`

预期：FAIL，缓存适配器不存在。

- [ ] **步骤 3：实现缓存适配器**

构造器注入 `StringRedisTemplate` 和 `TenantGateway`。`requireCurrent` 先读 Redis，未命中或 `DataAccessException` 时读取 MySQL；成功回源后尝试写缓存。`cache` 固定使用 5 秒 TTL。

- [ ] **步骤 4：运行缓存测试验证通过**

运行：`mvn -Dtest=RedisTenantAuthStateGatewayTest test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/tenant/infrastructure/redis src/test/java/com/saasbase/tenant/infrastructure/redis
git commit -m "增加租户认证状态缓存"
```

## 任务 7：扩展 JWT 和刷新会话版本契约

**文件：**
- 修改：`src/main/java/com/saasbase/auth/domain/UserPrincipal.java`
- 修改：`src/main/java/com/saasbase/auth/application/AuthApplicationService.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/security/JwtTokenGateway.java`
- 修改：`src/test/java/com/saasbase/auth/infrastructure/security/JwtTokenGatewayTest.java`
- 修改：`src/test/java/com/saasbase/auth/application/AuthApplicationServiceTest.java`

- [ ] **步骤 1：编写失败的 JWT 测试**

```java
UserPrincipal principal = new UserPrincipal(1001L, 2001L, "alice", Set.of(), 7L);
UserPrincipal parsed = gateway.parseAccessToken(gateway.issueAccessToken(principal));
assertThat(parsed.sessionVersion()).isEqualTo(7L);
```

更新应用测试，登录从 `TenantAuthStateGateway` 取得版本 7，刷新时版本不一致返回 `AUTH_TENANT_SESSION_EXPIRED`。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=JwtTokenGatewayTest,AuthApplicationServiceTest test`

预期：FAIL，`UserPrincipal` 没有 `sessionVersion`。

- [ ] **步骤 3：实现 Token 版本快照**

将主体改为：

```java
public record UserPrincipal(
        Long userId, Long tenantId, String username,
        Set<String> permissions, long sessionVersion) {}
```

JWT claim 使用 `session_version`。Refresh Token JSON 增加 `sessionVersion`。登录签发前读取当前租户认证状态；刷新时先解析快照，再要求当前租户为 `ACTIVE` 且版本一致，之后才进行原子轮换。

- [ ] **步骤 4：更新所有构造调用并运行认证测试**

运行：`mvn -Dtest='com.saasbase.auth.*' test`

预期：PASS，现有登录、刷新、注销测试及新增版本测试全部通过。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/auth src/test/java/com/saasbase/auth
git commit -m "为认证令牌增加租户会话版本"
```

## 任务 8：在认证过滤器中执行 fail-closed 校验

**文件：**
- 修改：`src/main/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilter.java`
- 修改：`src/test/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilterTest.java`

- [ ] **步骤 1：编写失败的过滤器测试**

覆盖有效状态放行、租户停用返回 401、版本不一致返回 401、状态网关异常返回 401，以及任意 `platform:tenant:*` 权限仅在 `/api/v1/platform/**` 建立平台上下文。

```java
when(tokenGateway.parseAccessToken("token")).thenReturn(
        new UserPrincipal(1L, 2L, "admin", Set.of("tenant:profile:read"), 3L));
when(authStateGateway.requireCurrent(2L)).thenReturn(
        new TenantAuthState(2L, TenantStatus.DISABLED, 4L));
filter.apply(requestWithBearer("token"), response, chain);
assertThat(response.getStatus()).isEqualTo(401);
assertThat(response.getContentAsString()).contains("AUTH_TENANT_SESSION_EXPIRED");
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=JwtAuthenticationFilterTest test`

预期：FAIL，过滤器尚未注入和校验 `TenantAuthStateGateway`。

- [ ] **步骤 3：实现过滤器校验**

解析 JWT 后、写入 SecurityContext 前读取认证状态：

```java
TenantAuthState current = tenantAuthStateGateway.requireCurrent(principal.tenantId());
if (current.status() != TenantStatus.ACTIVE ||
        current.sessionVersion() != principal.sessionVersion()) {
    throw new BizException(ErrorCode.AUTH_TENANT_SESSION_EXPIRED);
}
```

平台权限判断改为 `permission.startsWith("platform:tenant:")`，并继续要求请求路径以 `/api/v1/platform/` 开头。

- [ ] **步骤 4：运行过滤器及安全回归测试**

运行：`mvn -Dtest=JwtAuthenticationFilterTest,ApiPathPartitionTest test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilter.java src/test/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilterTest.java
git commit -m "校验租户认证状态和会话版本"
```

## 任务 9：实现租户应用服务和事务编排

**文件：**
- 创建：`src/main/java/com/saasbase/tenant/application/TenantApplicationService.java`
- 测试：`src/test/java/com/saasbase/tenant/application/TenantApplicationServiceTest.java`
- 测试：`src/test/java/com/saasbase/tenant/application/TenantApplicationServiceTransactionTest.java`

- [ ] **步骤 1：编写失败的应用层单元测试**

覆盖创建编排、编码冲突、重命名、启用、停用、重复状态、乐观锁冲突、平台分页、当前租户资料和事务提交后更新缓存。

```java
TenantResponse created = service.create(request, operatorId);
InOrder order = inOrder(tenantGateway, initializer, auditGateway);
order.verify(tenantGateway).insert(any(Tenant.class), eq(operatorId));
order.verify(initializer).initialize(anyLong(), eq("admin"), eq("管理员"), anyString(), eq(operatorId));
order.verify(auditGateway).appendAdminOperationAudit(any());
```

- [ ] **步骤 2：运行单元测试验证失败**

运行：`mvn -Dtest=TenantApplicationServiceTest test`

预期：FAIL，应用服务不存在。

- [ ] **步骤 3：实现应用服务**

类上使用 `@Service`，写方法使用 `@Transactional`。缓存更新通过 `TransactionSynchronizationManager.registerSynchronization(...)` 放在 `afterCommit()`，避免发布未提交状态。乐观锁更新返回 `false` 时抛出 `TENANT_CONCURRENT_MODIFICATION`。

管理审计事件分别使用 `CREATE`、`UPDATE`、`ENABLE`、`DISABLE` 和 `TENANT`，`resourceId` 为租户 ID 字符串。

- [ ] **步骤 4：验证数据库事务回滚**

`TenantApplicationServiceTransactionTest` 使用 Testcontainers：让管理员初始化在插入用户后抛异常，断言租户、用户、角色和审计均未落库。

运行：`mvn -Dtest=TenantApplicationServiceTest,TenantApplicationServiceTransactionTest test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/tenant/application src/test/java/com/saasbase/tenant/application
git commit -m "实现租户管理事务编排"
```

## 任务 10：实现平台与租户 API

**文件：**
- 修改：`src/main/java/com/saasbase/tenant/adapter/PlatformTenantController.java`
- 创建：`src/main/java/com/saasbase/tenant/adapter/AdminTenantProfileController.java`
- 测试：`src/test/java/com/saasbase/tenant/adapter/PlatformTenantControllerTest.java`
- 测试：`src/test/java/com/saasbase/tenant/adapter/AdminTenantProfileControllerTest.java`
- 修改：`src/test/java/com/saasbase/api/ApiPathPartitionTest.java`

- [ ] **步骤 1：编写失败的 MockMvc 测试**

逐个验证 7 个 API、对应 `@PreAuthorize` 权限、请求校验、分页响应和当前租户上下文。示例：

```java
mockMvc.perform(post("/api/v1/platform/tenants")
        .with(jwt().authorities(new SimpleGrantedAuthority("platform:tenant:create")))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tenantCode").value("acme"))
        .andExpect(jsonPath("$..initialPassword").doesNotExist())
        .andExpect(jsonPath("$..passwordHash").doesNotExist());
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=PlatformTenantControllerTest,AdminTenantProfileControllerTest,ApiPathPartitionTest test`

预期：FAIL，真实端点尚未实现。

- [ ] **步骤 3：实现 Controller**

使用规格中的路径和权限：

```java
@PostMapping
@PreAuthorize("hasAuthority('platform:tenant:create')")
public ApiResponse<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) { ... }

@GetMapping("/profile")
@PreAuthorize("hasAuthority('tenant:profile:read')")
public ApiResponse<TenantResponse> profile() {
    return ApiResponse.ok(service.currentProfile(TenantContextHolder.require().tenantId()));
}
```

移除原有两个 `/ping` 占位接口。所有响应使用 `ApiResponse`，分页数据使用 `PageResponse<TenantResponse>`。

- [ ] **步骤 4：运行 Adapter 测试验证通过**

运行：`mvn -Dtest=PlatformTenantControllerTest,AdminTenantProfileControllerTest,ApiPathPartitionTest test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/tenant/adapter src/test/java/com/saasbase/tenant/adapter src/test/java/com/saasbase/api/ApiPathPartitionTest.java
git commit -m "实现租户管理 API"
```

## 任务 11：端到端认证失效与架构验收

**文件：**
- 创建：`src/test/java/com/saasbase/tenant/TenantManagementEndToEndTest.java`
- 修改：`src/test/java/com/saasbase/architecture/ColaArchitectureTest.java`
- 修改：`docs/postman/SaaSBase.postman_collection.json`
- 修改：`README.md`

- [ ] **步骤 1：编写端到端失败测试**

使用 Testcontainers MySQL 和 Redis，依次执行：平台创建租户、管理员登录、访问 `/api/v1/admin/tenant/profile`、平台停用、旧 Access Token 被拒绝、旧 Refresh Token 被拒绝、重新启用、旧 Token 仍被拒绝、重新登录成功。

关键断言：

```java
assertThat(profileBeforeDisable.getStatusCode()).isEqualTo(HttpStatus.OK);
assertThat(profileAfterDisable.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
assertThat(oldRefreshAfterEnable.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
```

- [ ] **步骤 2：运行端到端测试并修复集成缺口**

运行：`mvn -Dtest=TenantManagementEndToEndTest test`

预期：首次运行暴露的 Bean、SQL、事务或安全链路问题被逐项修复，最终 PASS。不得通过放宽权限、移除状态校验或延长缓存规避失败。

- [ ] **步骤 3：更新架构测试和接口文档**

确保 `tenant.domain` 不依赖 `application`、`adapter`、`infrastructure`，`adapter` 不直接依赖基础设施。Postman 集合补齐 7 个接口，README 的“说明”增加租户管理能力与停用最多 5 秒异常缓存窗口说明。

- [ ] **步骤 4：运行完整验证**

运行：

```bash
mvn test
mvn package -DskipTests
git diff --check
```

预期：全部命令退出码为 0；测试无 failure/error；构建生成可执行 JAR；无空白错误。

- [ ] **步骤 5：核对规格验收项**

逐项核对：7 个 API、原子初始化、编码不可变、无删除、平台边界、5 秒缓存、旧 Token 永不恢复、OpenAPI 可见、现有回归测试通过。把任何遗漏补回对应任务并重新运行完整验证。

- [ ] **步骤 6：提交**

提交前先按项目规则展示最终变更摘要，然后运行：

```bash
git add src/test/java/com/saasbase/tenant/TenantManagementEndToEndTest.java src/test/java/com/saasbase/architecture/ColaArchitectureTest.java docs/postman/SaaSBase.postman_collection.json README.md
git commit -m "完成租户管理闭环验收"
```
