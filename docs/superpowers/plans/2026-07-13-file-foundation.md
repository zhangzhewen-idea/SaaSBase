# 文件基础能力模块实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现租户私有的文件上传、元数据查询、预览、附件下载和物理删除闭环，并以状态和补偿处理数据库与本地文件系统的一致性。

**架构：** 文件模块保持 COLA light 分层，应用服务编排 `FileMetadataGateway` 与 `FileStorageGateway`。MyBatis 保存文件元数据，本地适配器只管理物理对象；所有 API 从 `TenantContextHolder` 获取租户和用户，不接收租户参数。

**技术栈：** Java 25、Spring Boot 4.1、Spring MVC、Spring Security、MyBatis/MyBatis-Plus、Flyway、MySQL 8.4、JUnit 5、AssertJ、Mockito、Testcontainers

---

## 文件结构

### 创建

- `src/main/resources/db/migration/V3__extend_file_metadata.sql`：把既有 `file_object` 演进为设计要求的文件元数据表并补充状态、扩展名和索引。
- `src/main/java/com/saasbase/file/domain/FileStatus.java`：文件生命周期状态。
- `src/main/java/com/saasbase/file/domain/StoredObject.java`：物理存储写入结果。
- `src/main/java/com/saasbase/file/domain/FileMetadata.java`：业务文件元数据聚合。
- `src/main/java/com/saasbase/file/domain/FileQuery.java`：受控分页筛选条件。
- `src/main/java/com/saasbase/file/domain/gateway/FileMetadataGateway.java`：元数据端口。
- `src/main/java/com/saasbase/file/application/FilePolicy.java`：文件名、大小、扩展名、MIME 组合和内联策略。
- `src/main/java/com/saasbase/file/application/FileApplicationService.java`：上传、查询、读取和删除用例编排及补偿。
- `src/main/java/com/saasbase/file/application/dto/FileResponse.java`：不暴露存储细节的响应 DTO。
- `src/main/java/com/saasbase/file/application/dto/FileContent.java`：下载流及可信响应元数据。
- `src/main/java/com/saasbase/file/infrastructure/persistence/FileMetadataRecord.java`：MyBatis 持久化对象。
- `src/main/java/com/saasbase/file/infrastructure/persistence/FileMetadataMapper.java`：MyBatis Mapper 接口。
- `src/main/java/com/saasbase/file/infrastructure/persistence/FileMetadataPersistenceAdapter.java`：元数据端口实现。
- `src/main/resources/mapper/file/FileMetadataMapper.xml`：分页、状态更新和逻辑删除 SQL。
- `src/main/resources/db/migration/V4__seed_file_permissions.sql`：新增文件读取、上传和删除权限点。
- `src/main/java/com/saasbase/file/adapter/AdminFileController.java`：租户文件 REST API。
- `src/test/java/com/saasbase/file/application/FilePolicyTest.java`：上传与预览策略单元测试。
- `src/test/java/com/saasbase/file/application/FileApplicationServiceTest.java`：用例和补偿单元测试。
- `src/test/java/com/saasbase/file/infrastructure/persistence/FileMetadataPersistenceAdapterIntegrationTest.java`：MySQL 与租户隔离集成测试。
- `src/test/java/com/saasbase/file/adapter/AdminFileControllerTest.java`：HTTP 契约测试。

### 修改

- `src/main/java/com/saasbase/file/domain/gateway/FileStorageGateway.java`：改用 `StoredObject`，增加幂等物理删除。
- `src/main/java/com/saasbase/file/infrastructure/storage/LocalFileStorageGateway.java`：强化租户前缀、路径安全和删除语义。
- `src/main/java/com/saasbase/file/infrastructure/storage/FileStorageProperties.java`：增加大小与类型白名单配置。
- `src/main/java/com/saasbase/common/error/ErrorCode.java`：增加稳定文件业务错误码。
- `src/main/java/com/saasbase/common/error/GlobalExceptionHandler.java`：映射 multipart 超限错误。
- `src/main/resources/application.yml`：加入文件策略和 multipart 限制。
- `src/test/java/com/saasbase/file/infrastructure/storage/LocalFileStorageGatewayTest.java`：覆盖完整物理存储闭环与攻击路径。
- `src/test/java/com/saasbase/common/error/GlobalExceptionHandlerTest.java`：覆盖上传超限错误响应。
- `src/test/java/com/saasbase/architecture/ColaArchitectureTest.java`：确认新增包仍满足依赖方向。

