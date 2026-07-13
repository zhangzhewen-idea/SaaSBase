# 角色权限模块实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现租户隔离的角色生命周期、内置权限授权、用户角色分配、数据范围合并和授权变更后用户级会话立即失效。

**架构：** 新增独立 `iam` 能力域，按 adapter、application、domain、infrastructure 分层。角色生命周期、角色授权和用户角色分配分别由专注的应用服务编排；MyBatis 负责租户内持久化和加锁查询，认证域只消费聚合后的权限、数据范围和用户会话版本。

**技术栈：** Java 25、Spring Boot 4.1、Spring Security、MyBatis-Plus 3.5.14、MySQL 8.4、Flyway、JUnit 5、AssertJ、MockMvc、Testcontainers、ArchUnit

---

## 实施前提

- 先完成 `docs/superpowers/plans/2026-07-13-tenant-management.md`，确保租户初始化能创建租户管理员、`TENANT_ADMIN` 和内置权限绑定。
- 不修改当前用户已有的 `V1__init_core_schema.sql` 未提交变更；本功能使用新的 `V4__role_permission_management.sql`。
- 若执行时 `V3` 尚不存在，仍保留 `V4` 文件名，先完成租户管理计划再运行迁移。

## 文件结构

### 数据库与认证

- 创建 `src/main/resources/db/migration/V4__role_permission_management.sql`：角色类型、状态、数据范围及用户会话版本迁移，插入 IAM 权限点。
- 修改 `src/main/java/com/saasbase/auth/domain/UserCredential.java`：增加数据范围和会话版本。
- 修改 `src/main/java/com/saasbase/auth/domain/UserPrincipal.java`：增加数据范围和会话版本。
- 修改 `src/main/java/com/saasbase/auth/application/AuthApplicationService.java`：登录和刷新保存新授权快照。
- 创建 `src/main/java/com/saasbase/auth/domain/gateway/UserSessionVersionGateway.java`：认证请求校验用户会话版本。
- 修改 `src/main/java/com/saasbase/auth/infrastructure/security/JwtTokenGateway.java`：JWT 读写数据范围和会话版本。
- 修改 `src/main/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilter.java`：逐请求校验用户会话版本。
- 修改认证 Mapper、Record 和测试：聚合有效角色权限及最宽数据范围。

### IAM 领域与应用

- 创建 `src/main/java/com/saasbase/iam/domain/Role.java`：角色聚合和内置角色约束。
- 创建 `RoleType.java`、`RoleStatus.java`、`DataScope.java`、`EffectiveAuthorization.java`：稳定领域类型。
- 创建 `src/main/java/com/saasbase/iam/domain/LastTenantAdminPolicy.java`：最后管理员保护。
- 创建 `src/main/java/com/saasbase/iam/domain/gateway/` 下的角色、权限、用户角色和用户网关。
- 创建 `src/main/java/com/saasbase/iam/application/RoleApplicationService.java`：角色生命周期。
- 创建 `RoleAuthorizationService.java`：角色权限全量替换。
- 创建 `UserRoleAssignmentService.java`：用户角色全量替换。
- 创建 application DTO：请求命令、分页结果、角色详情、权限项和用户角色结果。

### IAM 持久化与 API

- 创建 `src/main/java/com/saasbase/iam/infrastructure/persistence/` Mapper、Record 和 Gateway Adapter。
- 创建 `src/main/resources/mapper/iam/RoleMapper.xml`、`PermissionMapper.xml`、`UserRoleMapper.xml`。
- 创建 `src/main/java/com/saasbase/iam/adapter/RoleController.java`、`PermissionController.java`、`RoleAuthorizationController.java`、`UserRoleController.java`。
- 修改 `src/main/java/com/saasbase/common/error/ErrorCode.java`：增加明确 IAM 错误码。
- 修改 `src/main/java/com/saasbase/system/infrastructure/openapi/OpenApiConfig.java`：增加 IAM API 分组。

## 任务 1：数据库迁移与领域基础类型

