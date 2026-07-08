---
name: cola-architecture
description: Use when working in Java Spring service projects that should follow COLA light package layering, including scaffolding, refactoring, code review, or placing application, domain, adapter, gateway, and infrastructure code.
---

# COLA Architecture

## 概览

使用 COLA light 作为 Java 服务的轻量 package 分层方法：小型服务保持单 Maven module，通过 package、测试和 review 维护架构边界。

核心依赖方向：

```text
adapter -> application -> domain <- infrastructure
```

## Package 布局

轻量 COLA 服务使用这个结构：

```text
src/main/java
├── Application.java
├── adapter/
├── application/
│   └── dto/
├── domain/
│   ├── <biz-domain>/
│   └── gateway/
└── infrastructure/
```

## 分层职责

| Layer | 应该放什么 | 不应该放什么 |
|---|---|---|
| `adapter` | REST/RPC/MQ 入口、参数适配、调用 application service | 业务规则、数据库客户端、外部 SDK 调用 |
| `application` | 用例编排、事务边界、command/query 流程、DTO 映射 | 应进 domain 的核心业务决策、持久化技术细节 |
| `application/dto` | request/response、查询结果 DTO、接口请求/响应数据对象 | 带业务行为的实体 |
| `domain` | entity、value object、domain service、rule、policy、factory、domain exception | Controller、HTTP client、repository 实现、技术编排 |
| `domain/gateway` | domain/application 需要的外部能力接口 | 技术实现细节、SQL、RestTemplate、SDK client |
| `infrastructure` | gateway 实现、持久化适配、外部 API client、消息/数据库/缓存集成 | 新业务规则 |

## 实施流程

1. 先检查现有 package，沿用项目已有命名。
2. 识别本次变更的用例、业务域、实体关系、外部依赖。
3. 只有边界需要 request/response 对象时，才在 `application/dto` 新增 DTO。
4. 在 `application` 新增或修改应用服务方法，只做流程编排。
5. 把业务不变量、计算、策略、规则放进 `domain`。
6. 把外部能力抽象为 `domain/gateway` 接口。
7. 在 `infrastructure` 实现 gateway 接口。
8. 在 application/domain 行为存在后，再通过 `adapter` 暴露入口。
9. 在最低可用层添加测试；如果跨 package 依赖有风险，添加架构测试。

## 放置判断

- 解析 HTTP、RPC、MQ、CLI、job 输入：放 `adapter`。
- 编排一个业务用例，协调多个对象或 gateway：放 `application`。
- 回答业务问题，且不关心数据如何存储或传输：放 `domain`。
- 调用数据库、缓存、远程服务、文件系统、SDK：放 `infrastructure`。
- domain/application 需要外部能力：先在 `domain/gateway` 定义接口。
- 类名以 `Controller`、`Listener`、`Consumer`、`Endpoint` 结尾：通常放 `adapter`。
- 类名以 `GatewayImpl`、`RepositoryImpl`、`Client`、外部数据 `Mapper` 结尾：通常放 `infrastructure`。
- 类名以 `Rule`、`Policy`、`Plan`、`Value`、`Entity`、`DomainService`、`Factory` 结尾：通常放 `domain`。

## 边界规则

保持依赖单向：

- `adapter` 可以依赖 `application` 和 DTO。
- `application` 可以依赖 `domain` 和 `domain/gateway`。
- `domain` 禁止依赖 `adapter`、`application`、`infrastructure`。
- `infrastructure` 可以依赖 `domain`，并实现 `domain/gateway`。
- `adapter` 禁止直接调用 `infrastructure`。
- `application` 可以通过 `domain/gateway` 接口使用 infrastructure 提供的能力，但源码禁止直接依赖 `infrastructure.*` 包或具体实现类。
- `adapter/application` 层入口对象禁止同层互调：`Controller` 不调用另一个 `Controller`，application service 不调用另一个 application service 来复用流程。共享逻辑应下沉到 `domain`，或提取为明确的应用层协作者/组件。

