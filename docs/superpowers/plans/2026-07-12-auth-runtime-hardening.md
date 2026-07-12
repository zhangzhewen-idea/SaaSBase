# 认证运行链路修复计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复审查发现的认证、Redis 状态、JWT 吊销、租户上下文和生产配置缺陷，使登录、刷新、注销和受保护请求形成可验证闭环。

**架构：** Controller 只负责 HTTP 映射，Application Service 负责认证用例，Gateway 接口隔离 MySQL、Redis 和 JWT 适配器。JWT Filter 验证 token 后设置租户上下文，并在请求结束时清理；Redis 不可用时认证链路拒绝请求。

**技术栈：** Spring Boot 4.1、Spring Security、MyBatis-Plus、Redis、MySQL 8.4、Testcontainers、MockMvc。

---

## 任务 1：补齐登录、刷新、注销失败测试

**文件：**
- 修改：`src/test/java/com/saasbase/auth/application/AuthApplicationServiceTest.java`
- 创建：`src/test/java/com/saasbase/auth/adapter/AuthControllerTest.java`

- [ ] 编写失败测试，验证登录保存 refresh token、刷新重新签发 access token、注销撤销 refresh token。
- [ ] 编写失败的 MockMvc 测试，验证 `/api/v1/auth/login`、`/refresh`、`/logout` 路由存在。
- [ ] 运行对应测试确认失败原因来自缺少真实行为。

## 任务 2：实现 MySQL 用户查询和认证 Controller

**文件：**
- 修改：`src/main/java/com/saasbase/auth/infrastructure/persistence/UserCredentialMapper.java`
- 修改：`src/main/java/com/saasbase/auth/adapter/AuthController.java`
- 修改：`src/main/java/com/saasbase/auth/application/AuthApplicationService.java`

- [ ] 使用 MyBatis-Plus Mapper 查询 `tenant` 与 `iam_user`，返回用户 ID、租户 ID、密码哈希和权限集合。
- [ ] 增加登录、刷新、注销 HTTP 映射和参数校验。
- [ ] 刷新时解析 refresh token 对应的用户身份，重新签发 access token 并轮换 refresh token。

## 任务 3：实现 Redis 状态和 JWT 吊销

**文件：**
- 修改：`src/main/java/com/saasbase/auth/infrastructure/redis/RedisRefreshTokenStore.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/redis/RedisTokenRevocationStore.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/security/JwtTokenGateway.java`
- 修改：`src/main/java/com/saasbase/auth/infrastructure/security/JwtAuthenticationFilter.java`

- [ ] 使用 Redis key、TTL 和 JSON/value 记录 refresh token 会话。
- [ ] JWT 增加 `jti`，解析结果能保留 token ID。
- [ ] Filter 对 JWT 格式、签名、过期和撤销状态失败统一返回 401，并在请求结束清理上下文。

## 任务 4：接入租户上下文和生产配置

**文件：**
- 修改：`src/main/java/com/saasbase/common/tenant/TenantContextHolder.java`
- 修改：`src/main/java/com/saasbase/tenant/infrastructure/mybatis/SaasTenantLineHandler.java`
- 修改：`src/main/resources/application-prod.yml`
- 修改：`docker-compose.prod.yml`

- [ ] JWT Filter 设置 `TenantContext`，平台请求使用显式平台权限判断。
- [ ] 生产应用和 migration 使用同一组 datasource 环境变量，migration 只切换 Flyway 开关。
- [ ] 增加配置解析测试，避免生产变量名再次漂移。

## 任务 5：真实集成验证

**文件：**
- 创建：`src/test/java/com/saasbase/auth/infrastructure/AuthInfrastructureIntegrationTest.java`
- 修改：`src/test/resources/application-test.yml`

- [ ] 使用 Testcontainers MySQL 和 Redis 验证登录、刷新、注销和吊销。
- [ ] 验证跨租户查询不会返回其他租户数据。
- [ ] 运行 `mvn test` 和 `mvn -q -DskipTests package`。
- [ ] 独立审查通过后再考虑合并。
