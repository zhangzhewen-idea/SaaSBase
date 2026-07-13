# 用户管理设计

日期：2026-07-13
状态：设计已确认

## 1. 目标与范围

本规格实现 SaaSBase 第一阶段的租户内用户管理闭环。

用户采用租户内独立账号模型，同一用户名可以存在于不同租户，每个用户只属于一个租户。具备对应用户管理权限的租户管理员负责创建、编辑、启用、停用、查询和重置密码。第一阶段不提供用户自助注册、平台管理员跨租户代管、用户删除或全局身份多租户成员关系。

创建用户时由管理员设置临时密码，用户首次登录后必须修改密码。用户只归属一个主部门，可绑定多个租户内已有角色。部门树、角色定义和权限定义分别由后续组织管理与 RBAC 规格负责。

## 2. 架构与组件边界

用户管理归属 `iam` 模块，继续采用模块化单体和 COLA light 分层：

```text
AdminUserController
        ↓
UserApplicationService
        ↓
IamUser / UserGateway
DepartmentReferenceGateway
UserRoleAssignmentGateway
UserSessionGateway
        ↓
MyBatis 持久化 / Redis 认证状态
```

组件职责：

- `AdminUserController`：提供当前租户内用户的创建、编辑、启用、停用、分页、详情和密码重置 API；租户 ID 只能来自认证上下文。
- `UserApplicationService`：定义事务边界，编排用户、主部门、角色绑定、会话失效和管理审计。
- `IamUser`：维护用户名不可变、资料变更、账号状态转换、首次改密标记和用户级 `sessionVersion`。
- `UserGateway`：负责用户持久化、租户内用户名唯一性检查、条件分页和乐观锁更新。
- `DepartmentReferenceGateway`：只校验主部门存在、属于当前租户且有效；部门树维护不进入本规格。
- `UserRoleAssignmentGateway`：绑定租户内已有角色；角色和权限定义由后续 RBAC 规格负责。
- `UserSessionGateway`：维护用户认证状态缓存，供登录、JWT 校验和 Refresh Token 校验使用。

`iam` 管理用户业务资料，`auth` 只负责凭证校验、Token 签发和认证链路。两者通过明确的 Gateway 或只读查询接口协作，认证模型不承载用户资料、部门和角色管理职责。

## 3. 数据模型

现有 `iam_user` 作为租户内用户主表，核心字段统一为：

```text
id
tenant_id
username
display_name
phone
password_hash
status
primary_department_id
must_change_password
session_version
last_login_at
created_at / created_by
updated_at / updated_by
deleted / deleted_at / deleted_by
version
```

字段规则：

- `username`：租户内唯一，创建后不可修改；唯一约束为 `(tenant_id, username)`。
- `phone`：作为用户资料和查询条件，不作为登录标识，也不要求租户内唯一。
- `status`：只允许 `ACTIVE`、`DISABLED`；新用户默认 `ACTIVE`。
- `primary_department_id`：必填，引用当前租户内有效部门。部门停用或删除时不得自动迁移用户，相关约束由组织管理规格定义。
- `must_change_password`：新用户固定为 `true`；首次成功修改密码后改为 `false`。
- `session_version`：初始为 `0`，停用和管理员重置密码时递增，使现有 Access Token 与 Refresh Token 全部失效。
- `version`：用于编辑、启停和重置密码的乐观锁控制。
- `deleted`：继续保留基础字段，但本规格不提供删除操作。

用户与角色通过关系表表达，一个用户可绑定多个当前租户内有效角色。角色绑定不得跨租户。用户必须至少绑定一个角色，但不会被隐式授予 `TENANT_ADMIN`。

## 4. API 与权限

租户管理侧 API：

```text
POST /api/v1/admin/users
GET  /api/v1/admin/users
GET  /api/v1/admin/users/{userId}
PUT  /api/v1/admin/users/{userId}
POST /api/v1/admin/users/{userId}/enable
POST /api/v1/admin/users/{userId}/disable
POST /api/v1/admin/users/{userId}/reset-password
```

权限点：

```text
tenant:user:create
tenant:user:read
tenant:user:update
tenant:user:enable
tenant:user:disable
tenant:user:reset-password
```

创建请求包含：

```text
username
displayName
phone
initialPassword
primaryDepartmentId
roleIds
```

