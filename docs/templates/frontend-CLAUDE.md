# SaaSBase 前端项目协作说明

## 项目定位

本项目是 SaaSBase 的前端工程。面向多平台访问，但不将所有终端强行实现为同一套页面。

- `admin`：租户后台和平台后台，面向 PC，使用 Vue 3、TypeScript、Vite、Vue Router、Pinia 与 Element Plus。
- `client`：用户侧 H5、小程序与移动 App，使用 uni-app（Vue 3 + TypeScript）。
- `portal`：官网、帮助中心等需要 SEO 的公开页面，按需使用 Nuxt 3；没有 SEO 需求时不创建。
- 多个应用共享 API SDK、领域模型、校验规则和纯工具函数；不跨应用直接复用页面或包含平台 API 的组件。

推荐使用 `pnpm workspace` 管理多应用。单一前端项目也应保持相同的职责边界。

```text
apps/
  admin/                 # Vue 3 + Vite 管理端
  client/                # uni-app 多端用户侧
  portal/                # Nuxt 3 公开站点（按需）
packages/
  api-client/            # 由 OpenAPI 生成的类型化 API Client
  shared/                # 纯领域模型、校验和工具函数
  eslint-config/         # 共享 ESLint 配置（按需）
  tsconfig/              # 共享 TypeScript 配置（按需）
```

## 技术约束

- 所有新增代码使用 TypeScript，保持 `strict`，不得以 `any` 绕过类型错误。
- Vue 组件默认使用 Composition API 与 `<script setup lang="ts">`。
- 管理端优先使用 Element Plus；uni-app 端优先使用 `uni-ui` 或经过验证的跨端组件。不要将 Element Plus 引入小程序或 App。
- 官网仅在确有 SEO、首屏性能或内容管理需求时使用 Nuxt 3；管理后台默认使用 Vite SPA。
- 不引入 Vue CLI、Vue 2、jQuery，或与 Vue 生态重复的状态管理方案。
- 组件只负责展示和交互；页面负责路由参数与用例编排；API 调用集中在 `api`、`services` 或由 `packages/api-client` 提供的 Client 中。

## 目录约定

以 `apps/admin` 为例：

```text
src/
  api/                   # API Client 的轻量封装；不手写重复 DTO
  components/            # 可复用展示组件
  composables/           # 可复用状态和交互逻辑
  layouts/               # 布局组件
  modules/               # 按业务域组织的页面、组件和状态
  router/                # 路由、导航守卫和权限元数据
  stores/                # Pinia store，仅存放跨页面客户端状态
  styles/                # 全局样式、设计令牌和主题
  utils/                 # 无业务副作用的工具函数
  views/                 # 仅在未按 modules 拆分时使用，不能与 modules 混用
```

- 业务能力按后端领域拆分，例如 `auth`、`tenant`、`iam`、`audit`、`system`、`file`。
- 单个模块内优先内聚页面、专用组件、composable 和状态；只有跨模块复用时才提升到 `components`、`composables` 或 `packages/shared`。
- `packages/shared` 不得依赖 Vue、uni-app、浏览器对象或任一应用的别名。
- 不在共享包中使用 `window`、`document`、`uni`、`process.client` 等平台对象。平台差异放在应用内适配层。

### 文件模块入口

文件模块面向租户后台，按 `/api/v1/admin/files` 提供基础文件能力。前端如果要接入文件上传、列表、详情、预览或删除，先看后端 OpenAPI，再以 `docs/postman/SaaSBase.postman_collection.json` 里的 `File` 分组作为联调入口。

- 上传：`POST /api/v1/admin/files`，`multipart/form-data`，文件字段名固定为 `file`。
- 列表：`GET /api/v1/admin/files`，支持 `filename`、`contentType`、`uploadedFrom`、`uploadedTo`、`pageNo`、`pageSize`。
- 详情：`GET /api/v1/admin/files/{id}`。
- 内容：`GET /api/v1/admin/files/{id}/content?disposition=inline|attachment`。
- 删除：`DELETE /api/v1/admin/files/{id}`。

文件相关页面只处理展示、筛选和触发动作；上传和下载的具体请求细节统一通过 API Client 或 `api` 层封装，不在页面里散落硬编码路径。

## 后端 API 契约

后端 API 使用 REST + JSON，并按版本划分路径：

```text
/api/v1/auth/**
/api/v1/admin/**
/api/v1/platform/**
/api/v1/open/**
```