### 删除或替换

- `src/main/java/com/saasbase/file/domain/FileObject.java`：由职责单一的 `StoredObject` 和 `FileMetadata` 替代。删除属于红线操作，执行该步骤前必须取得用户明确确认；未获确认时先保留该文件，待全部引用迁移后再处理。

## 任务 1：演进数据库文件元数据结构

**文件：**
- 创建：`src/main/resources/db/migration/V3__extend_file_metadata.sql`
- 测试：`src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java`

- [ ] **步骤 1：在 Flyway 测试中加入新结构断言**

在现有迁移测试中增加 JDBC 元数据断言，确认 `file_metadata` 包含 `extension`、`status`、`deleted_at`、`version`，并确认索引 `idx_file_metadata_tenant_type_time` 存在：

```java
assertThat(columnNames(connection, "file_metadata"))
        .contains("extension", "status", "deleted_at", "version");
assertThat(indexNames(connection, "file_metadata"))
        .contains("idx_file_metadata_tenant_type_time");
```

- [ ] **步骤 2：运行迁移测试并确认失败**

运行：`mvn -q -Dtest=FlywayMigrationTest test`

预期：FAIL，原因是 `file_metadata` 或新增字段不存在。

- [ ] **步骤 3：编写增量迁移**

创建迁移，禁止修改已发布的 `V1__init_core_schema.sql`：

```sql
RENAME TABLE file_object TO file_metadata;

ALTER TABLE file_metadata
    CHANGE COLUMN filename original_filename VARCHAR(255) NOT NULL COMMENT '清洗后的原始文件名',
    ADD COLUMN extension VARCHAR(32) NOT NULL DEFAULT '' AFTER content_type,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' AFTER size,
    MODIFY COLUMN storage_type VARCHAR(32) NOT NULL COMMENT '存储类型：LOCAL',
    DROP INDEX uk_file_object_tenant_key,
    DROP INDEX idx_file_object_tenant_time,
    ADD UNIQUE KEY uk_file_metadata_tenant_key (tenant_id, object_key),
    ADD KEY idx_file_metadata_tenant_time (tenant_id, deleted, created_at),
    ADD KEY idx_file_metadata_tenant_type_time (tenant_id, deleted, content_type, created_at);
```

- [ ] **步骤 4：运行迁移测试并确认通过**

运行：`mvn -q -Dtest=FlywayMigrationTest test`

预期：PASS，Testcontainers MySQL 完成 V1、V2、V3 迁移。

- [ ] **步骤 5：Commit**

提交前展示迁移文件和测试变更摘要，然后运行：

```bash
git add src/main/resources/db/migration/V3__extend_file_metadata.sql src/test/java/com/saasbase/infrastructure/FlywayMigrationTest.java
git commit -m "扩展文件元数据表"
```

## 任务 2：定义文件领域模型和元数据端口

**文件：**
- 创建：`src/main/java/com/saasbase/file/domain/FileStatus.java`
- 创建：`src/main/java/com/saasbase/file/domain/StoredObject.java`
- 创建：`src/main/java/com/saasbase/file/domain/FileMetadata.java`
- 创建：`src/main/java/com/saasbase/file/domain/FileQuery.java`
- 创建：`src/main/java/com/saasbase/file/domain/gateway/FileMetadataGateway.java`
- 修改：`src/main/java/com/saasbase/file/domain/gateway/FileStorageGateway.java`
- 测试：`src/test/java/com/saasbase/file/application/FileApplicationServiceTest.java`

- [ ] **步骤 1：编写引用目标接口的编译失败测试**

