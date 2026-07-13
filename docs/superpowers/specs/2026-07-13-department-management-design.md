# 部门管理设计

日期：2026-07-13
状态：设计已确认

## 1. 目标与范围

本规格实现 SaaSBase 第一阶段的部门管理闭环。

本次只做组织管理底座，不做完整 HR 系统。每个租户仅有一个根部门，支持部门新增、编辑、移动、停用、删除，以及用户主部门归属、直属成员查询、后代成员查询和单用户调岗。部门树、用户主部门和数据范围共同支撑权限系统中的组织维度判断。

本规格不实现多部门兼职归属，不实现岗位体系，不实现用户生命周期管理，也不扩展租户外的跨组织成员关系。

## 2. 架构与组件边界

部门管理归属 `iam` 模块，继续采用模块化单体和 COLA light 分层：

```text
AdminDepartmentController
        ↓
DepartmentApplicationService
        ↓
DepartmentDomainService / DepartmentMemberDomainService
DepartmentGateway / DepartmentMemberGateway
        ↓
MyBatis 持久化
```

组件职责：

- `AdminDepartmentController`：提供当前租户内的部门树、详情、新增、编辑、移动、停用、删除、成员查询和调岗 API；租户 ID 只能来自认证上下文。
- `DepartmentApplicationService`：定义事务边界，编排部门变更、成员归属变更和审计，不直接依赖 Mapper。
- `DepartmentDomainService`：负责部门树规则，包括父子关系、层级深度、成环校验、启停和删除约束。
- `DepartmentMemberDomainService`：负责主部门归属一致性、成员查询范围和调岗前置校验。
- `DepartmentGateway`：负责部门持久化、树查询、后代查询、唯一性检查和乐观锁更新。
- `DepartmentMemberGateway`：负责用户主部门读写、直属成员查询、后代成员查询和用户主部门更新。

`application` 和 `domain` 只依赖网关接口，MyBatis 等技术实现位于 `infrastructure`。

## 3. 数据模型

现有 `iam_dept` 作为租户内部门主表，继续使用邻接表结构，核心字段如下：

```text
id
tenant_id
parent_id
dept_code
dept_name
sort_order
status
deleted
created_by / created_at
updated_by / updated_at
version
```

字段规则：

- `tenant_id`：部门只在当前租户内生效。
- `parent_id`：父部门引用。根部门固定为 `0` 或空值，二选一后全局统一。
- `dept_code`：租户内唯一，创建后不可修改。
- `dept_name`：可修改。
- `sort_order`：手工排序字段，列表按 `sort_order` 升序，再按 `id` 兜底稳定排序。
- `status`：只允许 `ACTIVE`、`DISABLED`。
- `version`：用于部门编辑、移动、停用和删除的乐观锁控制。

建议约束和索引：

- `(tenant_id, dept_code)` 唯一约束。
- `(tenant_id, parent_id)` 索引，用于树查询和子部门判断。
- `(tenant_id, status)` 索引，用于筛选启用部门。
- `dept_code` 只在创建时写入，后续不允许更新。

`iam_user` 需要补主部门字段：

```text
dept_id
```

字段规则：

- `dept_id` 必填，表示用户当前主部门。
- 用户主部门只能有一个。
- 新用户创建和用户调岗都必须保证 `dept_id` 指向当前租户内启用部门。

不建议本阶段引入 `path`、闭包表或部门成员关系表。当前树深上限已限制为 10 层，邻接表足够满足实现和维护成本要求。

## 4. API 与权限

部门树与成员相关 API：

```text
GET    /api/v1/admin/depts/tree
GET    /api/v1/admin/depts/{deptId}
POST   /api/v1/admin/depts
PUT    /api/v1/admin/depts/{deptId}
POST   /api/v1/admin/depts/{deptId}/move
POST   /api/v1/admin/depts/{deptId}/disable
POST   /api/v1/admin/depts/{deptId}/enable
DELETE /api/v1/admin/depts/{deptId}

GET    /api/v1/admin/depts/{deptId}/members
POST   /api/v1/admin/users/{userId}/transfer-dept
```

权限点：

```text
tenant:dept:create
tenant:dept:read
tenant:dept:update
tenant:dept:move
tenant:dept:enable
tenant:dept:disable
tenant:dept:delete
tenant:dept:member:read
tenant:user:transfer-dept
```

查询约定：

- `GET /tree` 返回完整部门树，包含 `id`、`parentId`、`deptCode`、`deptName`、`sortOrder`、`status` 和 `children`。
- 成员查询默认返回直属成员；`scope=descendants` 时返回当前部门及全部后代成员。
- 成员列表和树列表都支持分页或按需分页策略，具体由前端场景决定。

