# SaaS 核心后端设计

日期：2026-07-09
状态：待用户审查

## 1. 目标

构建一个可上线真实使用的 Java SaaS 后端基础系统。第一阶段只做核心后端基座，不做完整 SaaS 平台。支付订阅、开放平台、完整文件中心、消息通知、任务调度和用户行为分析都不进入第一阶段。

第一阶段包含：

- 自有账号体系和 JWT 认证。
- 租户、组织、用户、角色、权限和数据范围管理。
- 安全审计和管理操作审计。
- 系统配置、健康检查和 OpenAPI 文档。
- Docker Compose 单机生产起步方案。
- 生产依赖使用阿里云托管 MySQL/Redis。
- 通过可替换存储网关封装本地文件存储适配器。

## 2. 技术基线

实施前必须再次确认官方版本状态和阿里云托管服务可用版本，再锁定最终依赖版本。设计目标如下：

- Java 语言版本确定为 Java 25 LTS。
- JDK 发行版生产默认使用 Alibaba Dragonwell 25 Standard Edition，当前锁定版本为 `25.0.3.0.3.9`。实施前必须复核 Dragonwell 官方 `releases.json`、Spring Boot 支持矩阵、目标 CPU 架构、容器基础镜像和阿里云运行环境；如果已有同一 Java 25 线的安全补丁版本，应升级到最新补丁版本。应用代码只依赖标准 Java/Spring 能力，不绑定 Dragonwell 私有特性。
- Alibaba Dragonwell Extended Edition 不作为第一阶段默认运行时；只有在明确需要阿里云生产优化特性，并经过压测和回滚验证后，才作为后续性能专项选项引入。
- Spring Boot 稳定版本线。根据 Spring Boot 官方项目页，当前设计按 Spring Boot 4.1.0 规划。
- MySQL LTS 兼容 SQL 基线。MySQL 8.4 LTS 作为保守兼容下限；实施前必须结合阿里云 RDS/OceanBase 可用版本最终选择生产版本。SQL 应避免不必要的 MySQL 私有能力，为后续 OceanBase/NewSQL 迁移保留空间。
- Redis 是生产必需依赖，生产使用阿里云 Tair/Redis。
- 持久化使用 MyBatis/MyBatis-Plus。
- 数据库迁移使用 Flyway。
- HTTP API 使用 REST + OpenAPI/Swagger。
- MySQL/Redis 集成测试使用 Testcontainers。

## 3. 架构

系统采用模块化单体和 COLA light 包分层。一个 Spring Boot 应用内部按能力域拆分，例如 `auth`、`tenant`、`iam`、`audit`、`system`、`file`。

要求的 package 依赖方向如下：

```text
adapter -> application -> domain <- infrastructure
```

各层职责：

- `adapter`：REST 入口、请求绑定、响应适配、认证入口，以及未来的消息/job 入口。
- `application`：用例编排、事务边界、command/query 流程和 DTO 映射。
- `application/dto`：request/response DTO 和 command/query 对象。
- `domain`：entity、value object、domain service、rule、policy、factory 和 domain exception。
- `domain/gateway`：domain/application 需要的外部能力接口。
- `infrastructure`：MyBatis mapper、persistence object、Redis client、文件存储适配器、外部 service client 和 gateway 实现。

硬性架构规则：

- `application` 依赖 gateway 接口，不依赖 MyBatis mapper、Redis client 或存储 SDK。
- `adapter` 不得直接调用 `infrastructure`。
- `domain` 不得依赖 `adapter`、`application` 或 `infrastructure`。
- 使用 ArchUnit 测试保护 package 依赖方向。

## 4. 数据与租户模型

租户隔离采用共享库共享表加 `tenant_id`。

规则：

- 所有租户业务表必须包含 `tenant_id`。
- 租户内查询默认必须带租户边界。
- 平台超级管理员的跨租户 API 必须显式命名并单独授权。
- 普通租户 API 不得隐式跨租户读写。

主键使用系统内部 ID，不把业务标识作为主键。租户内唯一约束必须使用 `(tenant_id, <business_unique_field>)`，例如：

- `(tenant_id, username)`
- `(tenant_id, role_code)`
- `(tenant_id, dept_code)`
- `(tenant_id, file_key)`

普通业务表通用字段：

```text
id
tenant_id
created_at
created_by
updated_at
updated_by
deleted
deleted_at
deleted_by
version
```

`version` 用于乐观锁。用户、部门、角色、菜单、租户配置、业务资源和文件元数据默认使用逻辑删除。

以下表默认不使用普通逻辑删除：

- 登录日志。
- 安全审计日志。
- 管理操作审计日志。
- token 黑名单记录。
- 幂等记录。
- 验证码和其他短期状态记录。
- 简单关系表，除非明确需要历史追踪。