Spring 注入应该让 application service 依赖 gateway 接口；运行时实现由 `infrastructure` 提供。

## Entity 协作与循环引用

不要引入聚合根等重 DDD 概念来解决轻量项目的问题。保持规则简单：

- entity 之间允许简单协作，但避免双向对象引用和互相递归调用。
- 跨 entity 的流程编排优先放在 application service，由 application 负责加载多个 entity、控制调用顺序、调用 gateway 保存结果。
- 可复用的业务规则仍应下沉到 domain，而不是长期堆在 application。
- 遇到 entity 循环引用风险时，优先用 id 引用、单向引用、DTO 映射或 application 编排打断对象图循环。
- 不要为了复用方法让 entity A 持有 entity B，同时 entity B 又持有 entity A。

## 常见变更

新增 REST endpoint：

1. 需要新的边界契约时，才在 `application/dto` 创建 request/response DTO；否则复用已有 command/query/DTO。
2. 在 application service 增加用例方法。
3. 把业务规则移入 `domain`。
4. 如果用例需要数据库、缓存、远程服务、文件系统或 SDK 等外部能力，先定义 gateway 接口。
5. 如果定义了 gateway 接口，在 `infrastructure` 实现 gateway。
6. 在 `adapter` 增加 controller 方法。

新增业务规则：

1. 新增或修改 domain object、rule、policy、domain service。
2. application service 只保留编排。
3. 优先用不启动 Spring 的 domain 单测验证规则。

新增外部集成：

1. 在 `domain/gateway` 定义以能力命名的接口，不以技术命名。
2. 在 `infrastructure` 增加实现。
3. application service 注入接口，不注入实现类。

## 测试规则

优先使用这个测试形状：

- Domain tests：纯单测，覆盖 entity、value object、rule、policy、domain service。
- Application tests：使用 fake/mock gateway 验证用例编排。
- Infrastructure tests：集成测试，覆盖持久化、远程调用、gateway 实现和映射。
- Adapter tests：验证请求绑定、响应形状、是否委托 application service。
- Architecture tests：使用 ArchUnit 或同类工具保护依赖方向。

轻量 package 分层没有 Maven module 的硬隔离，所以当变更引入跨 package 依赖时，要添加或启用架构测试。最低要求应覆盖：`domain` 不依赖 `adapter`、`application`、`infrastructure`；`application` 不依赖 `infrastructure` 包或具体实现类；`adapter` 不依赖 `infrastructure`；`infrastructure` 不依赖 `adapter` 或 `application`。

## 实用取舍

COLA light 是实用分层，不是纯 Clean Architecture。项目可以把 JPA annotation 放在 domain entity 上以减少映射成本，但只有在这符合现有项目风格时才接受。如果持久化细节开始主导 domain 行为，就把 persistence object 或 mapper 拆到 `infrastructure`。

不要机械造层。服务很小时保持 package 布局即可，避免没有行为或边界价值的空抽象。只有当外部能力、测试 seam 或架构边界真实存在时，才增加 gateway 接口。

## 常见错误

| 错误 | 修正 |
|---|---|
| 在 `Controller` 写业务计算 | 把规则移到 `domain`，`adapter` 只做输入输出适配 |
| application service 直接使用 mapper/client/SDK | 定义 `domain/gateway` 接口，把实现移到 `infrastructure` |
| domain import Spring MVC、repository 实现、外部 client | 通过 `domain/gateway` 反转依赖 |
| DTO 被当作有业务行为的实体 | 创建 domain entity/value object，在 application 边界映射 |
| 同层入口对象互相调用来复用流程 | 抽取 domain service、rule、policy 或应用层协作者，保持用例入口独立 |
| entity 双向持有对象导致序列化、ORM 或递归调用问题 | 用 id 引用、单向引用、DTO 映射或 application 编排打断循环 |
| 为 CRUD 创建大量空层 | 保留布局，但避免没有行为或边界价值的抽象 |
