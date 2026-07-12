# SaaSBase

## 本地启动

```bash
docker compose -f docker-compose.local.yml up -d
mvn spring-boot:run
```

## 生产迁移

生产应用账号不具备 DDL 权限。发布时先运行独立迁移步骤：

```bash
docker compose -f docker-compose.prod.yml --profile migration run --rm migration
docker compose -f docker-compose.prod.yml up -d app
```

生产环境应用启动时 `spring.flyway.enabled=false`。

## 说明

- `/api/v1/auth/**`：认证入口
- `/api/v1/admin/**`：租户管理入口
- `/api/v1/platform/**`：平台管理入口
- `/api/v1/open/**`：第一阶段保留路径
