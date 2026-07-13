# 用户管理实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现租户内用户创建、编辑、查询、启停、密码重置、角色绑定和用户级会话立即失效闭环。

**架构：** `iam` 模块按 COLA light 分层，Controller 处理 HTTP 契约，Application Service 负责事务编排，`IamUser` 维护状态与会话版本，MyBatis 和 Redis 分别实现持久化与认证状态网关。`auth` 只消费用户认证状态并签发带用户会话版本的 Token。

**技术栈：** Java 25、Spring Boot 4.1、Spring Security、MyBatis-Plus、MySQL 8.4、Redis、Flyway、JUnit 5、MockMvc、Testcontainers

---

## 文件结构

- `src/main/resources/db/migration/V3__add_user_management.sql`：用户管理字段、索引和权限种子。
- `src/main/java/com/saasbase/iam/domain/**`：用户聚合、查询对象、认证状态及 Gateway。
- `src/main/java/com/saasbase/iam/application/**`：命令、响应和事务用例。
- `src/main/java/com/saasbase/iam/adapter/AdminUserController.java`：租户管理 API。
- `src/main/java/com/saasbase/iam/infrastructure/persistence/**`：MyBatis 持久化、部门与角色边界。
- `src/main/java/com/saasbase/iam/infrastructure/redis/RedisUserSessionGateway.java`：5 秒用户认证状态缓存。
- `src/main/java/com/saasbase/auth/**`：登录、刷新、JWT 和请求过滤器接入用户状态。
- `src/test/java/com/saasbase/iam/**`：领域、应用、Adapter、基础设施和闭环测试。

## 任务 1：数据库迁移与错误契约

**文件：**
- 创建：`src/main/resources/db/migration/V3__add_user_management.sql`
- 修改：`src/main/java/com/saasbase/common/error/ErrorCode.java`
- 测试：`src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java`

- [ ] **步骤 1：编写失败的迁移测试**

在 `FlywayMigrationTest` 断言用户管理字段和权限点：

```java
assertThat(columnNames("iam_user")).contains(
        "phone", "primary_department_id", "must_change_password",
        "session_version", "last_login_at");
assertThat(permissionCodes()).contains(
        "tenant:user:create", "tenant:user:read", "tenant:user:update",
        "tenant:user:enable", "tenant:user:disable", "tenant:user:reset-password");
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn -Dtest=FlywayMigrationTest test
```

预期：FAIL，缺少用户管理字段或权限数据。

- [ ] **步骤 3：新增前向迁移**

不得修改已发布的 `V1`。迁移核心内容：

```sql
ALTER TABLE iam_user
    ADD COLUMN phone VARCHAR(32) NULL,
    ADD COLUMN primary_department_id BIGINT NOT NULL,
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_login_at DATETIME(6) NULL,
    ADD KEY idx_iam_user_tenant_dept (tenant_id, primary_department_id),
    ADD KEY idx_iam_user_tenant_phone (tenant_id, phone);
ALTER TABLE iam_dept ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE iam_role ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
```

插入六个稳定的 `tenant:user:*` 权限编码；固定 ID 必须先确认未被现有迁移占用。

- [ ] **步骤 4：增加规格错误码**

在 `ErrorCode` 增加 `IAM_USER_NOT_FOUND`、`IAM_USERNAME_CONFLICT`、`IAM_USER_STATUS_CONFLICT`、`IAM_USER_CONCURRENT_MODIFICATION`、部门/角色不存在与停用、跨租户引用、最后管理员保护、自操作禁止，以及 `AUTH_USER_DISABLED`、`AUTH_USER_SESSION_EXPIRED`、`AUTH_PASSWORD_CHANGE_REQUIRED`，HTTP 状态严格采用规格映射。

- [ ] **步骤 5：验证并提交**

```bash
mvn -Dtest=FlywayMigrationTest test
git add src/main/resources/db/migration/V3__add_user_management.sql src/main/java/com/saasbase/common/error/ErrorCode.java src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java
git commit -m "增加用户管理数据结构"
```

预期：PASS；提交只包含本任务文件。

## 任务 2：用户领域模型

**文件：**
- 创建：`src/main/java/com/saasbase/iam/domain/UserStatus.java`
- 创建：`src/main/java/com/saasbase/iam/domain/IamUser.java`
- 创建：`src/main/java/com/saasbase/iam/domain/UserPageQuery.java`
- 测试：`src/test/java/com/saasbase/iam/domain/IamUserTest.java`

- [ ] **步骤 1：编写领域失败测试**