**文件：**
- 创建：`src/main/resources/db/migration/V4__role_permission_management.sql`
- 创建：`src/main/java/com/saasbase/iam/domain/RoleType.java`
- 创建：`src/main/java/com/saasbase/iam/domain/RoleStatus.java`
- 创建：`src/main/java/com/saasbase/iam/domain/DataScope.java`
- 创建：`src/main/java/com/saasbase/iam/domain/EffectiveAuthorization.java`
- 测试：`src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java`
- 测试：`src/test/java/com/saasbase/iam/domain/DataScopeTest.java`

- [ ] **步骤 1：编写失败的迁移测试**

在 `FlywayMigrationTest` 增加元数据和种子断言：

```java
@Test
void migratesRolePermissionManagementSchema() throws Exception {
    Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
            .locations("classpath:db/migration").load().migrate();
    try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
         var statement = connection.createStatement()) {
        try (var columns = connection.getMetaData().getColumns(null, null, "iam_role", "data_scope")) {
            assertThat(columns.next()).isTrue();
        }
        try (var result = statement.executeQuery("SELECT COUNT(*) FROM iam_permission WHERE permission_code LIKE 'iam:%'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(8);
        }
    }
}
```

- [ ] **步骤 2：运行迁移测试确认失败**

运行：`./mvnw -Dtest=FlywayMigrationTest test`；若仓库没有 Maven Wrapper，运行 `mvn -Dtest=FlywayMigrationTest test`。

预期：FAIL，`iam_role.data_scope` 不存在或 IAM 权限数量不是 8。

- [ ] **步骤 3：编写迁移**

```sql
ALTER TABLE iam_role
    ADD COLUMN role_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOM' AFTER role_name,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER role_type,
    ADD COLUMN data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF' AFTER status,
    ADD KEY idx_iam_role_tenant_status (tenant_id, status);

ALTER TABLE iam_user
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0 AFTER status;

INSERT INTO iam_permission (id, permission_code, permission_name, permission_type, created_at) VALUES
(41001, 'iam:role:read', '查询角色', 'API', CURRENT_TIMESTAMP(6)),
(41002, 'iam:role:create', '创建角色', 'API', CURRENT_TIMESTAMP(6)),
(41003, 'iam:role:update', '修改角色', 'API', CURRENT_TIMESTAMP(6)),
(41004, 'iam:role:delete', '删除角色', 'API', CURRENT_TIMESTAMP(6)),
(41005, 'iam:role:authorize', '角色授权', 'API', CURRENT_TIMESTAMP(6)),
(41006, 'iam:user-role:read', '查询用户角色', 'API', CURRENT_TIMESTAMP(6)),
(41007, 'iam:user-role:assign', '分配用户角色', 'API', CURRENT_TIMESTAMP(6)),
(41008, 'iam:permission:read', '查询权限目录', 'API', CURRENT_TIMESTAMP(6));
```

在迁移中把已有 `TENANT_ADMIN` 更新为 `BUILT_IN`，其余角色保持 `CUSTOM`。本模块固定保留权限 ID 区间 `41001-41008`；实现租户管理迁移时不得占用该区间。

- [ ] **步骤 4：编写并通过数据范围合并测试**

```java
@Test
void mergesToWidestScopeAndFallsBackWithoutDepartment() {
    assertThat(DataScope.merge(Set.of(DataScope.SELF, DataScope.DEPT_AND_CHILDREN), true))
            .isEqualTo(DataScope.DEPT_AND_CHILDREN);
    assertThat(DataScope.merge(Set.of(DataScope.DEPT_ONLY), false)).isEqualTo(DataScope.SELF);
}
```

实现 `DataScope` 的显式 rank 和 `merge(Set<DataScope>, boolean hasDepartment)`；空角色集合返回 `SELF`。

- [ ] **步骤 5：运行测试并提交**

运行：`mvn -Dtest=FlywayMigrationTest,DataScopeTest test`

预期：PASS。