```java
FileMetadataGateway metadataGateway = mock(FileMetadataGateway.class);
FileStorageGateway storageGateway = mock(FileStorageGateway.class);
assertThat(metadataGateway).isNotNull();
assertThat(storageGateway).isNotNull();
```

- [ ] **步骤 2：运行测试并确认编译失败**

运行：`mvn -q -Dtest=FileApplicationServiceTest test`

预期：FAIL，报错包含 `cannot find symbol` 和 `FileMetadataGateway`。

- [ ] **步骤 3：定义状态和值对象**

```java
public enum FileStatus { UPLOADING, AVAILABLE, DELETE_FAILED }

public record StoredObject(String storageType, String objectKey, long size) {}

public record FileQuery(
        String filename,
        String contentType,
        Instant uploadedFrom,
        Instant uploadedTo,
        long pageNo,
        long pageSize) {}
```

`FileMetadata` 使用以下字段并提供 `uploading`、`markAvailable`、`markDeleteFailed` 方法：

```java
Long id, Long tenantId, String storageType, String objectKey,
String originalFilename, String contentType, String extension,
long size, FileStatus status, Instant createdAt, Long createdBy,
long version
```

- [ ] **步骤 4：定义两个网关的精确签名**

```java
public interface FileStorageGateway {
    StoredObject store(Long tenantId, InputStream inputStream);
    InputStream load(Long tenantId, String objectKey);
    void delete(Long tenantId, String objectKey);
}

public interface FileMetadataGateway {
    FileMetadata createUploading(FileMetadata metadata);
    void markAvailable(Long id, String storageType, String objectKey, long size, long version);
    void markDeleteFailed(Long id, long version);
    Optional<FileMetadata> findAvailableById(Long id);
    Optional<FileMetadata> findDeletableById(Long id);
    PageResponse<FileMetadata> search(FileQuery query);
    void logicallyDelete(Long id, Long deletedBy, long version);
    void removeUploading(Long id);
}
```

- [ ] **步骤 5：运行编译和测试**

运行：`mvn -q -DskipTests compile && mvn -q -Dtest=FileApplicationServiceTest test`

预期：编译通过；测试通过当前最小接口断言。

- [ ] **步骤 6：Commit**

提交前展示领域模型和网关摘要，然后运行：

```bash
git add src/main/java/com/saasbase/file/domain src/test/java/com/saasbase/file/application/FileApplicationServiceTest.java
git commit -m "定义文件领域模型"
```

## 任务 3：实现配置化文件策略

**文件：**
- 修改：`src/main/java/com/saasbase/file/infrastructure/storage/FileStorageProperties.java`
- 创建：`src/main/java/com/saasbase/file/application/FilePolicy.java`
- 修改：`src/main/java/com/saasbase/common/error/ErrorCode.java`
- 创建：`src/test/java/com/saasbase/file/application/FilePolicyTest.java`
- 修改：`src/main/resources/application.yml`

- [ ] **步骤 1：编写失败的策略测试**

覆盖空文件、20MB 边界、扩展名大小写、非法 MIME 组合、路径型文件名清洗以及 HTML 不可内联：

```java
assertThat(policy.validate("REPORT.PDF", "application/pdf", 1024))
        .isEqualTo(new ValidatedFile("REPORT.PDF", "pdf", "application/pdf"));
assertThatThrownBy(() -> policy.validate("x.pdf", "image/png", 10))
        .isInstanceOf(BizException.class);
assertThat(policy.forceAttachment("text/html")).isTrue();
```

- [ ] **步骤 2：运行策略测试并确认失败**

运行：`mvn -q -Dtest=FilePolicyTest test`

预期：FAIL，报错包含 `cannot find symbol class FilePolicy`。

- [ ] **步骤 3：扩展配置和错误码**

`FileStorageProperties` 精确字段：

```java
Path rootPath,
DataSize maxSize,
Set<String> allowedExtensions,
Set<String> allowedContentTypes,
Set<String> inlineContentTypes,
Map<String, Set<String>> contentTypeByExtension
```

向 `ErrorCode` 增加：

