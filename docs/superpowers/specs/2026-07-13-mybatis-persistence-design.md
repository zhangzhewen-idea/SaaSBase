# MyBatis 持久化层统一改造设计

## 1. 背景与目标

当前项目已经引入 MyBatis-Plus，并配置了租户拦截器，但 `UserCredentialMapper` 和 `AuditMapper` 仍通过 `JdbcTemplate` 执行 SQL。`JdbcTemplate` 不会经过 MyBatis-Plus 的租户拦截器，导致同一应用内存在两套持久化行为。

本次改造目标是统一持久化入口，移除这两个组件中的 `JdbcTemplate`，使用 MyBatis-Plus 处理基础能力、使用 MyBatis XML 管理复杂 SQL，同时保持现有领域接口和应用层调用方式不变。

本次范围只覆盖当前已经存在的认证查询和审计写入，不扩展完整 IAM 管理功能。

## 2. 设计原则

- 应用层和领域层只依赖现有 `UserCredentialGateway`、`AuditGateway`，不依赖 MyBatis 类型。
- 简单 CRUD 优先使用 MyBatis-Plus `BaseMapper`，避免重复编写通用 SQL。
- 多表关联、权限聚合、批量或条件复杂的 SQL 使用 XML，集中管理 SQL 和结果映射。
- 所有租户业务 SQL 统一经过现有 `MyBatisPlusTenantConfig` 和 `SaasTenantLineHandler`。
- 不通过新增数据库字段或改变接口返回结构解决持久化改造问题。

## 3. 目标结构

持久化层按职责拆分为实体、Mapper 和 XML：

```text
src/main/java/com/saasbase/
└── infrastructure/persistence/
    ├── entity/
    │   ├── TenantEntity
    │   ├── IamUserEntity
    │   ├── IamRoleEntity
    │   └── IamPermissionEntity
    └── mapper/
        ├── UserCredentialMapper
        ├── SecurityAuditMapper
        └── AdminOperationAuditMapper

src/main/resources/
└── mapper/
    ├── UserCredentialMapper.xml
    ├── SecurityAuditMapper.xml
    └── AdminOperationAuditMapper.xml
```

实体只承载数据库映射，不直接暴露给应用层。`UserCredentialMapper` 实现 `UserCredentialGateway`，将查询结果转换为领域对象；审计 Mapper 实现 `AuditGateway` 所需的写入能力。

## 4. 用户凭证查询

将当前登录查询从 `JdbcTemplate` 迁移到 `UserCredentialMapper.xml`，保留以下查询语义：

- 根据 `tenantCode` 和 `username` 查询用户。
- 只查询未逻辑删除的用户。
- 只允许 `tenant.status = 'ACTIVE'` 的租户登录。
- 返回用户 ID、租户 ID、用户名、密码哈希和权限编码集合。
- 保留角色、权限的多表关联和按权限编码排序的聚合行为。
- 查询结果由基础设施层转换为 `UserCredential`。

用户状态约定为 `ACTIVE`、`DISABLED`、`LOCKED`。实现时登录查询同时要求 `u.status = 'ACTIVE'`，避免已禁用或锁定用户继续登录。

该查询属于认证入口，必须明确写出租户关联条件；不能依赖当前请求已经存在的租户上下文，因为登录请求本身尚未建立认证上下文。

## 5. 审计写入

将当前 `AuditMapper` 拆分为安全审计和管理操作两个 MyBatis Mapper：

- `SecurityAuditMapper`：写入 `security_audit_log`。
- `AdminOperationAuditMapper`：写入 `admin_operation_audit_log`。

审计事件对象继续由领域层创建，基础设施层负责参数绑定和数据库写入。ID 生成策略保持当前行为，即由基础设施层生成非负随机长整型 ID。

安全事件字段约定如下：

- `event_type`：`LOGIN`、`LOGIN_FAILURE`、`LOGOUT`、`TOKEN_REFRESH`、`PASSWORD_CHANGE`、`PASSWORD_RESET`。
- `result`：`SUCCESS`、`FAILURE`。

管理审计字段约定如下：

- `operation_type`：`CREATE`、`UPDATE`、`DELETE`、`ENABLE`、`DISABLE`、`GRANT`、`REVOKE`。
- `resource_type`：`TENANT`、`USER`、`DEPT`、`ROLE`、`PERMISSION`、`SYSTEM_CONFIG`、`FILE_OBJECT`。

本次只迁移已有审计写入路径，不新增退出登录、刷新令牌或密码变更的业务审计调用。

## 6. 租户隔离与忽略表

MyBatis SQL 统一使用现有租户拦截器。需要遵守以下规则：

- `iam_user`、`iam_dept`、`iam_role`、`iam_user_role`、`iam_role_permission`、`file_object` 等租户业务表保留 `tenant_id` 条件能力。
- `tenant`、`iam_permission`、`security_audit_log`、`admin_operation_audit_log`、`system_config` 继续由 `SaasTenantLineHandler` 作为忽略表处理。
- 登录查询不能依赖租户拦截器自动注入条件，必须显式使用 `tenant_code` 和用户租户关联条件。
- 平台请求继续使用现有 `platformRequest` 逻辑进行跨租户访问控制。

如果 MyBatis-Plus 拦截器会对登录 SQL 或审计 SQL 产生不符合预期的改写，必须通过明确的表忽略或平台绕过规则解决，不能重新退回 `JdbcTemplate`。

## 7. 错误处理与事务

Mapper 层不吞异常，也不转换领域错误。数据库异常继续向上抛出，由现有应用层和全局异常处理机制决定对外响应。

认证查询无结果时继续返回 `Optional.empty()`，由 `AuthApplicationService` 转换为现有认证失败业务异常。审计写入失败不改变现有接口契约；是否阻断主业务由后续事务策略单独决定，本次不引入异步审计或独立事务。

## 8. 测试方案

需要补充或调整以下测试：

1. Mapper 集成测试：使用 Testcontainers MySQL 执行用户、角色、权限和租户数据查询，验证返回的 `UserCredential`。
2. 登录边界测试：租户非 `ACTIVE`、用户非 `ACTIVE`、用户已逻辑删除、用户名不存在时均查询不到凭证。
3. 权限聚合测试：多角色、多权限场景下权限集合正确且顺序稳定。
4. 审计 Mapper 测试：安全审计和管理审计写入后字段值正确。
5. 租户隔离测试：普通租户上下文只能访问当前租户数据，平台请求遵守现有绕过规则。
6. 回归测试：现有认证应用测试、租户拦截器测试、Flyway 迁移测试和架构测试继续通过。

## 9. 非目标与兼容性

- 本次不新增完整租户、用户、角色、权限管理 API。
- 本次不改变领域网关接口、认证接口或审计事件对象结构。
- 本次不把所有表一次性改造成完整 CRUD Mapper；只为当前改造路径引入必要实体和 Mapper。
- 本次不修改 Flyway 表结构或迁移文件。
- 改造完成后，认证和审计持久化不再依赖 `JdbcTemplate`。