```bash
git add src/main/resources/db/migration/V4__role_permission_management.sql src/main/java/com/saasbase/iam/domain src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java src/test/java/com/saasbase/iam/domain/DataScopeTest.java
git commit -m "添加角色权限数据模型"
```

## 任务 2：角色聚合与最后管理员策略

**文件：**
- 创建：`src/main/java/com/saasbase/iam/domain/Role.java`
- 创建：`src/main/java/com/saasbase/iam/domain/LastTenantAdminPolicy.java`
- 修改：`src/main/java/com/saasbase/common/error/ErrorCode.java`
- 测试：`src/test/java/com/saasbase/iam/domain/RoleTest.java`
- 测试：`src/test/java/com/saasbase/iam/domain/LastTenantAdminPolicyTest.java`

- [ ] **步骤 1：编写失败的内置角色测试**

```java
@Test
void builtInTenantAdminAllowsNameAndAuthorizationButRejectsDisableDeleteAndCodeChange() {
    Role role = Role.restore(1L, 10L, "TENANT_ADMIN", "管理员", RoleType.BUILT_IN,
            RoleStatus.ACTIVE, DataScope.ALL, 0L);
    role.rename("租户管理员");
    role.changeDataScope(DataScope.DEPT_ONLY);
    assertThatThrownBy(role::disable).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> role.changeCode("ADMIN")).isInstanceOf(BizException.class);
    assertThatThrownBy(role::delete).isInstanceOf(BizException.class);
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -Dtest=RoleTest,LastTenantAdminPolicyTest test`

预期：FAIL，领域类型尚不存在。

- [ ] **步骤 3：实现最小领域规则**

`Role` 暴露 `create`、`restore`、`rename`、`changeDataScope`、`enable`、`disable`、`delete`；`roleCode` 只在创建时赋值，不提供普通修改入口。为明确测试禁止行为，`changeCode` 仅允许新值等于原值，否则抛 `IAM_BUILT_IN_ROLE_PROTECTED`。

```java
public final class LastTenantAdminPolicy {
    public void ensureAssignable(boolean targetHasTenantAdmin, boolean removingTenantAdmin,
                                 long activeTenantAdminCount) {
        if (targetHasTenantAdmin && removingTenantAdmin && activeTenantAdminCount <= 1) {
            throw new BizException(ErrorCode.IAM_LAST_TENANT_ADMIN);
        }
    }
}
```

新增错误码：`IAM_ROLE_NOT_FOUND`、`IAM_USER_NOT_FOUND`、`IAM_ROLE_CODE_CONFLICT`、`IAM_BUILT_IN_ROLE_PROTECTED`、`IAM_LAST_TENANT_ADMIN`、`IAM_OPTIMISTIC_LOCK_CONFLICT`，分别映射 `404` 或 `409`。

- [ ] **步骤 4：运行领域测试并提交**

运行：`mvn -Dtest=RoleTest,LastTenantAdminPolicyTest test`

预期：PASS。

```bash
git add src/main/java/com/saasbase/iam/domain src/main/java/com/saasbase/common/error/ErrorCode.java src/test/java/com/saasbase/iam/domain
git commit -m "实现角色领域规则"
```

## 任务 3：角色持久化与角色生命周期服务

