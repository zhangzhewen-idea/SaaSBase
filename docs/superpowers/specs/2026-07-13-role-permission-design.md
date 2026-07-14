# 角色权限模块设计

## 1. 目标与范围

本模块为租户提供完整的角色权限管理后端能力，包括角色生命周期管理、平台内置权限目录查询、角色功能授权、用户角色分配和角色数据范围管理。

本次实现范围：

- 角色创建、编辑、启用、停用、软删除、分页查询和详情查询。
- 查询平台内置权限目录。
- 全量替换角色的功能权限。
- 查询并全量替换用户角色。
- 数据范围支持 `ALL`、`DEPT_ONLY`、`DEPT_AND_CHILDREN` 和 `SELF`。
- 授权变化后立即使受影响用户的现有会话失效。
- 防止租户失去最后一名有效 `TENANT_ADMIN` 用户。

本次不实现：

- 用户和部门 CRUD。
- 自定义部门数据范围。
- 租户自定义权限点。
- ABAC 或通用策略引擎。
- 通用 SQL 数据权限改写器。
- 管理端前端页面。

## 2. 现状与设计原则

项目已采用 Spring Boot 模块化单体和 COLA light 分层，数据库已有 `iam_role`、`iam_permission`、`iam_user_role` 和 `iam_role_permission` 表。认证链路会把用户有效权限聚合到 JWT，但尚无完整 IAM 管理 API。

角色权限能力新增为独立 `iam` 能力域。`auth` 只负责认证以及消费最终有效权限，不承载角色管理规则。权限点属于平台全局目录，由数据库迁移随版本发布；租户只能查询并授权。

所有租户侧操作只从认证上下文取得 `tenantId`。客户端不得提交或切换目标租户。平台权限目录可以作为全局数据读取，但角色、用户和关联关系必须严格隔离在当前租户内。

平台演示账号 `platform-admin` 使用显式的 `SUPER_ADMIN` 兜底标记，不依赖 `tenant:*` 这类通配符权限。普通账号仍按精确权限码判断，例如 `tenant:user:read`、`tenant:file:delete`。`SUPER_ADMIN` 只用于平台演示或全局管理员场景，不作为普通租户角色的授权方式。

## 3. 架构与组件

### 3.1 Adapter 层

- `RoleController`：角色分页、详情、创建、编辑、状态变更和删除。
- `PermissionController`：平台内置权限目录查询。
- `RoleAuthorizationController`：查询和全量替换角色权限。
- `UserRoleController`：查询和全量替换用户角色。

### 3.2 Application 层

- `RoleApplicationService`：编排角色生命周期与查询用例。
- `RoleAuthorizationService`：编排角色功能授权，计算受影响用户并使其会话失效。
- `UserRoleAssignmentService`：编排用户角色替换，执行最后管理员保护并使用户会话失效。
- 查询 DTO 与命令 DTO 分离，写命令不接受 `tenantId`。

### 3.3 Domain 层

- `Role` 聚合：维护角色编码、名称、类型、状态、数据范围和版本。
- `RoleType`：`BUILT_IN`、`CUSTOM`。
- `RoleStatus`：`ACTIVE`、`DISABLED`。
- `DataScope`：`ALL`、`DEPT_ONLY`、`DEPT_AND_CHILDREN`、`SELF`。
- `EffectiveAuthorization`：表达多角色合并后的权限集合和单一有效数据范围。
- 领域策略：内置角色保护、最后管理员保护、角色可授权性及数据范围合并。

### 3.4 Gateway 与 Infrastructure 层

领域网关按职责拆分：

- `RoleGateway`
- `PermissionGateway`
- `RolePermissionGateway`
- `UserRoleGateway`
- `IamUserGateway`
- `UserSessionVersionGateway`
- `AuditGateway`（复用现有审计能力）

MyBatis Mapper 和 XML 实现持久化。租户业务表继续由 MyBatis-Plus 租户拦截器添加租户条件；复杂聚合和加锁查询使用 XML 显式 SQL。