- 以后端 OpenAPI 文档为唯一接口事实来源。变更接口后，先更新后端契约，再重新生成 `api-client`；不要手工复制请求和响应类型。
- 不得把后端业务错误包装成“前端成功”。HTTP 状态码、业务错误码和错误信息须按契约分别处理。
- 分页、排序、枚举与字段命名必须遵循 OpenAPI；前端不得猜测未发布字段或自行拼接排序字段。
- 请求基地址、超时、重试策略和环境开关通过配置统一管理，禁止散落硬编码 URL。
- 多租户上下文的传递方式必须以后端已发布契约为准。不得擅自假设 `tenant_id` 参数或 `X-Tenant-Id` 请求头。
- 平台管理员 API 与租户管理员 API 必须在路由、菜单和调用层清晰分离；前端隐藏入口不是鉴权，后端鉴权始终是最终边界。

### 依据当前后端 `adapter` 的前端接入边界

当前后端已经明确了以下前端可依赖的接口形态，新增页面和 `api` 封装必须优先贴合这些契约：

- 认证：`/api/v1/auth/login`、`/api/v1/auth/refresh`、`/api/v1/auth/logout`
  - 登录和刷新都返回统一包装的 `ApiResponse<LoginResponse>`。
  - 退出时会从 `Authorization: Bearer <token>` 中读取访问令牌，前端调用 logout 时应保留该请求头。
- 租户后台用户：`/api/v1/admin/users`
  - 列表分页参数为 `page`、`size`，不是 `pageNo`、`pageSize`。
  - 支持 `username`、`departmentId`、`status`、`phone` 过滤。
  - 详情、启用、停用、重置密码、转部门都在同一资源下按 `/{userId}` 子路径暴露。
  - 所有写操作都依赖租户上下文，前端不应自行传租户 ID。
- 租户后台部门：`/api/v1/admin/depts`
  - 树结构通过 `/tree` 获取，成员列表通过 `/{deptId}/members` 获取。
  - `delete`、`disable`、`enable`、`move` 都依赖版本或命令对象，前端必须保留乐观锁字段，不要删掉。
- 租户后台租户资料：`/api/v1/admin/tenant/profile`
  - 这是当前租户的资料视图，适合用于顶部租户信息、设置页和只读展示。
- 平台后台租户：`/api/v1/platform/tenants`
  - 平台侧创建、更新、启用、停用都需要额外的 `operatorId` 查询参数，前端封装时不能漏传。
  - 平台列表使用 `TenantQuery`，分页字段为 `pageNo`、`pageSize`。
- 文件模块：`/api/v1/admin/files`
  - 上传字段名固定为 `file`。
  - 列表筛选字段为 `filename`、`contentType`、`uploadedFrom`、`uploadedTo`、`pageNo`、`pageSize`。
  - `content` 接口支持 `disposition=inline|attachment`，但只有 PDF、PNG、JPEG 允许按 `inline` 展示，其他类型应按下载处理。
  - 列表和详情返回的 `FileView` 不包含存储实现细节，前端不要假设存在对象存储 key、bucket 或路径。

### 响应与错误处理

- 所有后端成功响应统一包在 `ApiResponse<T>` 中，前端应优先读取 `data`，不要把 `success` 误解为唯一判断条件。
- 列表接口分页统一使用后端返回的 `PageResponse<T>`，其中 `items`、`total`、`pageNo`、`pageSize` 是稳定字段。
- 对于表单校验失败、权限不足、认证失效、版本冲突和资源不存在，前端分别给出明确提示，不要用通用失败文案糊过去。
- 需要按后端字段展示错误详情时，优先保留 `code` 和 `message`，不要自己重写语义。

## 认证、权限与安全

- 登录、退出、刷新 token、切换租户和权限变更必须通过统一的认证模块处理，业务页面不得自行读写 token。
- token 的存储、刷新和失效策略必须与后端 JWT/refresh token 契约一致。未确认前不要默认使用 `localStorage`、Cookie 或自定义 Header。
- 认证失效时，清理内存中的敏感状态并跳转登录页；不要无限重试刷新接口。
- 禁止在日志、埋点、错误提示、截图、URL 参数或前端状态持久化中暴露密码、access token、refresh token、验证码、密钥和用户隐私。
- 菜单和按钮权限仅用于体验控制；所有敏感操作仍必须由后端授权。
- 富文本、服务端错误信息和用户输入必须按输出位置处理，避免 XSS。没有明确必要性时，不使用 `v-html`。
- 权限码必须与后端 `@PreAuthorize` 保持一致，例如 `tenant:user:read`、`tenant:dept:create`、`platform:tenant:disable`、`tenant:file:delete`。
- 租户后台默认依赖当前登录上下文，平台后台按显式接口区分，不要用同一套前端菜单去猜测后端权限范围。