编辑请求只允许包含：

```text
displayName
phone
primaryDepartmentId
roleIds
version
```

用户名、状态、密码和租户 ID 不得通过编辑接口修改。启停与重置密码使用独立命令接口。

列表查询支持：

- `username` 精确匹配。
- `displayName`、`phone` 模糊匹配。
- `primaryDepartmentId` 精确匹配。
- `status` 精确匹配。
- `page`、`size` 分页。
- 固定按 `createdAt DESC, id DESC` 排序，不开放任意排序。

所有接口只能操作认证上下文中的当前租户，不接受客户端传入 `tenantId`。响应不得返回 `passwordHash`、临时密码、Token 或内部安全状态缓存信息。

## 5. 核心业务流程

### 5.1 创建用户

```text
校验用户管理权限
→ 校验 username 租户内唯一
→ 校验主部门属于当前租户且有效
→ 校验 roleIds 均属于当前租户且有效
→ 校验初始密码策略并执行 BCrypt 哈希
→ 创建 ACTIVE 用户
→ 设置 mustChangePassword = true、sessionVersion = 0
→ 绑定角色
→ 写入 CREATE / USER 管理审计
→ 提交事务
→ 初始化用户认证状态缓存
```

用户、角色绑定和审计必须处于同一数据库事务。Redis 初始化失败不回滚数据库事务，认证时从 MySQL 回源并尝试重建缓存。

### 5.2 编辑用户

校验主部门和角色后，通过 `version` 乐观锁更新资料，并在同一事务中全量替换角色绑定、写入审计。纯资料或角色变更不使现有会话失效；权限变更的实时生效方式由后续 RBAC 规格统一确定。

### 5.3 停用用户

通过乐观锁将 `ACTIVE` 改为 `DISABLED`，递增 `sessionVersion` 并写入管理审计。事务提交后覆盖 Redis 状态。Access Token 与 Refresh Token 因状态或版本不匹配立即失效。

### 5.4 启用用户

通过乐观锁将 `DISABLED` 改为 `ACTIVE`，保持 `sessionVersion` 不变并写入管理审计。事务提交后覆盖 Redis 状态。停用前签发的 Token 不会恢复，用户必须重新登录。

### 5.5 管理员重置密码

校验新临时密码策略，更新密码哈希，设置 `mustChangePassword = true`，递增 `sessionVersion` 并写入管理审计。新密码不进入响应、普通日志或审计详情。事务提交后覆盖 Redis 状态。

## 6. 认证状态与安全约束

用户认证状态缓存使用：

```text
user:auth-state:{tenantId}:{userId}
```

缓存至少包含：

```text
status
sessionVersion
mustChangePassword
```

认证规则：

- Access Token 与 Refresh Token 均携带签发时的用户 `sessionVersion`。
- 每次受保护请求先校验租户状态，再校验用户为 `ACTIVE` 且 Token 版本等于当前用户版本。
- Redis 未命中或异常时回源 MySQL 并尝试重建缓存；Redis 与 MySQL 均不可用时 fail-closed。
- 用户状态缓存 TTL 与租户状态缓存保持一致，固定为 5 秒。
- 正常停用或重置密码成功后立即覆盖缓存；缓存更新异常时允许存在最长约 5 秒的一致性窗口。
- `mustChangePassword = true` 时，只允许访问修改密码、退出和必要的当前身份查询接口；其他受保护接口拒绝访问并返回明确错误。
- 管理员不能停用或重置自己的账号，避免误操作导致当前管理入口失效。
- 不允许停用租户内最后一个有效的 `TENANT_ADMIN` 用户；角色绑定变更也必须保护该约束。
- 初始密码和重置密码必须遵循统一密码策略，禁止写入响应、请求日志、异常日志或审计详情。
- 所有写操作记录操作者、目标用户、操作类型、结果和 `traceId`，不记录完整请求体或敏感字段。

角色权限变更是否即时使 Token 中的权限失效，由后续 RBAC 规格统一设计。本规格只保证用户停用和密码重置立即使用户会话失效。

## 7. 并发与一致性

