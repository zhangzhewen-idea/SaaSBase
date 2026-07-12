# 租户管理闭环设计

日期：2026-07-13
状态：设计已确认

## 1. 目标与范围

本规格实现 SaaSBase 第一阶段的租户管理闭环。

平台管理员负责租户创建、编辑、启用、停用、分页查询和详情查询。租户管理员只能查看当前租户资料，不能修改租户状态或跨租户访问。第一阶段不提供租户删除，停用后保留用户、角色、权限、业务数据和审计记录。

创建租户时，同一数据库事务必须完成租户、首个管理员、租户独立 `TENANT_ADMIN` 角色、内置权限绑定和管理员角色绑定。`tenantCode` 创建后不可修改。

本规格只引入租户初始化所需的最小 IAM 能力，不提前实现完整用户、组织、角色和权限管理 API。

## 2. 架构与组件边界

继续采用模块化单体和 COLA light 分层：

```text
PlatformTenantController / AdminTenantProfileController
                    ↓
           TenantApplicationService
                    ↓
 TenantGateway / TenantAdminInitializer / TenantSessionGateway
          ↓                ↓                 ↓
 MyBatis Tenant     IAM 最小网关组合       Redis 认证状态缓存
 Persistence
```

组件职责：

- `PlatformTenantController`：提供平台侧创建、编辑、启用、停用、分页查询和详情查询接口，并校验平台租户管理权限。
- `AdminTenantProfileController`：供租户管理员查看当前租户资料；租户 ID 只从认证上下文获取。
- `TenantApplicationService`：定义事务边界，编排租户初始化、状态变更、会话失效和审计，不直接依赖 Mapper 或 Redis Client。
- `Tenant`：维护租户状态、租户编码不可变、名称有效性和状态转换规则。
- `TenantGateway`：提供租户持久化、唯一性检查、分页查询、状态查询和乐观锁更新。
- `TenantAdminInitializer`：封装首个管理员、租户独立 `TENANT_ADMIN` 角色、内置权限复制及绑定。
- `TenantSessionGateway`：读取和更新租户认证状态缓存。

`application` 和 `domain` 只依赖网关接口，MyBatis、Redis 等技术实现位于 `infrastructure`。

## 3. 数据模型

现有 `tenant` 表增加：

```text
session_version BIGINT NOT NULL DEFAULT 0
```

关键字段语义：

- `tenant_code`：全局唯一，创建后不可修改。
- `tenant_name`：平台管理员可以修改。
- `status`：只允许 `ACTIVE`、`DISABLED`。
- `session_version`：租户认证会话版本，停用时递增，启用时保持不变。
- `deleted`：继续保留，但第一阶段不提供删除操作。
- `version`：用于乐观锁并发控制。

MySQL 中的 `tenant.status` 和 `session_version` 是权威值。Redis 只缓存租户认证状态，JWT 和 Refresh Token 只保存签发时的版本快照。

## 4. 认证状态缓存与失效

租户认证状态缓存建议使用键：

```text
tenant:auth-state:{tenantId}
```

缓存值至少包含 `status` 和 `sessionVersion`，TTL 固定为 5 秒：

- 请求校验优先读取 Redis。
- Redis 未命中或异常时回源 MySQL，并尝试重建缓存。
- Redis 和 MySQL 都不可用时 fail-closed，拒绝受保护请求。
- 启用或停用事务提交后立即覆盖 Redis，不增加 JVM 本地二级缓存。
- 缓存异常情况下允许存在最长约 5 秒的一致性窗口；正常更新成功时立即生效。

Access Token 和 Refresh Token 都携带签发时的 `sessionVersion`。认证时必须同时满足租户为 `ACTIVE`，并且 Token 版本等于当前租户版本。

停用租户时将状态改为 `DISABLED` 并递增 `sessionVersion`。重新启用时不降低版本，因此停用前签发的 Token 永远不会恢复有效，用户必须重新登录。

## 5. API 契约

平台 API：