**文件：**
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/RoleGateway.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/RoleMapper.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/RoleRecord.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/RolePersistenceAdapter.java`
- 创建：`src/main/resources/mapper/iam/RoleMapper.xml`
- 创建：`src/main/java/com/saasbase/iam/application/RoleApplicationService.java`
- 创建：`src/main/java/com/saasbase/iam/application/dto/RoleCommands.java`
- 创建：`src/main/java/com/saasbase/iam/application/dto/RoleView.java`
- 测试：`src/test/java/com/saasbase/iam/application/RoleApplicationServiceTest.java`
- 测试：`src/test/java/com/saasbase/iam/infrastructure/persistence/RolePersistenceAdapterIntegrationTest.java`

- [ ] **步骤 1：编写失败的应用服务测试**

覆盖创建编码冲突、更新乐观锁、内置角色禁止停用/删除、普通角色软删除和只使用 `TenantContextHolder.require().tenantId()`。

```java
@Test
void createsCustomRoleInCurrentTenant() {
    TenantContextHolder.set(new TenantContext(7L, 70L, false));
    RoleView result = service.create(new CreateRoleCommand("AUDITOR", "审计员", DataScope.SELF));
    assertThat(result.tenantId()).isEqualTo(7L);
    verify(gateway).insert(argThat(role -> role.roleType() == RoleType.CUSTOM));
}
```

- [ ] **步骤 2：运行应用测试确认失败**

运行：`mvn -Dtest=RoleApplicationServiceTest test`

预期：FAIL，服务和网关尚不存在。

- [ ] **步骤 3：定义网关和实现服务**

`RoleGateway` 至少提供：

```java
Optional<Role> findById(long tenantId, long roleId);
boolean existsByCode(long tenantId, String roleCode);
PageResponse<Role> page(long tenantId, String keyword, RoleStatus status, RoleType type, long pageNo, long pageSize);
void insert(Role role);
boolean update(Role role, long expectedVersion);
void deleteRelationsAndSoftDelete(long tenantId, long roleId, long operatorId);
```

应用服务写方法加 `@Transactional`，更新返回 `false` 时抛 `IAM_OPTIMISTIC_LOCK_CONFLICT`；创建用项目现有 ID 生成方式，不引入新 ID 框架。

- [ ] **步骤 4：编写 Mapper 集成测试和 XML**

集成测试插入两个租户同编码角色，验证分页只返回当前租户；删除后详情为空；旧版本更新返回 0。XML 的所有复杂 SQL 显式包含 `tenant_id = #{tenantId}` 和 `deleted = 0`。

- [ ] **步骤 5：运行测试并提交**

运行：`mvn -Dtest=RoleApplicationServiceTest,RolePersistenceAdapterIntegrationTest test`

预期：PASS。

```bash
git add src/main/java/com/saasbase/iam src/main/resources/mapper/iam/RoleMapper.xml src/test/java/com/saasbase/iam
git commit -m "实现角色生命周期"
```

## 任务 4：权限目录与角色授权

**文件：**
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/PermissionGateway.java`
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/RolePermissionGateway.java`
- 创建：`src/main/java/com/saasbase/iam/application/RoleAuthorizationService.java`
- 创建：`src/main/java/com/saasbase/iam/application/dto/PermissionView.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/PermissionMapper.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/PermissionPersistenceAdapter.java`
- 创建：`src/main/resources/mapper/iam/PermissionMapper.xml`
- 测试：`src/test/java/com/saasbase/iam/application/RoleAuthorizationServiceTest.java`
- 测试：`src/test/java/com/saasbase/iam/infrastructure/persistence/PermissionPersistenceAdapterIntegrationTest.java`

- [ ] **步骤 1：编写失败的全量替换测试**

```java
@Test
void replacesPermissionsAndInvalidatesEveryAffectedUser() {
    when(roleGateway.findById(7L, 71L)).thenReturn(Optional.of(activeRole()));
    when(permissionGateway.findExistingIds(Set.of(1L, 2L))).thenReturn(Set.of(1L, 2L));
    when(rolePermissionGateway.findUserIdsByRole(7L, 71L)).thenReturn(Set.of(10L, 11L));
    service.replacePermissions(71L, Set.of(1L, 2L));
    verify(rolePermissionGateway).replace(7L, 71L, Set.of(1L, 2L));
    verify(sessionVersionGateway).incrementAll(7L, Set.of(10L, 11L));
}
```