所有接口只能操作认证上下文中的当前租户，不接受客户端传入 `tenantId`。

## 5. 核心业务流程

### 5.1 创建部门

```text
校验部门管理权限
→ 校验父部门存在且启用
→ 校验新部门层级不超过 10
→ 校验同租户 `deptCode` 唯一
→ 创建部门
→ 写入 CREATE / DEPT 管理审计
→ 提交事务
```

根部门由系统自动创建，不通过普通新增接口创建。租户初始化时，根部门名称可先使用租户名，`deptCode` 固定为 `ROOT`。租户后续改名不会自动同步到根部门，根部门名称可单独维护。

### 5.2 编辑部门

部门编辑只允许修改名称、排序和状态等可变字段，不允许修改 `deptCode`、父部门和租户归属。编辑时通过 `version` 乐观锁防止并发覆盖。

### 5.3 移动部门

```text
校验目标父部门存在且启用
→ 校验目标父部门不是自身或后代
→ 校验移动后层级不超过 10
→ 更新 parent_id
→ 写入 MOVE / DEPT 管理审计
→ 提交事务
```

根部门不可移动。移动时必须把整棵子树视为一个事务单位，保证结构一致性。

### 5.4 停用部门

停用部门后：

- 保留现有成员
- 保留历史数据和权限计算中的组织关系
- 禁止新增子部门
- 禁止新增用户挂靠到该部门
- 禁止把其他用户调入该部门

停用只改变结构状态，不自动迁移现有成员。

### 5.5 删除部门

部门删除只允许删除空部门：

- 不能有子部门
- 不能有直接成员
- 根部门不可删除

删除时只做逻辑删除，不做物理删除。

### 5.6 用户主部门归属

```text
校验用户存在
→ 校验目标部门存在且启用
→ 校验用户当前归属与目标不同
→ 原子更新 user.dept_id
→ 写入 TRANSFER_DEPT / USER 管理审计
→ 提交事务
```

要求每个用户必须且只能有一个主部门。用户创建时必须绑定一个启用部门。调岗必须是原子替换，不允许临时出现无部门状态。

### 5.7 成员查询

直属成员查询仅按 `user.dept_id = deptId` 过滤。

后代成员查询先取当前部门及全部后代部门，再按这些部门的 `dept_id` 查询用户。查询结果必须保留与部门的映射关系，方便前端展示和筛选。

## 6. 并发与一致性

- 部门编辑、移动、启停和删除使用 `version` 乐观锁。
- 用户调岗也使用 `version` 乐观锁，避免并发覆盖主部门。
- 移动部门和调岗都必须放在单事务内完成。
- 重复启用、重复停用、重复删除应返回状态冲突或资源冲突。
- 部门树的递归查询由数据库或仓储层完成，应用层不手写遍历逻辑。
- 对树深、成环和启停约束的判断必须在事务内完成，不能只靠前端校验。

## 7. 校验、异常和兼容

错误码及 HTTP 状态映射：

```text
DEPT_NOT_FOUND                   → 404
DEPT_CODE_CONFLICT               → 409
DEPT_STATUS_CONFLICT             → 409
DEPT_CONCURRENT_MODIFICATION     → 409
DEPT_CYCLE_NOT_ALLOWED           → 400
DEPT_DEPTH_LIMIT_EXCEEDED        → 400
DEPT_NOT_EMPTY                   → 409
DEPT_ROOT_NOT_ALLOW              → 400
USER_NOT_FOUND                   → 404
USER_DEPT_CONFLICT               → 409
```

兼容策略：

- 现有 `iam_dept` 结构可直接复用，不做表级重构。
- 现有用户表若已存在其他主部门字段，迁移时以 `dept_id` 为准，旧字段只做一次性数据清理。
- 根部门初始化逻辑放在租户创建流程中，由租户模块统一创建。
- 后续如果需要更高性能的后代查询，再评估路径列或闭包表，但本期不提前引入。

## 8. 测试方案

### 8.1 领域测试

- 允许创建一层到十层部门，超过十层被拒绝。
- `deptCode` 创建后不可修改。
- 移动部门时禁止形成环。
- 根部门不可移动、不可删除。
- 停用部门后，不能继续新增子部门或调岗进入该部门。
- 删除非空部门被拒绝。
- 用户只能有一个主部门。

### 8.2 应用层测试

- 新建部门会正确校验父部门、深度和唯一性。
- 移动部门会保持树结构一致。
- 停用和删除会正确拦截非法状态。
- 调岗会在同一事务内完成主部门替换和审计。

### 8.3 Adapter 测试

- 树接口返回结构正确。
- 成员查询区分直属和后代范围。
- 权限不足时返回 403。
- 不同租户之间无法互相访问部门数据。