```java
FILE_INVALID(HttpStatus.BAD_REQUEST, "文件无效"),
FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "文件大小超出限制"),
FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件类型不允许"),
FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "文件不存在"),
FILE_STATE_INVALID(HttpStatus.CONFLICT, "文件状态不可用"),
FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件存储失败"),
FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败"),
FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件删除失败"),
```

- [ ] **步骤 4：实现最小策略**

`validate(filename, contentType, size)` 必须：取 `Paths.get(filename).getFileName()`、移除控制字符、限制为 255 字符、提取小写扩展名，同时校验大小、两个白名单及 `contentTypeByExtension` 组合。返回：

```java
public record ValidatedFile(String filename, String extension, String contentType) {}
```

- [ ] **步骤 5：加入明确默认配置**

在 `application.yml` 配置 20MB、`pdf/png/jpg/jpeg/docx/xlsx`、对应 MIME 映射以及仅 `application/pdf`、`image/png`、`image/jpeg` 可内联；同步设置：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 21MB
```

- [ ] **步骤 6：运行策略测试**

运行：`mvn -q -Dtest=FilePolicyTest test`

预期：PASS。

- [ ] **步骤 7：Commit**

提交前展示配置、策略和错误码摘要，然后运行：

```bash
git add src/main/java/com/saasbase/file/application/FilePolicy.java src/main/java/com/saasbase/file/infrastructure/storage/FileStorageProperties.java src/main/java/com/saasbase/common/error/ErrorCode.java src/main/resources/application.yml src/test/java/com/saasbase/file/application/FilePolicyTest.java
git commit -m "添加文件上传策略"
```

## 任务 4：强化本地物理存储适配器

**文件：**
- 修改：`src/main/java/com/saasbase/file/infrastructure/storage/LocalFileStorageGateway.java`
- 修改：`src/test/java/com/saasbase/file/infrastructure/storage/LocalFileStorageGatewayTest.java`

- [ ] **步骤 1：补充失败测试**

新增写入读取一致、跨租户加载拒绝、`../` 路径穿越拒绝、删除存在文件、重复删除成功五组测试：

```java
StoredObject stored = gateway.store(2001L, bytes("hello"));
assertThat(stored.objectKey()).startsWith("2001/");
assertThat(new String(gateway.load(2001L, stored.objectKey()).readAllBytes())).isEqualTo("hello");
assertThatThrownBy(() -> gateway.load(2002L, stored.objectKey()))
        .isInstanceOf(IllegalArgumentException.class);