大表和持续增长表必须包含可查询的时间字段，以及租户/时间索引。审计表必须支持后续归档。禁止无边界分页、无租户条件业务查询和隐式跨租户 join。

## 5. 数据库迁移

数据库变更通过 Flyway 管理。

Flyway 管理：

- 表结构。
- 索引。
- 内置权限点。
- 内置角色模板。
- 系统字典类型。
- 系统默认配置。

Flyway 不管理运行期租户业务数据，例如上线后创建的用户、租户部门、租户角色授权、上传文件记录、订单或未来业务单据。

生产控制：

- 应用数据库账号不得拥有 DDL 权限。
- DDL 通过发布流程中的专用迁移账号执行。
- Flyway checksum 校验在迁移脚本被修改或缺失时必须失败。
- 紧急生产 DDL 必须回补为迁移脚本，并记录在发布/变更流程中。

## 6. 认证

认证采用自有账号体系和 JWT。

核心行为：

- 登录签发短期 `access token` 和可轮换的 `refresh token`。
- refresh token 状态、token 吊销列表、登录失败计数、验证码、一次性 token 和短期安全状态存入 Redis。
- 用户、密码哈希、账号状态和安全审计记录存入 MySQL。
- 密码必须使用安全哈希算法。
- 登录、退出、token 刷新、登录失败、修改密码和重置密码必须创建安全审计记录。

租户身份和用户身份分离：

- 一个用户可以属于一个或多个租户。
- 登录后，请求必须携带或解析出当前租户上下文。
- 普通业务 API 需要当前 `tenant_id`。
- 平台超级管理员 API 与租户 API 分离。

第一阶段不实现 OAuth2/OIDC。身份域应为未来外部身份绑定预留扩展点。

## 7. 授权

授权采用 RBAC 加数据范围。

规则：

- 权限点按 API、菜单和操作定义。
- 角色归属租户。
- 用户通过租户成员关系获得角色。
- 数据范围在租户隔离之后生效，不得替代 `tenant_id` 隔离。

第一阶段支持的数据范围：

- 全部数据。
- 本部门及下级部门。
- 本部门。
- 本人数据。
- 自定义部门。

第一阶段不实现 ABAC/policy-engine 权限模型。

## 8. 审计

第一阶段实现安全审计和管理操作审计。

安全审计记录：

- 登录。
- 退出。
- token 刷新。
- 登录失败。
- 修改密码。
- 重置密码。

管理操作审计记录：

- 租户变更。
- 用户变更。
- 部门变更。
- 角色变更。
- 权限变更。
- 系统配置变更。

审计日志是追加式记录，不使用普通逻辑删除。

前端行为埋点和大数据分析不属于审计模块。它们是后续独立子系统，可能路径为 frontend SDK、collection API、MQ/Kafka、Flink/Spark、MaxCompute/Hologres/ClickHouse/OSS data lake 和 BI。

## 9. API 设计

HTTP API 使用 REST + JSON + OpenAPI/Swagger。

所有外部 HTTP API 使用路径版本号：

```text
/api/v1/auth/**
/api/v1/admin/**
/api/v1/open/**
```

`/api/v1/open/**` 在第一阶段仅预留，不实现开放平台 API。

规则：

- 保留 HTTP 状态码语义，不把所有错误包装成 HTTP `200`。
- 在有价值的地方使用统一响应结构，并保持稳定业务错误码。
- 使用统一分页格式和排序白名单。
- 统一参数校验规则。
- 按模块生成 OpenAPI 文档。
- OpenAPI 标题/版本必须明确标记为 `SaaSBase API v1`。

未来实现 Open API 时，其治理要比 admin API 更严格：

- 破坏性变更必须开新 API 版本。
- 字段可以兼容新增，但已有字段名和含义必须稳定。
- 错误码必须稳定。
- 分页和鉴权语义必须稳定。
- 废弃 API 必须提供迁移窗口。

## 10. 部署

第一阶段生产起步使用 Docker Compose 单机部署。这是起步部署形态，不是长期基础设施上限。

生产假设：

- MySQL 使用阿里云托管服务。
- Redis 使用阿里云 Tair/Redis。
- 生产 Compose 不自建 MySQL 或 Redis。

按用途需要的文件：

- `docker-compose.local.yml`：本地开发依赖，可包含本地 MySQL/Redis。
- `docker-compose.prod.yml`：只包含应用和无状态反向代理组件。

应用要求：

- 应用进程无状态。
- 配置外置。
- 仓库不得提交凭据。
- 日志输出到 stdout/stderr。
- 健康检查兼容 readiness/liveness 概念。
- 文件、session、cache、database 依赖不得依赖容器临时存储。