## UI 与多端规则

- 管理端以信息密度、键盘操作、表格筛选、批量操作和可访问性为优先目标；不要为追求“移动化”牺牲 PC 效率。
- `client` 以触控交互、弱网、首屏体积和小程序审核限制为优先目标；需要平台能力时通过条件编译或适配器隔离。
- 先保证核心流程在 H5、目标小程序和 App 的一致行为，再做平台特有增强。
- 所有删除、停用、权限变更、跨租户操作等高风险动作必须二次确认，并展示可理解的影响范围。
- 设计令牌（颜色、间距、字号、圆角、层级）统一维护，不在业务组件中散落魔法值。

## 状态与数据获取

- 远程数据优先在页面或模块级 composable 中管理；仅跨页面共享、需要缓存或与会话相关的状态才进入 Pinia。
- Pinia store 不得成为任意 API 调用的堆放处，也不得保存可由路由或服务端重新获取的数据副本。
- 异步请求需要明确 `loading`、`empty`、`error` 与成功状态；并发请求需避免旧响应覆盖新状态。
- 所有列表页提供与接口契约一致的分页、筛选和空状态。删除或更新后，按当前查询条件刷新数据。
- 页面级筛选条件要和后端参数名一一对应，不要在前端做“语义上差不多”的重命名后再手工转换。
- 详情页和编辑页应优先复用同一套 `api` 方法和 DTO 解析逻辑，避免列表、详情、表单三套结构漂移。

## 样式与可访问性

- 使用语义化 HTML，表单元素必须有关联的标签或可访问名称。
- 交互元素必须可通过键盘访问；不能只依赖颜色传递状态。
- 图片、图标按钮和加载状态提供合适的替代文本或 `aria-label`。
- 以响应式布局适配浏览器宽度；不要仅通过 User-Agent 判断设备类型。
- 不提交未经压缩的大图、重复图标库或未使用的组件库全量样式。

## 环境变量与配置

- 仅提交 `.env.example`，其中只包含变量名、示例值和说明；不得提交真实密钥、token、生产地址或个人账号。
- 所有暴露给浏览器或小程序包的环境变量都视为公开信息，严禁放置服务端密钥。
- 新增环境变量时，同步更新 `.env.example` 和使用说明，并提供安全的默认行为。
- 不修改 `.env`、密钥、证书、CI/CD 配置；确有需要时先征得用户确认。

## 测试与质量门禁

- 业务规则、composable 与工具函数使用 Vitest 编写单元测试。
- 关键用户流程（登录、刷新会话、租户切换、权限拦截、核心 CRUD）使用 Playwright 编写端到端测试；uni-app 端按目标平台补充真机或模拟器验证。
- 每次修改至少运行受影响的测试；提交前运行以下命令（以实际 `package.json` 为准）：

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

- 修复 Bug 先编写能复现问题的测试，再做最小修复。不要以关闭类型检查、删除测试或吞掉异常作为解决方案。

## 常用命令

```bash
# 安装依赖
pnpm install

# 启动指定应用
pnpm --filter @saasbase/admin dev
pnpm --filter @saasbase/client dev:h5

# 生成 API Client（命令由实际生成器脚本定义）
pnpm api:generate

# 质量检查
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

不要假设命令必然存在。执行前先检查根目录与目标应用的 `package.json`。

## Git 与文件操作

- 处理任何文件前先查看 `.claudeignore`；其中匹配的路径不得主动读取、搜索、修改或输出。用户明确要求处理时，先说明并等待确认。
- 提交前展示变更摘要；commit message 使用简洁中文。
- 删除文件或目录、修改 `.env`/密钥/证书/CI/CD 配置、执行 `git push`、`git rebase`、`git reset --hard` 或生产发布，必须先征得用户确认。
- 不因无关格式化、依赖升级或重构扩大改动范围。

## 完成前检查

- [ ] 接口调用与当前 OpenAPI 契约一致，且没有手写重复 DTO。
- [ ] 认证、租户、部门、用户、文件和平台租户接口的路径、分页字段和权限码都与后端 `adapter` 一致。
- [ ] 多租户、平台管理员与租户管理员边界清晰。
- [ ] 没有泄露 token、密码、密钥或隐私数据。
- [ ] 完成受影响的 lint、类型检查、测试和构建。
- [ ] 页面包含加载、空数据、错误和无权限状态。
- [ ] 多端代码没有引用不兼容的平台组件或 API。
- [ ] 更新了必要的 `.env.example`、OpenAPI 生成结果或使用文档。
