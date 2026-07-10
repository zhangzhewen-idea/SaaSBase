# SaaSBase

## 开发环境启动

默认 profile 是 `dev`，连接开发库 `saasbase_dev`。

```bash
export SAASBASE_DEV_DATASOURCE_PASSWORD='<开发库密码>'
mvn spring-boot:run
```

## 本地 Docker 依赖启动

`local` profile 连接本机 Docker MySQL 的 `saasbase` 库。

```bash
docker compose -f docker-compose.local.yml up -d
export SAASBASE_LOCAL_DATASOURCE_PASSWORD='<本地库密码>'
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 测试环境启动

运行期测试环境连接测试库 `saasbase_test`。

```bash
export SAASBASE_TEST_DATASOURCE_PASSWORD='<测试库密码>'
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

`src/test/resources/application-test.yml` 仍用于自动化测试，使用 Testcontainers 临时 MySQL，不连接共享测试库。

## 生产迁移

生产应用账号不具备 DDL 权限。发布时先运行独立迁移步骤：

```bash
export SAASBASE_DATASOURCE_URL='jdbc:mysql://192.168.50.6:3306/saasbase_prod?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export SAASBASE_DATASOURCE_USERNAME='root'
export SAASBASE_DATASOURCE_PASSWORD='<生产库密码>'
docker compose -f docker-compose.prod.yml --profile migration run --rm migration
docker compose -f docker-compose.prod.yml up -d app
```

生产环境应用启动时 `spring.flyway.enabled=false`。

## 说明

- `/api/v1/auth/**`：认证入口
- `/api/v1/admin/**`：租户管理入口
- `/api/v1/platform/**`：平台管理入口
- `/api/v1/open/**`：第一阶段保留路径