另测重复 ID 返回 `400`、不存在权限返回 `404`、空集合允许清空、跨租户角色返回 `404`。

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -Dtest=RoleAuthorizationServiceTest test`

预期：FAIL，授权服务尚不存在。

- [ ] **步骤 3：实现事务编排与持久化**

`replacePermissions` 必须按“校验角色 → 校验权限全集 → 查询受影响用户 → 删除旧关联并批量插入 → 递增用户版本 → 写 `GRANT` 审计”的顺序在同一事务执行。权限目录按 `permission_type, permission_code` 稳定排序。

- [ ] **步骤 4：运行测试并提交**

运行：`mvn -Dtest=RoleAuthorizationServiceTest,PermissionPersistenceAdapterIntegrationTest test`

预期：PASS。

```bash
git add src/main/java/com/saasbase/iam src/main/resources/mapper/iam/PermissionMapper.xml src/test/java/com/saasbase/iam
git commit -m "实现角色权限授权"
```

## 任务 5：用户角色分配与最后管理员并发保护

**文件：**
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/IamUserGateway.java`
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/UserRoleGateway.java`
- 创建：`src/main/java/com/saasbase/iam/domain/gateway/UserSessionVersionGateway.java`
- 创建：`src/main/java/com/saasbase/iam/application/UserRoleAssignmentService.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/UserRoleMapper.java`
- 创建：`src/main/java/com/saasbase/iam/infrastructure/persistence/UserRolePersistenceAdapter.java`
- 创建：`src/main/resources/mapper/iam/UserRoleMapper.xml`
- 测试：`src/test/java/com/saasbase/iam/application/UserRoleAssignmentServiceTest.java`
- 测试：`src/test/java/com/saasbase/iam/infrastructure/persistence/UserRoleConcurrencyIntegrationTest.java`

- [ ] **步骤 1：编写失败的分配规则测试**

覆盖有效用户多角色替换、无效角色拒绝、跨租户拒绝、替换后用户版本递增，以及最后管理员不能移除 `TENANT_ADMIN`。

```java
@Test
void rejectsRemovingLastTenantAdmin() {
    when(userGateway.isActive(7L, 10L)).thenReturn(true);
    when(userRoleGateway.lockAndCountActiveTenantAdmins(7L)).thenReturn(1L);
    when(userRoleGateway.hasRoleCode(7L, 10L, "TENANT_ADMIN")).thenReturn(true);
    assertThatThrownBy(() -> service.replaceRoles(10L, Set.of(99L)))
            .isInstanceOfSatisfying(BizException.class,
                    ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.IAM_LAST_TENANT_ADMIN));
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -Dtest=UserRoleAssignmentServiceTest test`

预期：FAIL，服务尚不存在。

- [ ] **步骤 3：实现加锁和全量替换**

`UserRoleMapper.xml` 使用当前租户的 `TENANT_ADMIN` 关联查询并加 `FOR UPDATE`；锁定后重新统计有效管理员。全量替换删除和插入都显式带 `tenant_id`。事务内替换成功后执行：

```sql
UPDATE iam_user
   SET session_version = session_version + 1,
       updated_at = CURRENT_TIMESTAMP(6)
 WHERE tenant_id = #{tenantId}
   AND id = #{userId}
   AND deleted = 0;
```

- [ ] **步骤 4：编写双事务并发测试**

创建两个有效管理员，两个线程分别尝试移除自己的 `TENANT_ADMIN`。使用 `CountDownLatch` 同时启动，断言最多一个事务成功，提交后数据库仍至少有一名有效管理员。

- [ ] **步骤 5：运行测试并提交**

运行：`mvn -Dtest=UserRoleAssignmentServiceTest,UserRoleConcurrencyIntegrationTest test`

预期：PASS。

```bash
git add src/main/java/com/saasbase/iam src/main/resources/mapper/iam/UserRoleMapper.xml src/test/java/com/saasbase/iam
git commit -m "实现用户角色分配保护"
```

## 任务 6：认证授权快照与用户级会话失效

**文件：**
- 修改：`src/main/java/com/saasbase/auth/domain/UserCredential.java`
- 修改：`src/main/java/com/saasbase/auth/domain/UserPrincipal.java`
- 修改：`src/main/java/com/saasbase/auth/application/AuthApplicationService.java`
- 创建：`src/main/java/com/saasbase/auth/domain/gateway/UserSessionVersionGateway.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialRecord.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialPersistenceAdapter.java`
- 修改：`src/main/resources/mapper/auth/UserCredentialMapper.xml`
- 创建：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserSessionVersionPersistenceAdapter.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/security/JwtTokenGateway.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilter.java`
- 测试：认证 application、Mapper、JWT 和 Filter 现有测试文件