```text
POST   /api/v1/platform/tenants
GET    /api/v1/platform/tenants
GET    /api/v1/platform/tenants/{tenantId}
PUT    /api/v1/platform/tenants/{tenantId}
POST   /api/v1/platform/tenants/{tenantId}/enable
POST   /api/v1/platform/tenants/{tenantId}/disable
```

租户侧 API：

```text
GET    /api/v1/admin/tenant/profile
```

创建请求包含：

```text
tenantCode
tenantName
adminUsername
adminDisplayName
initialPassword
```

创建响应不得返回初始密码或密码哈希。编辑请求只允许修改 `tenantName`，不得接受 `tenantCode` 或状态字段。

平台分页查询支持：

- `tenantCode` 精确匹配。
- `tenantName` 模糊匹配。
- `status` 精确匹配。
- `page`、`size` 分页参数。
- 固定按 `createdAt DESC, id DESC` 排序，第一阶段不开放任意排序字段。

## 6. 权限模型

新增权限点：

```text
platform:tenant:create
platform:tenant:read
platform:tenant:update
platform:tenant:enable
platform:tenant:disable
tenant:profile:read
```

平台接口必须同时满足 JWT 有效、具备对应 `platform:tenant:*` 权限和处于平台管理上下文。平台绕过只能用于明确的平台用例，不能扩散到普通租户接口。

租户资料接口必须满足 JWT 有效、租户为 `ACTIVE`、会话版本一致并具备 `tenant:profile:read` 权限。租户 ID 只取自认证上下文，不接受客户端指定。

每个租户拥有独立的 `TENANT_ADMIN` 角色。创建租户时从第一阶段内置权限模板复制权限绑定，该角色后续可以调整，但完整的角色维护规则由角色权限规格定义。

## 7. 核心业务流程

### 7.1 创建租户

```text
校验平台权限
→ 校验 tenantCode 全局唯一
→ 校验管理员用户名有效
→ 创建 ACTIVE 租户，sessionVersion = 0
→ 创建首个管理员并哈希密码
→ 创建 TENANT_ADMIN 角色
→ 绑定第一阶段内置权限
→ 绑定管理员与角色
→ 写入 CREATE / TENANT 管理审计
→ 提交数据库事务
→ 初始化 Redis 租户认证状态缓存
```

租户、管理员、角色、权限绑定和审计写入必须处于同一数据库事务，任一步失败则全部回滚。Redis 初始化失败不回滚已提交事务，首次认证检查时通过 MySQL 回源重建缓存。

### 7.2 停用租户

```text
读取租户
→ 通过乐观锁将 ACTIVE 改为 DISABLED
→ sessionVersion + 1
→ 写入 DISABLE / TENANT 管理审计
→ 提交数据库事务
→ 覆盖 Redis 认证状态缓存
```

提交后的 Redis 更新失败时记录结构化错误和指标，由 5 秒 TTL 与 MySQL 回源兜底。第一阶段不引入 Outbox、消息队列或分布式事务。

### 7.3 启用租户

将 `DISABLED` 改为 `ACTIVE`，保持 `sessionVersion` 不变，写入管理审计并在事务提交后覆盖 Redis 缓存。停用前 Token 不会恢复，用户必须重新登录。

### 7.4 编辑和查询

编辑只能修改 `tenantName`，并通过 `version` 乐观锁避免并发覆盖。平台查询可以跨租户，但必须显式进入平台用例；租户管理员只能读取认证上下文对应的租户资料。

## 8. 并发与幂等

- 租户编辑和启停使用 `version` 乐观锁。
- 已是 `ACTIVE` 时再次启用、已是 `DISABLED` 时再次停用，返回状态冲突。
- `tenantCode` 冲突由应用层预检和数据库唯一约束共同保证。
- 创建接口第一阶段不引入幂等键；客户端超时后应按 `tenantCode` 查询创建结果，不能通过更换编码盲目重试。
- 缓存更新必须发生在数据库事务提交之后，不能让未提交状态对外可见。

## 9. 错误处理