gateway.delete(2001L, stored.objectKey());
gateway.delete(2001L, stored.objectKey());
```

- [ ] **步骤 2：运行测试并确认失败**

运行：`mvn -q -Dtest=LocalFileStorageGatewayTest test`

预期：FAIL，原因是网关新签名和 `delete` 尚未实现。

- [ ] **步骤 3：实现安全路径解析和幂等删除**

集中使用私有方法：

```java
private Path resolveOwnedPath(Long tenantId, String objectKey) {
    String prefix = tenantId + "/";
    if (!objectKey.startsWith(prefix)) throw new IllegalArgumentException("invalid object key");
    Path target = rootPath.toAbsolutePath().normalize().resolve(objectKey).normalize();
    if (!target.startsWith(rootPath.toAbsolutePath().normalize()))
        throw new IllegalArgumentException("invalid object key");
    return target;
}
```

`delete` 使用 `Files.deleteIfExists`；`store` 返回实际复制字节数，所有 I/O 异常分别包装为存储、读取或删除内部异常，由应用层映射业务错误。

- [ ] **步骤 4：运行本地存储测试**

运行：`mvn -q -Dtest=LocalFileStorageGatewayTest test`

预期：PASS。

- [ ] **步骤 5：Commit**

提交前展示网关和安全测试摘要，然后运行：

```bash
git add src/main/java/com/saasbase/file/infrastructure/storage/LocalFileStorageGateway.java src/main/java/com/saasbase/file/domain/gateway/FileStorageGateway.java src/test/java/com/saasbase/file/infrastructure/storage/LocalFileStorageGatewayTest.java
git commit -m "强化本地文件存储"
```

## 任务 5：实现 MyBatis 元数据适配器

**文件：**
- 创建：`src/main/java/com/saasbase/file/infrastructure/persistence/FileMetadataRecord.java`
- 创建：`src/main/java/com/saasbase/file/infrastructure/persistence/FileMetadataMapper.java`
- 创建：`src/main/java/com/saasbase/file/infrastructure/persistence/FileMetadataPersistenceAdapter.java`
- 创建：`src/main/resources/mapper/file/FileMetadataMapper.xml`
- 创建：`src/test/java/com/saasbase/file/infrastructure/persistence/FileMetadataPersistenceAdapterIntegrationTest.java`

- [ ] **步骤 1：编写 MySQL 集成失败测试**

使用现有 Testcontainers 配置，设置 `TenantContextHolder` 后验证创建、状态更新、详情、文件名筛选、MIME/时间筛选、乐观锁删除和租户隔离；每个测试后调用 `TenantContextHolder.clear()`。

```java
TenantContextHolder.set(new TenantContext(2001L, 3001L, false));
FileMetadata created = gateway.createUploading(uploading(1001L, 2001L));
gateway.markAvailable(created.id(), "LOCAL", "2001/key", 5, created.version());
assertThat(gateway.findAvailableById(1001L)).isPresent();
```

- [ ] **步骤 2：运行集成测试并确认失败**

运行：`mvn -q -Dtest=FileMetadataPersistenceAdapterIntegrationTest test`

预期：FAIL，报错包含 `FileMetadataPersistenceAdapter` 不存在。

- [ ] **步骤 3：实现 Record、Mapper 和 XML**

Mapper 方法严格对应网关操作；XML 的分页条件只允许固定字段，文件名使用 `LIKE CONCAT('%', #{query.filename}, '%')`，排序固定为 `created_at DESC, id DESC`，更新语句必须带 `id`、`version`、预期状态和 `deleted = 0`。

- [ ] **步骤 4：实现适配器映射和并发冲突处理**

所有更新若影响行数不是 1，抛出 `BizException(ErrorCode.FILE_STATE_INVALID)`；`search` 将 `pageNo/pageSize` 转为有界 offset，要求 `pageNo >= 1` 且 `1 <= pageSize <= 100`。

- [ ] **步骤 5：运行集成测试**

运行：`mvn -q -Dtest=FileMetadataPersistenceAdapterIntegrationTest test`

预期：PASS，且跨租户详情为空、列表无其他租户数据。

- [ ] **步骤 6：Commit**

提交前展示持久化文件和测试摘要，然后运行：

```bash
git add src/main/java/com/saasbase/file/infrastructure/persistence src/main/resources/mapper/file src/test/java/com/saasbase/file/infrastructure/persistence
git commit -m "实现文件元数据持久化"
```

## 任务 6：实现应用服务与失败补偿

**文件：**
- 创建：`src/main/java/com/saasbase/file/application/FileApplicationService.java`
- 创建：`src/main/java/com/saasbase/file/application/dto/FileResponse.java`
- 创建：`src/main/java/com/saasbase/file/application/dto/FileContent.java`
- 修改：`src/test/java/com/saasbase/file/application/FileApplicationServiceTest.java`

- [ ] **步骤 1：编写上传流程失败测试**

覆盖成功、物理写入失败清理 `UPLOADING`、状态更新失败删除已写对象：

```java
when(storage.store(eq(2001L), any())).thenReturn(new StoredObject("LOCAL", "2001/key", 5));
FileResponse response = service.upload("a.pdf", "application/pdf", 5, bytes("hello"));
verify(metadata).markAvailable(response.id(), "LOCAL", "2001/key", 5, 0);
```

- [ ] **步骤 2：编写读取和删除失败测试**

覆盖只读取 `AVAILABLE`、内联降级附件、物理删除失败标记 `DELETE_FAILED`、物理删除成功后逻辑删除、再次删除缺失对象仍可完成。

- [ ] **步骤 3：运行应用测试并确认失败**

运行：`mvn -q -Dtest=FileApplicationServiceTest test`

预期：FAIL，报错包含 `cannot find symbol class FileApplicationService`。

- [ ] **步骤 4：实现 DTO 与应用服务**

公开方法固定为：

```java
FileResponse upload(String filename, String contentType, long declaredSize, InputStream input);
FileResponse detail(Long id);
PageResponse<FileResponse> search(FileQuery query);
FileContent content(Long id, ContentDisposition disposition);
void delete(Long id);
```

`FileContent` 字段：`InputStream inputStream, String filename, String contentType, long size, ContentDisposition disposition`。所有方法使用 `TenantContextHolder.require()`；上传和删除按规格执行补偿，底层异常映射为对应文件错误码并记录 `tenantId/id/objectKey`，日志不得记录内容。

- [ ] **步骤 5：运行应用测试**

运行：`mvn -q -Dtest=FileApplicationServiceTest test`

预期：PASS，并验证所有补偿交互次数。

- [ ] **步骤 6：Commit**

提交前展示应用用例和补偿测试摘要，然后运行：

```bash
git add src/main/java/com/saasbase/file/application src/test/java/com/saasbase/file/application
git commit -m "实现文件应用服务"
```

## 任务 7：实现文件 REST API 和统一异常响应

**文件：**
- 创建：`src/main/java/com/saasbase/file/adapter/AdminFileController.java`
- 创建：`src/main/resources/db/migration/V4__seed_file_permissions.sql`
- 修改：`src/main/java/com/saasbase/common/error/GlobalExceptionHandler.java`
- 创建：`src/test/java/com/saasbase/file/adapter/AdminFileControllerTest.java`
- 修改：`src/test/java/com/saasbase/common/error/GlobalExceptionHandlerTest.java`

- [ ] **步骤 1：编写 Controller 失败测试**

使用 `MockMvc` 覆盖五个端点、认证、分页参数、响应 DTO、`204` 删除，以及下载响应头：

```java
mockMvc.perform(get("/api/v1/admin/files/1001/content")
        .param("disposition", "inline")
        .with(jwt()))
    .andExpect(status().isOk())
    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
    .andExpect(header().string("Content-Disposition", startsWith("inline;")));
```

- [ ] **步骤 2：编写 multipart 超限异常失败测试**

直接调用异常处理器或使用 MockMvc 抛出 `MaxUploadSizeExceededException`，断言 HTTP `413` 和 `FILE_SIZE_EXCEEDED`。

- [ ] **步骤 3：运行 HTTP 测试并确认失败**

运行：`mvn -q -Dtest=AdminFileControllerTest,GlobalExceptionHandlerTest test`

预期：FAIL，原因是控制器和异常映射尚不存在。

- [ ] **步骤 4：实现控制器**

控制器使用 `/api/v1/admin/files`，上传字段固定为 `file`；详情与列表返回项目统一 `ApiResponse`，分页体使用 `PageResponse`；内容接口使用 `InputStreamResource`，设置 `Content-Type`、`Content-Length`、安全编码的 `Content-Disposition` 和 `nosniff`；删除返回 `ResponseEntity.noContent()`。

所有端点使用文件权限：读取类 `@PreAuthorize("hasAuthority('file:file:read')")`，上传 `file:file:create`，删除 `file:file:delete`。创建 `V4__seed_file_permissions.sql`，使用项目统一 ID 生成方式写入三个 `iam_permission` 权限点；迁移 SQL 使用显式权限码和名称，不修改历史迁移：

```sql
INSERT INTO iam_permission (id, permission_code, permission_name, permission_type, created_at)
VALUES
    (1000000000000000101, 'file:file:read', '文件读取', 'API', CURRENT_TIMESTAMP(6)),
    (1000000000000000102, 'file:file:create', '文件上传', 'API', CURRENT_TIMESTAMP(6)),
    (1000000000000000103, 'file:file:delete', '文件删除', 'API', CURRENT_TIMESTAMP(6));
```

- [ ] **步骤 5：实现超限异常映射**

在 `GlobalExceptionHandler` 增加 `@ExceptionHandler(MaxUploadSizeExceededException.class)`，返回 `FILE_SIZE_EXCEEDED` 的统一错误体和 HTTP `413`。

- [ ] **步骤 6：运行 HTTP 测试**

运行：`mvn -q -Dtest=AdminFileControllerTest,GlobalExceptionHandlerTest test`

预期：PASS。

- [ ] **步骤 7：Commit**

提交前展示端点、权限和异常映射摘要，然后运行：

```bash
git add src/main/java/com/saasbase/file/adapter src/main/java/com/saasbase/common/error/GlobalExceptionHandler.java src/test/java/com/saasbase/file/adapter src/test/java/com/saasbase/common/error/GlobalExceptionHandlerTest.java src/main/resources/db/migration
git commit -m "提供文件管理接口"
```

## 任务 8：清理旧模型并完成架构与全量验证

**文件：**
- 删除：`src/main/java/com/saasbase/file/domain/FileObject.java`（必须先取得用户明确确认）
- 修改：`src/test/java/com/saasbase/architecture/ColaArchitectureTest.java`
- 检查：`src/main/java/com/saasbase/file/**`
- 检查：`src/test/java/com/saasbase/file/**`

- [ ] **步骤 1：搜索旧模型引用**

运行：`rg -n 'FileObject|file_object' src/main src/test -g '!target/**'`

预期：除迁移文件中历史表名外，没有 Java 代码引用 `FileObject`。

- [ ] **步骤 2：请求删除确认并处理旧文件**

向用户展示：旧 `FileObject.java` 已由 `StoredObject` 与 `FileMetadata` 完全替代，删除不影响调用方。仅在用户明确同意后删除；若未同意，则保留该无引用文件且不声称清理完成。

- [ ] **步骤 3：补充架构测试**

确保规则覆盖 `file.adapter` 不依赖 `file.infrastructure`、`file.application` 不依赖 MyBatis/存储实现、`file.domain` 不依赖外层包。运行：

`mvn -q -Dtest=ColaArchitectureTest test`

预期：PASS。

- [ ] **步骤 4：运行文件模块测试**

运行：

```bash
mvn -q -Dtest='com.saasbase.file.**' test
```

预期：PASS，文件策略、应用补偿、本地存储、MyBatis 和 Controller 测试全部通过。

- [ ] **步骤 5：运行完整验证**

运行：`mvn -q test`

预期：PASS，无失败和错误。

- [ ] **步骤 6：检查工作区并展示最终提交摘要**

运行：`git status --short && git diff --check && git diff --stat`

只汇总本计划产生的变更，明确排除执行前已经存在的用户修改。

- [ ] **步骤 7：Commit**

取得删除确认后：

```bash
git add src/main/java/com/saasbase/file/domain/FileObject.java src/test/java/com/saasbase/architecture/ColaArchitectureTest.java
git commit -m "完成文件模块验证"
```

如果用户未批准删除，只提交架构测试：

```bash
git add src/test/java/com/saasbase/architecture/ColaArchitectureTest.java
git commit -m "补充文件架构验证"
```

## 自检清单

- [ ] 规格覆盖度：计划覆盖本地存储、元数据、上传、详情、分页筛选、预览、附件下载、物理删除、逻辑删除、租户隔离、白名单、错误码、失败补偿和全量验证。
- [ ] 范围控制：计划不引入 OSS、公开 URL、Range、分片、秒传、去重、版本、目录、缩略图、病毒扫描、批量上传或后台清理任务。
- [ ] 迁移安全：不修改已发布的 V1/V2，数据库演进使用新的 Flyway 迁移。
- [ ] 类型一致性：`FileMetadata`、`StoredObject`、`FileQuery`、`FileMetadataGateway` 和 `FileStorageGateway` 的签名在后续任务保持一致。
- [ ] 安全：API 不接收 `tenantId`，不返回 `objectKey`，跨租户与路径穿越测试均有覆盖。
- [ ] 红线：删除旧 `FileObject.java` 前必须单独取得用户确认；每次 commit 前必须先展示变更摘要。
