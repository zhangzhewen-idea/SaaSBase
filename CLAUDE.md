# SaaSBase 项目协作说明

## 协作方式

- 默认使用中文沟通；代码、命令、变量名、文件路径和技术标识保留英文。
- 结论先行、表达简洁；直接说明风险、缺口与取舍，不使用无依据的肯定或铺垫。
- 项目内新增或修改的文档以中文为主；技术标识、命令、配置项和外部引用可保留原文。

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

生产环境的应用账号不具备 DDL 权限，且应用启动时 `spring.flyway.enabled=false`；数据库变更必须通过独立迁移步骤执行。执行生产迁移、启动或发布前，必须先取得用户明确确认。

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

## 测试、Git 与修改约束

- 功能或缺陷修复遵循 TDD：先写失败测试，再实现最小改动，最后运行相关测试。
- 测试按层编写：domain 使用纯单测；application 使用 mock/fake gateway；adapter 验证 HTTP 行为；infrastructure 使用 Testcontainers；跨层依赖使用 ArchUnit 验证。
- 处理文件前先查看 `.claudeignore`；不得读取、搜索、分析、修改或输出其中匹配的路径。若用户明确要求处理被忽略路径，先说明并等待确认。
- 提交前先向用户展示变更摘要；commit message 使用简洁中文。
- 删除文件、目录或 Git 历史；修改 `.env`、密钥、token、证书或 CI/CD 配置；执行 `git push`、`git rebase`、`git reset --hard`、强制推送或公开发布前，必须先取得用户明确确认。

## Skills 工作流

- 开始任务时，先检查当前会话提供的可用 skills；用户显式指定的 skill 必须优先使用。
- 项目内工作流 skills 位于 `.codex/skills/`，每个 skill 的 `SKILL.md` 是其适用条件和执行方式的唯一依据；不要硬编码 skill 数量或假定某个客户端专属工具一定存在。
- 新增功能或修改行为前先使用 `brainstorming`；实现功能或修复缺陷时遵循 `test-driven-development`；排查异常时使用 `systematic-debugging`；在宣称完成、提交或创建 PR 前使用 `verification-before-completion`。