## 4. 数据模型与约束

### 4.1 `iam_role`

在现有结构上增加：

- `role_type VARCHAR(32) NOT NULL`：`BUILT_IN` 或 `CUSTOM`。
- `data_scope VARCHAR(32) NOT NULL`：四种数据范围之一。
- `version BIGINT NOT NULL DEFAULT 0`：乐观锁版本。

保留现有 `status` 和 `deleted` 字段。`role_code` 在租户内唯一，创建后不可修改。

`TENANT_ADMIN` 固定为 `BUILT_IN`。它允许修改名称、功能权限和数据范围，但禁止修改编码、停用和删除。普通角色允许停用和软删除。

### 4.2 `iam_user`

增加：

- `session_version BIGINT NOT NULL DEFAULT 0`：用户级会话版本。

用户级版本用于精确失效受授权变化影响的用户会话，不复用租户级会话版本，避免无关用户被迫重新登录。

### 4.3 关联关系

`iam_role_permission` 和 `iam_user_role` 保持租户维度联合主键。写入前必须校验所有关联对象存在、有效且属于当前租户；权限点必须来自有效的全局权限目录。

删除普通角色时，在同一事务内解除用户角色和角色权限关联、软删除角色并写入审计。已停用或已删除角色不参与登录权限聚合。

## 5. 领域规则

### 5.1 功能权限

一个用户可以拥有多个角色，有效功能权限为所有有效角色权限编码的并集。只有 `ACTIVE` 且未删除的角色、有效权限点和有效用户参与计算。

### 5.2 数据范围

多角色数据范围按以下顺序合并，取最宽范围：

```text
ALL > DEPT_AND_CHILDREN > DEPT_ONLY > SELF
```

`DEPT_ONLY` 和 `DEPT_AND_CHILDREN` 依赖用户当前 `dept_id`。用户未绑定部门时，有效数据范围降级为 `SELF`。

本模块只计算并传递有效数据范围。后续业务查询通过独立数据范围组件显式生成过滤条件，不能把数据权限与现有租户隔离拦截器混为一层。

### 5.3 最后管理员保护

任何角色分配、用户状态或相关关联操作都不得导致当前租户不存在“有效用户 + `ACTIVE` 且未删除的 `TENANT_ADMIN` 角色”的组合。

替换用户角色时，如果目标用户是最后一名有效租户管理员，则请求不得移除其 `TENANT_ADMIN`。校验必须在事务中锁定相关用户角色记录，防止并发请求同时通过检查。

本模块负责用户角色替换场景的保护。未来用户停用或删除功能必须复用同一领域策略。

## 6. API 设计

```text
GET    /api/v1/iam/roles
POST   /api/v1/iam/roles
GET    /api/v1/iam/roles/{roleId}
PUT    /api/v1/iam/roles/{roleId}
PUT    /api/v1/iam/roles/{roleId}/status
DELETE /api/v1/iam/roles/{roleId}

GET    /api/v1/iam/permissions
PUT    /api/v1/iam/roles/{roleId}/permissions

GET    /api/v1/iam/users/{userId}/roles
PUT    /api/v1/iam/users/{userId}/roles
```

角色列表支持按名称或编码关键字、状态、类型分页筛选。角色详情返回基础信息、数据范围和已授权权限 ID。权限目录按类型和稳定顺序返回，并标识指定角色的选中状态时由角色授权查询用例组合结果。

角色权限和用户角色写接口均采用全量替换语义。请求中的 ID 集合必须去重；空集合表示清空，但最后管理员保护仍然生效。角色状态接口接收明确目标状态，不提供 toggle 语义。

## 7. 权限点与访问控制

新增平台内置权限点：

```text
iam:role:read
iam:role:create
iam:role:update
iam:role:delete
iam:role:authorize
iam:user-role:read
iam:user-role:assign
iam:permission:read
```

每个接口逐项校验对应权限，不使用笼统管理员判断。菜单和前端按钮权限只能改善体验，后端权限校验始终是安全边界。