- [ ] **步骤 1：扩展失败的认证聚合测试**

在 `UserCredentialMapperIntegrationTest` 增加：停用角色不贡献权限；多个角色取最宽数据范围；无部门用户的部门范围降级 `SELF`；返回 `session_version`。

```java
assertThat(credential)
        .extracting(UserCredential::dataScope, UserCredential::sessionVersion)
        .containsExactly(DataScope.DEPT_AND_CHILDREN, 3L);
```

- [ ] **步骤 2：扩展失败的 Token 和 Filter 测试**

JWT 往返断言 `dataScope`、`sessionVersion` 不丢失。Filter 测试中令网关返回当前版本 `4`、Token 版本 `3`，断言响应 `401` 且不进入业务链。

- [ ] **步骤 3：运行认证测试确认失败**

运行：`mvn -Dtest=UserCredentialMapperIntegrationTest,JwtTokenGatewayTest,JwtAuthenticationFilterTest,AuthApplicationServiceTest test`

预期：FAIL，新字段和版本网关尚不存在。

- [ ] **步骤 4：实现认证聚合和版本校验**

Mapper 只聚合 `r.status = 'ACTIVE' AND r.deleted = 0` 的角色；数据范围用明确 `CASE` rank 聚合后由 Adapter 转为 `DataScope`。登录和 Refresh Store 写入完整授权快照。Filter 解析 Token 后、设置 SecurityContext 前调用：

```java
if (!userSessionVersionGateway.matches(
        principal.tenantId(), principal.userId(), principal.sessionVersion())) {
    throw new IllegalArgumentException("User session version expired");
}
```

- [ ] **步骤 5：运行认证测试并提交**

运行：`mvn -Dtest=UserCredentialMapperIntegrationTest,JwtTokenGatewayTest,JwtAuthenticationFilterTest,AuthApplicationServiceTest test`

预期：PASS。

```bash
git add src/main/java/com/saasbase/auth src/main/resources/mapper/auth/UserCredentialMapper.xml src/test/java/com/saasbase/auth
git commit -m "支持授权快照立即失效"
```

## 任务 7：IAM REST API 与接口权限

**文件：**
- 创建：`src/main/java/com/saasbase/iam/adapter/RoleController.java`
- 创建：`src/main/java/com/saasbase/iam/adapter/PermissionController.java`
- 创建：`src/main/java/com/saasbase/iam/adapter/RoleAuthorizationController.java`
- 创建：`src/main/java/com/saasbase/iam/adapter/UserRoleController.java`
- 创建：`src/main/java/com/saasbase/iam/adapter/request/` 下请求对象
- 修改：`src/main/java/com/saasbase/system/infrastructure/openapi/OpenApiConfig.java`
- 测试：`src/test/java/com/saasbase/iam/adapter/IamControllerTest.java`

- [ ] **步骤 1：编写失败的 MockMvc 权限测试**

逐接口覆盖规格中的 10 条路由，并至少验证：无 Token 返回 `401`、缺权限返回 `403`、有对应权限调用服务、请求体不含 `tenantId`、状态变更接收明确 `ACTIVE`/`DISABLED`。

```java
mockMvc.perform(put("/api/v1/iam/roles/71/permissions")
        .with(user(principal).authorities(new SimpleGrantedAuthority("iam:role:authorize")))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"permissionIds\":[1,2]}"))
        .andExpect(status().isNoContent());
verify(roleAuthorizationService).replacePermissions(71L, Set.of(1L, 2L));
```

- [ ] **步骤 2：运行 Controller 测试确认失败**

运行：`mvn -Dtest=IamControllerTest test`

预期：FAIL，路由不存在。

- [ ] **步骤 3：实现 Controller**