```java
@Test
void disableIncrementsSessionVersion() {
    IamUser user = activeUser(7L);
    user.disable();
    assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
    assertThat(user.sessionVersion()).isEqualTo(8L);
}

@Test
void enableDoesNotRestoreOldSessionVersion() {
    IamUser user = disabledUser(8L);
    user.enable();
    assertThat(user.sessionVersion()).isEqualTo(8L);
}
```

同时覆盖用户名无修改入口、重复启停冲突、密码重置设置 `mustChangePassword=true` 且递增版本。

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn -Dtest=IamUserTest test
```

预期：FAIL，领域类型尚不存在。

- [ ] **步骤 3：实现最小领域行为**

```java
public void disable() {
    if (status != UserStatus.ACTIVE) throw new BizException(ErrorCode.IAM_USER_STATUS_CONFLICT);
    status = UserStatus.DISABLED;
    sessionVersion++;
}
public void enable() {
    if (status != UserStatus.DISABLED) throw new BizException(ErrorCode.IAM_USER_STATUS_CONFLICT);
    status = UserStatus.ACTIVE;
}
public void resetPassword(String encodedPassword) {
    passwordHash = requireText(encodedPassword);
    mustChangePassword = true;
    sessionVersion++;
}
```

`UserPageQuery` 限定 `page >= 1`、`1 <= size <= 100`，只包含规格允许的过滤字段。

- [ ] **步骤 4：验证并提交**

```bash
mvn -Dtest=IamUserTest test
git add src/main/java/com/saasbase/iam/domain src/test/java/com/saasbase/iam/domain/IamUserTest.java
git commit -m "实现用户领域规则"
```

## 任务 3：持久化、部门与角色边界

**文件：**
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/UserGateway.java`
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/DepartmentReferenceGateway.java`
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/UserRoleAssignmentGateway.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/UserRecord.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/UserMapper.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/UserPersistenceAdapter.java`
- 创建：`src/main/resources/mapper/iam/UserMapper.xml`
- 测试：`src/test/java/com/saasbase/iam/infrastructure/persistence/UserPersistenceAdapterIntegrationTest.java`

- [ ] **步骤 1：编写集成失败测试**

验证同租户用户名冲突、不同租户可重名、跨租户部门/角色拒绝、固定排序、乐观锁和角色全量替换：

```java
assertThatThrownBy(() -> adapter.assertDepartmentActive(tenantA, departmentOfTenantB))
        .isInstanceOf(BizException.class);
adapter.replaceRoles(tenantA, userId, Set.of(roleA, roleB));
assertThat(mapper.findRoleIds(tenantA, userId)).containsExactlyInAnyOrder(roleA, roleB);
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn -Dtest=UserPersistenceAdapterIntegrationTest test
```

- [ ] **步骤 3：定义网关并实现显式租户 SQL**

```java
public interface UserGateway {
    boolean existsByUsername(long tenantId, String username);
    Optional<IamUser> findById(long tenantId, long userId);
    PageResponse<UserView> page(long tenantId, UserPageQuery query);
    void insert(IamUser user);
    boolean update(IamUser user);
}
```

每条业务 SQL 显式包含 `tenant_id = #{tenantId}`；分页固定使用：

```sql
ORDER BY u.created_at DESC, u.id DESC
LIMIT #{offset}, #{size}
```

最后管理员保护在事务内锁定 `TENANT_ADMIN` 角色行，再统计除目标用户外的有效管理员，避免并发绕过。

- [ ] **步骤 4：验证并提交**

```bash
mvn -Dtest=UserPersistenceAdapterIntegrationTest test
git add src/main/java/com/saasbase/iam/domain/gateway src/main/java/com/saasbase/iam/infrastructure/persistence src/main/resources/mapper/iam/UserMapper.xml src/test/java/com/saasbase/iam/infrastructure/persistence/UserPersistenceAdapterIntegrationTest.java
git commit -m "实现用户持久化与租户边界"
```

## 任务 4：应用服务、事务与审计

**文件：**
- 创建：`src/main/java/com/saasbase/iam/application/dto/UserCommands.java`
- 创建：`src/main/java/com/saasbase/iam/application/dto/UserView.java`
- 创建：`src/main/java/com/saasbase/iam/application/UserApplicationService.java`
- 创建：`src/main/java/com/saasbase/iam/domain/UserAuthState.java`
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/UserSessionGateway.java`
- 测试：`src/test/java/com/saasbase/iam/application/UserApplicationServiceTest.java`

- [ ] **步骤 1：编写用例失败测试**

覆盖创建编排、编辑角色、禁止自停用/自重置、最后管理员保护、缓存失败不篡改数据库结果：

```java
assertThatThrownBy(() -> service.disable(tenantId, operatorId, operatorId, version))
        .isInstanceOf(BizException.class);