错误码及 HTTP 状态映射：

```text
TENANT_NOT_FOUND                 → 404
TENANT_CODE_CONFLICT             → 409
TENANT_STATUS_CONFLICT           → 409
TENANT_CONCURRENT_MODIFICATION   → 409
TENANT_DISABLED                  → 403
IAM_USERNAME_CONFLICT            → 409
IAM_PERMISSION_TEMPLATE_MISSING  → 500
TENANT_INITIALIZATION_FAILED     → 500
AUTH_TENANT_SESSION_EXPIRED      → 401
```

Mapper 和 Redis 适配器不得吞异常。数据库事务内的初始化失败统一回滚，但日志必须保留可定位的原始异常。缓存更新失败不能伪装成数据库事务失败。

## 10. 安全与审计

- 初始密码必须满足统一密码策略，并使用现有 BCrypt 强度哈希。
- 初始密码、密码哈希和 Token 不得进入响应、请求日志、异常日志或审计详情。
- 平台租户列表和详情不返回管理员凭证信息。
- 停用不删除用户、角色、权限、业务数据或历史审计。
- 租户不存在、已停用或会话版本过期时均 fail-closed。
- 所有租户写操作记录管理操作审计。
- 审计至少记录操作者、目标租户、操作类型、结果和 `traceId`，不记录完整请求体。

## 11. 测试方案

### 11.1 领域测试

- `tenantCode` 创建后不可修改。
- 合法状态转换为 `ACTIVE → DISABLED`、`DISABLED → ACTIVE`。
- 重复启用、重复停用被拒绝。
- 停用时 `sessionVersion + 1`，启用时版本不变。
- 空名称和非法状态被拒绝。

### 11.2 应用层测试

- 创建租户正确编排租户、管理员、角色、权限和审计。
- 任一数据库步骤失败时整体回滚。
- 编辑租户不能修改 `tenantCode`。
- 租户管理员只能查询当前租户。
- 启停成功后更新认证状态缓存。
- Redis 更新失败不篡改已提交的数据库结果。

### 11.3 Adapter 测试

- 平台接口逐项验证权限。
- 普通租户身份不能访问平台接口。
- 参数校验、分页结构和 HTTP 状态码正确。
- 密码和密码哈希不出现在响应中。
- 租户资料接口不接受客户端伪造租户标识。

### 11.4 基础设施集成测试

- `tenantCode` 唯一约束生效。
- MyBatis 租户查询和平台绕过边界正确。
- 乐观锁冲突不会覆盖新数据。
- 创建租户的全部初始化数据正确落库。
- 管理审计与业务数据处于同一事务。
- Redis TTL 为 5 秒，缓存未命中能回源 MySQL。

### 11.5 认证回归测试

- 租户停用后，新请求因状态或版本不匹配被拒绝。
- 停用前 Access Token 和 Refresh Token 均失效。
- 重新启用后旧 Token 不恢复。
- 重新登录签发当前版本 Token 后可以访问。
- Redis 不可用时回源 MySQL；Redis 和 MySQL 均不可用时拒绝请求。

## 12. 验收标准

- 七个租户 API 均可用并出现在 OpenAPI 文档中。
- 创建租户能在同一数据库事务中生成首个管理员和租户独立 `TENANT_ADMIN` 角色。
- `tenantCode` 创建后不可修改，租户不提供删除能力。
- 平台与租户访问边界无法绕过。
- 停用在正常缓存更新时立即生效，异常情况下最迟在约 5 秒缓存窗口内生效。
- 停用前签发的 Access Token 和 Refresh Token 不会因重新启用而恢复。
- 新增测试与现有回归测试全部通过。
- 本规格不提前实现完整用户、组织、角色和权限管理 API。

## 13. 非目标

- 租户删除和数据清理。
- 完整用户、组织、角色、权限和数据范围管理。
- 创建接口幂等键。
- Outbox、消息队列或分布式事务。
- JVM 本地二级缓存。
- 套餐、额度、订阅、计费和支付。
