# SaaSBase

## 本地启动

```bash
docker compose -f docker-compose.local.yml up -d
mvn spring-boot:run
```

## 生产迁移

生产应用账号不具备 DDL 权限。发布时先打包 jar，再用生产 compose 构建镜像并执行独立迁移步骤。

```bash
mvn -DskipTests package
docker compose -f docker-compose.prod.yml --profile migration run --rm migration
docker compose -f docker-compose.prod.yml up -d app
```

其中 `docker compose -f docker-compose.prod.yml --profile migration run --rm migration` 的作用是：
- 只启动一次性迁移容器
- 使用 `prod` 配置连接生产数据库执行 Flyway
- 迁移完成后自动删除容器
- 不启动 Web 服务

生产环境应用启动时 `spring.flyway.enabled=false`。

## 生产发布

生产镜像由仓库根目录的 `Dockerfile` 构建，发布脚本会先生成 jar，再 `docker pull` 你指定的 Dragonwell 基础镜像并基于它打包发布镜像。

推荐直接执行仓库根目录的发布脚本：

```bash
bash scripts/publish.sh
```

应用默认通过 `8080` 对外提供服务，对外访问地址为 `http://<当前机器IP>:8080`。

Dragonwell 基础镜像地址：

```text
alibabadragonwell/dragonwell:25.0.3.0.3.9-standard-ga-anolis
```

## 说明

- `/api/v1/auth/**`：认证入口
- `/api/v1/admin/**`：租户管理入口
- `/api/v1/platform/**`：平台管理入口
- `/api/v1/open/**`：第一阶段保留路径

## 默认初始化账号

应用启动时会自动创建一组默认账号，用于本地、开发、测试和生产环境的首次登录与初始化验证。

- 平台租户编码：`platform`
- 平台管理员用户名：`platform-admin`
- 平台管理员默认密码：`Platform123!`
- 初始租户编码：`demo`
- 初始租户管理员用户名：`admin`
- 初始租户管理员默认密码：`Tenant123!`

可通过环境变量覆盖：

- `SAASBASE_BOOTSTRAP_ENABLED`
- `SAASBASE_PLATFORM_TENANT_CODE`
- `SAASBASE_PLATFORM_TENANT_NAME`
- `SAASBASE_PLATFORM_ADMIN_USERNAME`
- `SAASBASE_PLATFORM_ADMIN_DISPLAY_NAME`
- `SAASBASE_PLATFORM_ADMIN_PASSWORD`
- `SAASBASE_TENANT_CODE`
- `SAASBASE_TENANT_NAME`
- `SAASBASE_TENANT_ADMIN_USERNAME`
- `SAASBASE_TENANT_ADMIN_DISPLAY_NAME`
- `SAASBASE_TENANT_ADMIN_PASSWORD`