未来迁移 Kubernetes 时，应复用同一镜像、环境变量、健康检查、日志模型和外部依赖连接模型。Flyway 后续可从应用启动迁移到 CI/CD 步骤或 Kubernetes Job。

## 11. 文件存储

第一阶段使用本地文件存储，但只能通过可替换的 `FileStorageGateway` 访问。

规则：

- 业务代码不得直接读写本地路径。
- 文件元数据存入 MySQL。
- 存储适配器记录 `tenant_id`、`storage_type`、`object_key`、`filename`、`content_type`、`size` 和审计字段。
- 本地存储路径通过外部配置提供，并在单机 Compose 中挂载为 volume。
- 本地存储不适用于多实例 Kubernetes 生产环境。
- 在迁移 Kubernetes 或正式接入阿里云文件能力之前，必须切换到 OSS 适配器。

第一阶段排除：

- 完整文件中心。
- 分片上传。
- 预览转换。
- 病毒扫描。
- 复杂生命周期规则。

## 12. Redis 使用

Redis 是生产必需依赖。

第一阶段允许的用途：

- refresh token 存储和轮换。
- access token 吊销列表。
- 登录失败和账号/IP 限流。
- 验证码和一次性 token。
- 短期安全状态。
- 权限缓存和租户配置短期缓存。
- 幂等 key 和防重复提交。

权威数据仍然保存在 MySQL。Redis 不得成为租户、用户、角色、权限或核心配置的唯一存储。

## 13. 错误处理

系统使用稳定业务错误码和合适的 HTTP 状态码。

示例：

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_REVOKED`
- `TENANT_NOT_FOUND`
- `IAM_PERMISSION_DENIED`
- `IAM_DATA_SCOPE_DENIED`
- `RESOURCE_CONFLICT`

HTTP 状态码语义：

- `400`：请求非法或参数校验失败。
- `401`：未认证。
- `403`：已认证但无权限。
- `404`：资源不存在。
- `409`：冲突，包括乐观锁冲突。
- `500`：非预期服务端错误。

日志在可用时必须包含 `trace_id`、`tenant_id`、`user_id`、请求路径和错误码。日志不得输出密码、token、密钥、私钥、证书或其他敏感值。

## 14. 测试

测试采用分层测试加 Testcontainers。

需要的测试形态：

- Domain tests：纯单元测试，覆盖 entity、value object、rule、policy 和 domain service。
- Application tests：使用 mock/fake gateway 测试用例编排。
- Adapter tests：使用 MockMvc 测试请求绑定、认证、授权、参数校验和响应结构。
- Infrastructure tests：使用 Testcontainers 启动 MySQL/Redis，测试 Flyway 脚本、MyBatis SQL、索引、唯一约束、Redis TTL、token 吊销和 gateway 实现。
- Architecture tests：使用 ArchUnit 保护 COLA 依赖方向。

不得用 H2 或其他内存数据库作为 MySQL 行为的主要集成测试替代。

## 15. 验收标准

第一阶段满足以下条件即可验收：

- 应用可以在本地启动。
- Flyway 可以初始化空数据库。
- OpenAPI 文档可访问。
- 核心 API 覆盖认证、租户、组织、用户、角色、权限、数据范围、审计查询和系统配置。
- 普通租户 API 不能跨租户读写数据。
- `username`、`role_code`、`dept_code` 等字段的租户内唯一约束生效。
- 登录失败限流生效。
- refresh token 轮换生效。
- token 吊销生效。
- 必要事件能创建安全审计和管理操作审计记录。
- Docker Compose 可以启动应用，并连接外部配置的 MySQL/Redis。
- 测试套件覆盖 domain、application、adapter、infrastructure 和 architecture 边界。

## 16. 明确不在范围内

以下内容不属于第一阶段设计，应拆成后续规格：

- 支付、订阅、套餐、额度、订单和计费。
- 开放平台实现、API key/签名系统和外部开发者门户。
- 阿里云 OSS 生产适配器。
- 完整文件中心。
- 消息和通知中心。
- 任务调度平台。
- 用户行为分析和大数据链路。
- Kubernetes/Helm 生产部署。
- OAuth2/OIDC 登录。
- ABAC policy engine。

## 17. 来源说明

起草设计时，已针对版本敏感信息查验官方来源：

- Oracle Java SE Support Roadmap: https://www.oracle.com/java/technologies/java-se-support-roadmap.html
- Alibaba Dragonwell: https://dragonwell-jdk.io/
- Alibaba Dragonwell releases manifest: https://dragonwell-jdk.io/releases.json
- Spring Boot official project page: https://spring.io/projects/spring-boot/
- MySQL official reference manual: https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html