doThrow(new RedisConnectionFailureException("down")).when(sessionGateway).put(any());
assertThatCode(() -> service.disable(tenantId, operatorId, targetId, version))
        .doesNotThrowAnyException();
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn -Dtest=UserApplicationServiceTest test
```

- [ ] **步骤 3：实现事务用例**

命令 DTO 使用 Jakarta Validation。Service 使用 `@Transactional`；创建、编辑、启停和重置密码追加 `AdminOperationAuditEvent`，重置密码另写安全审计，均不得记录密码。

缓存写入注册为提交后回调：

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override public void afterCommit() {
        try {
            userSessionGateway.put(UserAuthState.from(user));
        } catch (RuntimeException ex) {
            log.error("user auth cache update failed tenantId={} userId={}",
                    user.tenantId(), user.id(), ex);
        }
    }
});
```

- [ ] **步骤 4：验证并提交**

```bash
mvn -Dtest=UserApplicationServiceTest test
git add src/main/java/com/saasbase/iam/application src/main/java/com/saasbase/iam/domain/UserAuthState.java src/main/java/com/saasbase/iam/domain/gateway/UserSessionGateway.java src/test/java/com/saasbase/iam/application/UserApplicationServiceTest.java
git commit -m "实现用户管理应用服务"
```

## 任务 5：管理 API 与权限

**文件：**
- 创建：`src/main/java/com/saasbase/iam/adapter/AdminUserController.java`
- 测试：`src/test/java/com/saasbase/iam/adapter/AdminUserControllerTest.java`

- [ ] **步骤 1：编写 MockMvc 失败测试**

逐项验证七个路由、权限、参数校验、当前租户来源和响应无敏感字段：

```java
mockMvc.perform(post("/api/v1/admin/users")
        .with(user(principal).authorities(new SimpleGrantedAuthority("tenant:user:create")))
        .contentType(MediaType.APPLICATION_JSON)
        .content(validCreateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.initialPassword").doesNotExist());
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn -Dtest=AdminUserControllerTest test
```

预期：FAIL，路由返回 404。

- [ ] **步骤 3：实现 Controller**

```java
@PostMapping
@PreAuthorize("hasAuthority('tenant:user:create')")
public ApiResponse<UserView> create(@Valid @RequestBody CreateUserCommand command) {
    TenantContext context = TenantContextHolder.require();
    return ApiResponse.success(service.create(context.tenantId(), context.userId(), command));
}
```

其余方法使用对应 `tenant:user:*` 权限。请求 DTO 和查询参数中不得出现 `tenantId`。

- [ ] **步骤 4：验证并提交**

```bash
mvn -Dtest=AdminUserControllerTest test
git add src/main/java/com/saasbase/iam/adapter/AdminUserController.java src/test/java/com/saasbase/iam/adapter/AdminUserControllerTest.java
git commit -m "增加租户用户管理接口"
```

## 任务 6：Redis 用户认证状态

**文件：**
- 创建：`src/main/java/com/saasbase/iam/infrastructure/redis/RedisUserSessionGateway.java`
- 测试：`src/test/java/com/saasbase/iam/infrastructure/redis/RedisUserSessionGatewayIntegrationTest.java`

- [ ] **步骤 1：编写 Redis 失败测试**

验证 key、JSON、5 秒 TTL、未命中回源和 Redis 异常回源：

```java
gateway.put(new UserAuthState(tenantId, userId, UserStatus.ACTIVE, 3L, true));
assertThat(redisTemplate.getExpire("user:auth-state:" + tenantId + ":" + userId))
        .isBetween(1L, 5L);
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn -Dtest=RedisUserSessionGatewayIntegrationTest test
```

- [ ] **步骤 3：实现缓存与回源**

```java
private static final Duration TTL = Duration.ofSeconds(5);
private String key(long tenantId, long userId) {
    return "user:auth-state:" + tenantId + ":" + userId;
}
```

Redis 连接异常后调用 `UserGateway.findAuthState`；MySQL 也失败时继续抛出，由认证链路 fail-closed。不得增加 JVM 本地缓存。

- [ ] **步骤 4：验证并提交**