使用 `@PreAuthorize("hasAuthority('iam:...')")` 逐项保护接口；请求使用 Bean Validation。创建返回 `201`，更新、状态、授权、分配和删除成功返回 `204`，查询使用 `ApiResponse.ok(...)`。

- [ ] **步骤 4：补充 OpenAPI 分组并运行测试**

新增匹配 `/api/v1/iam/**` 的 `GroupedOpenApi` Bean。

运行：`mvn -Dtest=IamControllerTest,ApiPathPartitionTest test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/java/com/saasbase/iam/adapter src/main/java/com/saasbase/system/infrastructure/openapi/OpenApiConfig.java src/test/java/com/saasbase/iam/adapter/IamControllerTest.java
git commit -m "提供角色权限管理接口"
```

## 任务 8：审计、架构和完整回归

**文件：**
- 修改：`src/main/java/com/saasbase/iam/application/RoleApplicationService.java`
- 修改：`src/main/java/com/saasbase/iam/application/RoleAuthorizationService.java`
- 修改：`src/main/java/com/saasbase/iam/application/UserRoleAssignmentService.java`
- 修改：`src/test/java/com/saasbase/architecture/ColaArchitectureTest.java`
- 创建：`src/test/java/com/saasbase/iam/application/IamAuditTest.java`
- 修改：`docs/postman/SaaSBase.postman_collection.json`

- [ ] **步骤 1：编写失败的审计测试**

分别断言角色创建/更新/启停/删除、角色授权和用户角色分配调用 `AuditGateway.appendAdminOperationAudit`，事件包含当前 `tenantId`、操作者 ID、资源类型和资源 ID，不包含密码、Token 或完整权限对象。

- [ ] **步骤 2：运行审计和架构测试确认失败**

运行：`mvn -Dtest=IamAuditTest,ColaArchitectureTest test`

预期：审计断言 FAIL；架构测试应保持 PASS，若失败必须调整依赖方向，不得放宽规则。

- [ ] **步骤 3：补齐审计和 Postman 请求**

在各事务成功路径写 `AdminOperationAuditEvent`。Postman 增加 `IAM / Roles`、`IAM / Permissions`、`IAM / User Roles` 文件夹，复用 `baseUrl` 和 Bearer Token 变量，不写入真实凭证。

- [ ] **步骤 4：运行模块回归**

运行：

```bash
mvn -Dtest='com.saasbase.iam.**,com.saasbase.auth.**,FlywayMigrationTest,ColaArchitectureTest,ApiPathPartitionTest' test
```

预期：全部 PASS，且无失败、错误或跳过的 IAM 核心测试。

- [ ] **步骤 5：运行完整验证**

运行：`mvn clean test`

预期：`BUILD SUCCESS`。

- [ ] **步骤 6：检查迁移与工作区并提交**

运行：`git diff --check && git status --short`

预期：无空白错误；只出现本任务预期文件和用户原有未提交变更。

提交前向用户展示将提交的文件和变更摘要，然后执行：

```bash
git add src/main/java/com/saasbase/iam/application src/test/java/com/saasbase/architecture/ColaArchitectureTest.java src/test/java/com/saasbase/iam/application/IamAuditTest.java docs/postman/SaaSBase.postman_collection.json
git commit -m "完善角色权限审计与回归"
```

## 规格覆盖检查

- 角色 CRUD、状态、分页和详情：任务 2、3、7。
- 平台内置权限目录与角色授权：任务 1、4、7。
- 用户多角色分配与最后管理员保护：任务 2、5、7。
- 四种数据范围和合并：任务 1、6。
- 自定义部门范围明确不实现：任务 1 只定义四种枚举，无自定义部门关联表。
- 内置 `TENANT_ADMIN` 可改名称/授权/范围但不可改编码、停用、删除：任务 2、3。
- 授权变化后精准失效相关用户会话：任务 4、5、6。
- 租户隔离、跨租户隐藏存在性：任务 3、4、5、7。
- 乐观锁、事务与并发保护：任务 3、4、5。
- 明确错误语义和逐接口权限：任务 2、7。
- 管理审计、架构约束和完整回归：任务 8。