- 用户编辑、启停和重置密码使用 `version` 乐观锁。
- 已是 `ACTIVE` 时再次启用、已是 `DISABLED` 时再次停用，返回状态冲突。
- 用户名冲突由应用层预检和数据库唯一约束共同保证。
- 角色全量替换必须与用户资料更新、管理审计处于同一数据库事务。
- 最后一个有效 `TENANT_ADMIN` 保护必须在事务内完成，并通过适当的行锁或等价并发控制避免并发绕过。
- 缓存更新必须发生在数据库事务提交之后，不能让未提交状态对外可见。
- 缓存更新失败不能篡改或伪装已提交的数据库结果。

## 8. 错误处理

错误码及 HTTP 状态映射：

```text
IAM_USER_NOT_FOUND                   → 404
IAM_USERNAME_CONFLICT                → 409
IAM_USER_STATUS_CONFLICT             → 409
IAM_USER_CONCURRENT_MODIFICATION     → 409
IAM_DEPARTMENT_NOT_FOUND             → 400
IAM_DEPARTMENT_DISABLED              → 409
IAM_ROLE_NOT_FOUND                   → 400
IAM_ROLE_DISABLED                    → 409
IAM_CROSS_TENANT_REFERENCE           → 403
IAM_LAST_TENANT_ADMIN_PROTECTED       → 409
IAM_SELF_OPERATION_FORBIDDEN         → 409
AUTH_USER_DISABLED                   → 403
AUTH_USER_SESSION_EXPIRED            → 401
AUTH_PASSWORD_CHANGE_REQUIRED        → 403
```

Mapper 和 Redis 适配器不得吞异常。数据库事务内的失败统一回滚，日志保留可定位的原始异常。事务提交后的缓存更新失败只记录结构化错误和指标，不伪装成数据库事务失败。

## 9. 测试方案

### 9.1 领域测试

- 用户名创建后不可修改。
- 合法状态转换为 `ACTIVE → DISABLED`、`DISABLED → ACTIVE`。
- 重复启用和重复停用被拒绝。
- 停用和管理员重置密码时 `sessionVersion + 1`，启用时版本不变。
- 新用户和管理员重置密码后必须修改密码，成功改密后清除标记。

### 9.2 应用层测试

- 创建用户正确编排用户、角色绑定和管理审计。
- 任一数据库步骤失败时整体回滚。
- 编辑用户不能修改用户名。
- 主部门和角色必须存在、有效且属于当前租户。
- 管理员不能停用或重置自己的账号。
- 不能停用或解除租户内最后一个有效 `TENANT_ADMIN` 用户的管理角色。
- Redis 更新失败不篡改已提交的数据库结果。

### 9.3 Adapter 测试

- 管理接口逐项验证权限。
- 租户 ID 只来自认证上下文，客户端不能伪造。
- 查询条件、固定排序和分页结构正确。
- 密码、密码哈希、Token 和内部安全状态不出现在响应中。

### 9.4 基础设施集成测试

- `(tenant_id, username)` 唯一约束生效。
- MyBatis 租户隔离不会返回其他租户用户。
- 乐观锁冲突不会覆盖新数据。
- 角色全量替换和用户资料更新处于同一事务。
- 管理审计与业务更新处于同一事务。
- 最后一个有效 `TENANT_ADMIN` 保护在并发操作下不能被绕过。
- Redis TTL 为 5 秒，缓存未命中能回源 MySQL。

### 9.5 认证与安全回归测试

- 用户停用后，Access Token 和 Refresh Token 均失效。
- 管理员重置密码后，已有 Access Token 和 Refresh Token 均失效。
- 重新启用后旧 Token 不恢复。
- 重新登录签发当前版本 Token 后可以访问。
- Redis 不可用时回源 MySQL；Redis 和 MySQL 均不可用时拒绝请求。
- 强制改密用户只能访问白名单接口。
- 临时密码不进入日志和审计。
- 跨租户部门、角色和用户引用全部被拒绝。

## 10. 明确排除项

以下内容不进入本规格：

- 用户自助注册、邀请和审批。
- 平台管理员跨租户代管用户。
- 全局身份与多租户成员关系。
- 用户删除和用户名回收复用。
- 多部门兼职关系。
- 手机号登录和手机号唯一约束。
- 部门树维护、角色定义、权限定义和数据范围维护。
- 角色权限变更后的 Token 实时失效策略。
- 邮件、短信、激活链接和找回密码流程。
