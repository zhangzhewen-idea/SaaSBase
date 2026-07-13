# SaaSBase 项目协作说明

## 项目概览

- 这是一个面向生产的 Java SaaS 后端基础系统，采用模块化单体和 COLA light 包分层。
- 技术基线：Java 25、Spring Boot 4.1、MyBatis-Plus、MySQL、Redis、Flyway、Spring Security、OpenAPI，以及 Testcontainers。
- 业务能力按域组织，例如 `auth`、`tenant`、`iam`、`audit`、`system`、`file`。
- 外部 HTTP API 使用 REST + JSON，并保持路径版本号：`/api/v1/auth/**`、`/api/v1/admin/**`、`/api/v1/platform/**`、`/api/v1/open/**`；其中 `/api/v1/open/**` 在第一阶段仅预留，不实现开放平台 API。

## 常用命令

```bash
# 启动本地依赖并运行应用
docker compose -f docker-compose.local.yml up -d
mvn spring-boot:run

# 运行测试
mvn test

# 生产迁移与启动
docker compose -f docker-compose.prod.yml --profile migration run --rm migration
docker compose -f docker-compose.prod.yml up -d app
```

生产环境的应用账号不具备 DDL 权限，且应用启动时 `spring.flyway.enabled=false`；数据库变更必须通过独立迁移步骤执行。

## 代码架构

依赖方向必须保持为：

```text
adapter -> application -> domain <- infrastructure
```

- `adapter`：Controller、REST 入口、请求绑定和响应适配，以及经单独设计批准的消息或 job 入口；不得包含业务规则，也不得直接调用 `infrastructure`。
- `application`：用例编排、事务边界和 DTO 映射；只能依赖 `domain` 与 `domain/gateway` 接口，不能依赖 Mapper、Redis Client、SDK 或其他 `infrastructure` 实现。
- `domain`：实体、值对象、规则、策略、领域服务及领域异常；不得依赖 `adapter`、`application` 或 `infrastructure`。
- `domain/gateway`：以业务能力命名的外部能力接口。
- `infrastructure`：MyBatis Mapper、Redis、文件存储、外部服务客户端与 gateway 实现；不得反向依赖上层。

新增功能时，先确认所属业务域和现有 package；只在确有边界契约时新增 `application/dto`。跨实体流程由 application service 编排，可复用业务规则下沉到 domain。使用 ArchUnit 保持分层约束。

## 租户、安全与数据规则

- 租户业务表必须包含 `tenant_id`，普通业务查询必须带租户边界；租户内唯一约束使用 `(tenant_id, <business_unique_field>)`。
- 跨租户能力只能通过显式命名、单独鉴权的平台 API 提供；不得隐式跨租户查询或写入。
- 认证使用 JWT；refresh token、吊销记录、限流等短期安全状态放 Redis，用户及审计等权威数据放 MySQL。
- 登录、退出、刷新 token、登录失败和密码变更必须保留安全审计；审计日志为追加式记录，不使用普通逻辑删除。
- 日志和响应不得泄露密码、token、密钥、证书或其他敏感值。

## 数据库与部署

- 所有结构、索引与内置初始化数据变更通过 Flyway 新增迁移脚本；不要修改已经执行过的迁移文件。
- 普通业务表默认包含审计字段、逻辑删除字段与乐观锁版本；持续增长表需要可查询的时间字段及租户/时间索引。
- 本地文件存储只能通过 `FileStorageGateway` 访问，业务代码不得直接读写本地路径；它仅适用于单机 Compose，迁移到多实例 Kubernetes 或正式接入阿里云文件能力前必须切换至 OSS 适配器。
- 应用应保持无状态，配置外置，日志输出到 stdout/stderr；第一阶段生产以 Docker Compose 单机部署起步，而非长期基础设施上限；生产使用外部 MySQL 和 Redis，不在生产 Compose 中自建它们。

## 测试与修改约束

- 功能或缺陷修复遵循 TDD：先写失败测试，再实现最小改动，最后运行相关测试。
- 测试按层编写：domain 使用纯单测；application 使用 mock/fake gateway；adapter 验证 HTTP 行为；infrastructure 使用 Testcontainers；跨层依赖使用 ArchUnit 验证。
- 处理文件前先查看 `.claudeignore`，不要读取、搜索、修改或输出其中匹配的路径；若用户明确要求处理，应先说明并等待确认。
- 默认使用中文沟通与编写项目文档；代码、命令、变量名、路径和技术标识保留英文。
- 删除文件或目录、修改 `.env`/密钥/证书/CI 配置、执行 `git push`、`git rebase`、`git reset --hard` 或生产发布前，必须先征得用户确认。

<!-- superpowers-zh:begin (do not edit between these markers) -->
# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.claude/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文 review 沟通参考——话术模板、分级标注（必须修复/建议修改/仅供参考）、国内团队常见反模式应对。仅在用户显式 /chinese-code-review 时调用，不要根据上下文自动触发。
- **chinese-commit-conventions**: 中文 commit 与 changelog 配置参考——Conventional Commits 中文适配、commitlint/husky/commitizen 中文模板、conventional-changelog 中文配置。仅在用户显式 /chinese-commit-conventions 时调用，不要根据上下文自动触发。
- **chinese-documentation**: 中文文档排版参考——中英文空格、全半角标点、术语保留、链接格式、中文文案排版指北约定。仅在用户显式 /chinese-documentation 时调用，不要根据上下文自动触发。
- **chinese-git-workflow**: 国内 Git 平台配置参考——Gitee、Coding.net、极狐 GitLab、CNB 的 SSH/HTTPS/凭据/CI 接入差异与镜像同步配置。仅在用户显式 /chinese-git-workflow 时调用，不要根据上下文自动触发。
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
- **mcp-builder**: MCP 服务器构建方法论 — 系统化构建生产级 MCP 工具，让 AI 助手连接外部能力
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发，或在执行实现计划之前使用——通过原生工具或 git worktree 回退机制确保隔离工作区存在
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能，要求在任何响应（包括澄清性问题）之前调用 Skill 工具
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流——无需 API key，使用当前会话的 LLM 作为执行引擎。当用户提供 .yaml 工作流文件或要求多角色协作完成任务时触发。
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，使用当前代理环境支持的方式加载并遵循对应 `SKILL.md`。不要假设 `Skill`、`Read` 等某个工具名在所有代理中都存在。

如果你认为哪怕只有 1% 的可能性某个 skill 适用于你正在做的事情，你必须调用该 skill 检查。
<!-- superpowers-zh:end -->