```bash
mvn -Dtest=RedisUserSessionGatewayIntegrationTest test
git add src/main/java/com/saasbase/iam/infrastructure/redis/RedisUserSessionGateway.java src/test/java/com/saasbase/iam/infrastructure/redis/RedisUserSessionGatewayIntegrationTest.java
git commit -m "实现用户认证状态缓存"
```

## 任务 7：认证链路接入用户状态

**文件：**
- 修改：`src/main/java/com/saasbase/auth/domain/UserPrincipal.java`
- 修改：`src/main/java/com/saasbase/auth/domain/UserCredential.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialRecord.java`
- 修改：`src/main/resources/mapper/auth/UserCredentialMapper.xml`
- 修改：`src/main/java/com/saasbase/auth/application/AuthApplicationService.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/security/JwtTokenGateway.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilter.java`
- 修改：`src/test/java/com/saasbase/auth/application/AuthApplicationServiceTest.java`
- 修改：`src/test/java/com/saasbase/auth/infrastructure/security/JwtTokenGatewayTest.java`
- 修改：`src/test/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilterTest.java`

- [ ] **步骤 1：编写认证失败测试**

验证停用用户不能登录、Refresh Token 版本过期、JWT claims 往返、强制改密白名单和双故障拒绝：

```java
UserPrincipal principal = new UserPrincipal(
        1L, 2L, "alice", permissions, 4L, true);
UserPrincipal parsed = gateway.parseAccessToken(gateway.issueAccessToken(principal));
assertThat(parsed.sessionVersion()).isEqualTo(4L);
assertThat(parsed.mustChangePassword()).isTrue();
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn -Dtest=AuthApplicationServiceTest,JwtTokenGatewayTest,JwtAuthenticationFilterTest test
```

- [ ] **步骤 3：扩展凭证与 Token 契约**

```java
public record UserPrincipal(
        Long userId, Long tenantId, String username, Set<String> permissions,
        long sessionVersion, boolean mustChangePassword) {}
```

JWT 和 Refresh Token JSON 使用稳定字段 `user_session_version`、`must_change_password`。登录先检查用户状态；刷新和每个受保护请求通过 `UserSessionGateway.getOrLoad` 校验当前状态和版本。

- [ ] **步骤 4：实现强制改密白名单**

仅允许：

```java
Set.of("/api/v1/auth/change-password", "/api/v1/auth/logout", "/api/v1/auth/me")
```

强制改密访问其他接口返回 `AUTH_PASSWORD_CHANGE_REQUIRED`；停用返回 403；版本不一致和双故障返回 401。请求结束始终清理租户和 Security 上下文。

- [ ] **步骤 5：验证并提交**

```bash
mvn -Dtest=AuthApplicationServiceTest,JwtTokenGatewayTest,JwtAuthenticationFilterTest test
git add src/main/java/com/saasbase/auth src/main/resources/mapper/auth/UserCredentialMapper.xml src/test/java/com/saasbase/auth
git commit -m "接入用户级认证状态校验"
```

## 任务 8：闭环与架构验证

**文件：**
- 修改：`src/test/java/com/saasbase/architecture/ColaArchitectureTest.java`
- 创建：`src/test/java/com/saasbase/iam/UserManagementIntegrationTest.java`

- [ ] **步骤 1：增加架构与闭环测试**

ArchUnit 验证 `iam.application` 不依赖 Mapper、Redis 和 `infrastructure`。Testcontainers 闭环验证创建用户、首次改密、停用后 Access/Refresh Token 失效、启用后旧 Token 仍无效、重新登录恢复：

```java
@Test
void disabledUserTokensNeverRecoverAfterEnable() {
    Tokens oldTokens = createChangePasswordAndLogin();
    disableUserAsTenantAdmin();
    assertUnauthorized(oldTokens.accessToken());
    enableUserAsTenantAdmin();
    assertUnauthorized(oldTokens.accessToken());
    assertRefreshRejected(oldTokens.refreshToken());
}
```

- [ ] **步骤 2：运行专项测试**

```bash
mvn -Dtest=ColaArchitectureTest,UserManagementIntegrationTest test
```

预期：PASS。

- [ ] **步骤 3：运行完整验证**

```bash
mvn test
mvn -q -DskipTests package
git diff --check
```

预期：全部退出码为 0，无空白错误，构建生成可执行包。

- [ ] **步骤 4：展示摘要并提交**

提交前先向用户展示变更摘要，再执行：

```bash
git add src/test/java/com/saasbase/architecture/ColaArchitectureTest.java src/test/java/com/saasbase/iam/UserManagementIntegrationTest.java
git commit -m "补充用户管理闭环验证"
```