`SUPER_ADMIN` 是方法级鉴权的显式兜底条件。对被标记为 `SUPER_ADMIN` 的认证主体，`hasAuthority(...)`、`hasAnyAuthority(...)` 等权限判断直接通过；未带该标记的主体仍严格按具体权限码匹配。系统不实现 `tenant:*` 的通配符权限语义，也不建议把通配符当作授权数据长期存储。

所有写操作记录管理审计，包括操作者、租户、资源类型、资源 ID、动作和不含敏感信息的变更摘要。

## 8. 事务与会话失效

写操作执行顺序：

```text
授权校验
→ 加载并校验当前租户资源
→ 执行业务规则
→ 更新角色或关联关系
→ 计算受影响用户
→ 递增受影响用户的 session_version
→ 写入管理审计
→ 提交事务
```

会话失效规则：

- 修改角色权限、数据范围、状态或删除角色：递增该角色关联的全部受影响用户版本。
- 全量替换用户角色：递增该用户版本。
- 仅修改角色名称：授权结果不变，不递增用户版本。

JWT 携带用户 `session_version`、权限集合和有效数据范围。登录、刷新和每次认证请求均校验用户会话版本。旧 Token 版本不匹配时返回 `401`，用户必须重新登录。

不使用逐 Token 黑名单实现批量授权失效，因为系统无法可靠枚举一个用户已签发的全部 Access Token。

角色更新使用 `version` 乐观锁。全量替换关联关系、会话版本更新和审计写入必须在同一数据库事务中完成，任一步失败则整体回滚。

## 9. 错误处理

- `400 Bad Request`：参数格式、状态、数据范围或 ID 集合不合法。
- `401 Unauthorized`：Token 无效、已撤销或用户会话版本过期。
- `403 Forbidden`：已认证但缺少对应 IAM 权限。
- `404 Not Found`：资源在当前租户内不存在、无效或已删除。
- `409 Conflict`：角色编码重复、乐观锁冲突、内置角色受保护或触发最后管理员保护。

跨租户资源不泄露存在性，统一按当前租户内资源不存在处理。

## 10. 测试策略

### 10.1 领域单元测试

- 内置角色允许和禁止的变更。
- 普通角色状态流转与删除规则。
- 多角色功能权限并集。
- 四种数据范围的优先级合并。
- 未绑定部门时降级为 `SELF`。
- 最后一名有效管理员保护。

### 10.2 应用服务测试

- 角色权限全量替换和空集合清空。
- 用户角色全量替换及最后管理员保护。
- 跨租户角色、用户和关联对象拒绝。
- 不同写操作对应的受影响用户计算。
- 用户会话版本递增和事务失败回滚。
- 写操作审计内容不包含敏感信息。

### 10.3 Mapper 集成测试

- 租户隔离和逻辑删除。
- 多角色权限聚合及稳定去重。
- 数据范围合并。
- 乐观锁冲突。
- 并发移除最后管理员时只有安全结果可以提交。

### 10.4 API 与迁移测试

- MockMvc 验证路由、参数校验、权限校验以及 `401/403/404/409` 映射。
- 验证 `tenantId` 只来自认证上下文。
- Flyway 验证新增字段、索引、内置权限种子和既有数据升级默认值。
- 回归认证、Token 刷新、租户管理和 MyBatis 租户拦截行为。

## 11. 验收标准

- 租户管理员可以完成角色生命周期、角色授权和用户角色分配。
- 权限点只能由平台版本迁移维护，租户只能查询和授权。
- 任意跨租户读取或写入均失败且不泄露资源存在性。
- 多角色功能权限和数据范围按定义规则稳定合并。
- 授权变化立即使受影响用户的旧会话失效，不影响无关用户。
- 内置角色不能被停用或删除，角色编码不能修改。
- 串行或并发操作都不能移除租户最后一名有效管理员。
- 所有关键写操作均可审计，事务失败不产生部分授权结果。
