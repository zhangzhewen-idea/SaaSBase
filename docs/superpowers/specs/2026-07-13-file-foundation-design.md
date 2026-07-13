# 文件基础能力模块设计

日期：2026-07-13  
状态：待书面审查

## 1. 目标与范围

在现有最小 `FileStorageGateway` 和本地存储适配器基础上，构建可供租户后台使用的基础文件中心，提供：

- 单文件上传。
- 文件元数据详情和分页列表。
- 按原文件名、MIME 类型和上传时间筛选。
- 认证后的文件预览与附件下载。
- 物理文件删除和元数据逻辑删除。
- 文件大小、扩展名、MIME 类型及安全预览类型的配置化限制。
- 严格租户隔离和存储失败补偿。

第一阶段仅实现本地存储，通过网关保留替换阿里云 OSS 的能力。不实现公开链接、匿名访问、分片上传、断点续传、秒传、内容去重、文件版本、目录、缩略图、病毒扫描、批量上传、Range 请求、自动重试任务或孤儿文件定时清理。

同一内容的重复上传视为不同文件，每次创建独立元数据记录和独立物理对象。

## 2. 架构与职责

文件模块继续采用 COLA light 分层：

```text
adapter -> application -> domain <- infrastructure
```

各层职责：

- `file.adapter`：提供 REST API，完成 HTTP 参数绑定、multipart 接收、响应头设置和文件流输出。
- `file.application`：编排上传、查询、下载和删除用例，执行租户校验、上传策略校验及数据库与物理存储之间的失败补偿。
- `file.domain`：定义 `FileMetadata`、文件状态、存储类型、存储结果和文件规则，不依赖 Spring、MyBatis 或本地文件系统。
- `file.domain.gateway.FileMetadataGateway`：定义元数据新增、更新、详情、分页和逻辑删除能力。
- `file.domain.gateway.FileStorageGateway`：只定义物理对象写入、读取和删除能力。
- `file.infrastructure.persistence`：实现 MyBatis 元数据持久化。
- `file.infrastructure.storage`：实现本地文件存储和配置绑定。

现有 `FileObject` 不再同时表达业务文件和存储结果。实施时应将其收缩为存储层返回值，业务文件使用独立的 `FileMetadata` 表达。外部 API 不返回 `objectKey` 或本地路径。

## 3. 数据模型

新增 `file_metadata` 表：

```text
id
tenant_id
storage_type
object_key
original_filename
content_type
extension
size
status
created_at
created_by
updated_at
updated_by
deleted
deleted_at
deleted_by
version
```

字段规则：

- `id` 使用项目统一的系统内部 ID 策略，也是对外暴露的文件标识。
- `tenant_id` 标识文件所属租户。
- `storage_type` 第一阶段固定为 `LOCAL`，为未来 OSS 适配器保留区分能力。
- `object_key` 由服务端生成，原文件名不得参与物理路径计算。
- `original_filename` 保存清洗后的用户文件名，用于展示和生成 `Content-Disposition`。
- `content_type` 保存上传校验通过后的 MIME 类型。
- `extension` 保存规范化为小写的扩展名。
- `size` 保存物理文件字节数。
- `status` 支持 `UPLOADING`、`AVAILABLE` 和 `DELETE_FAILED`。
- `deleted` 等字段实现元数据逻辑删除。
- `version` 用于乐观锁。

数据库约束与索引：

- 唯一约束：`(tenant_id, object_key)`。
- 列表主索引：`(tenant_id, deleted, created_at)`。
- MIME 类型筛选索引：`(tenant_id, deleted, content_type, created_at)`。

原文件名模糊查询第一阶段使用受租户和分页边界约束的 `LIKE` 查询，不额外引入全文索引。

## 4. 租户与访问控制

- 所有 API 都要求认证，并从可信认证上下文取得 `tenantId` 和 `userId`。
- 普通调用者不能通过请求参数指定租户。
- 元数据查询受 MyBatis 租户拦截器约束，并显式排除逻辑删除记录。
- 详情、预览、下载和删除只接收文件 `id`；应用层先按当前租户查询元数据，再用可信 `objectKey` 操作物理文件。
- 本地存储适配器校验 `objectKey` 的租户前缀必须匹配当前租户。
- 不提供普通租户跨租户访问，也不新增平台跨租户文件 API。
- 对越权访问、已删除文件和不存在文件统一返回“文件不存在”，避免泄露其他租户文件是否存在。

## 5. API 设计

租户文件 API 使用 `/api/v1/admin/files`：

```text
POST   /api/v1/admin/files
GET    /api/v1/admin/files/{id}
GET    /api/v1/admin/files
GET    /api/v1/admin/files/{id}/content?disposition=inline|attachment
DELETE /api/v1/admin/files/{id}
```

### 5.1 上传

`POST /api/v1/admin/files` 使用 `multipart/form-data`，文件字段名为 `file`。成功后返回文件元数据，排除 `objectKey`、本地路径和底层存储细节。

### 5.2 详情与列表

详情接口返回当前租户中处于 `AVAILABLE` 状态且未删除的文件。

列表接口支持：

- `filename`：原文件名模糊查询。
- `contentType`：MIME 类型精确匹配。
- `uploadedFrom`：上传时间下界，包含边界。
- `uploadedTo`：上传时间上界，包含边界。
- 项目统一分页参数，默认按 `created_at DESC, id DESC` 排序。

不接受客户端传入任意排序字段。

### 5.3 预览与下载

`disposition` 只接受 `inline` 或 `attachment`，默认值为 `attachment`。

- `attachment`：使用安全编码后的原文件名生成 `Content-Disposition`。
- `inline`：仅当文件 MIME 类型命中 `inline-content-types` 白名单时生效；否则强制返回 `attachment`。
- 响应设置准确的 `Content-Type`、`Content-Length`、`Content-Disposition` 和 `X-Content-Type-Options: nosniff`。

第一阶段不支持 Range 请求。

### 5.4 删除

删除成功返回 `204 No Content`。重复删除、越权删除和不存在文件统一按文件不存在处理。

## 6. 上传流程与一致性

上传流程：

1. 从认证上下文取得 `tenantId` 和 `userId`。
2. 校验文件非空、文件名、大小、扩展名及 MIME 类型。
3. 创建状态为 `UPLOADING` 的元数据记录并获得文件 `id`。
4. 生成包含租户前缀的 `objectKey`，通过 `FileStorageGateway` 写入本地存储。
5. 使用实际写入字节数更新元数据，并将状态改为 `AVAILABLE`。
6. 返回不含内部存储信息的文件元数据。

一致性规则：

- 数据库事务不能覆盖本地文件系统，模块使用状态和补偿实现最终一致性。
- 物理写入失败时，删除本次 `UPLOADING` 元数据；如果清理元数据也失败，必须记录包含租户和文件 ID 的错误日志。
- 物理写入成功但状态更新失败时，尽力删除已写入对象；元数据保留为 `UPLOADING`，使异常可定位。
- 第一阶段不提供后台自动恢复；失败记录通过日志和状态支持人工排查。

## 7. 删除流程与一致性

删除流程：

1. 按当前租户和文件 `id` 查询 `AVAILABLE` 或 `DELETE_FAILED` 元数据。
2. 调用 `FileStorageGateway` 删除物理文件。
3. 物理删除成功后逻辑删除元数据，并记录删除人和删除时间。
4. 物理删除失败时，将状态更新为 `DELETE_FAILED`，接口返回存储删除失败。

幂等与补偿规则：

- 本地适配器将物理文件不存在视为删除成功。
- 如果物理删除成功但元数据逻辑删除失败，下一次删除会再次执行物理删除；由于物理删除幂等，随后可完成元数据逻辑删除。
- `DELETE_FAILED` 文件不可下载或预览，但允许再次执行删除。
- 本阶段不增加定时重试或孤儿文件清理任务。

## 8. 配置与校验

配置统一放在 `saasbase.file`：

```yaml
saasbase:
  file:
    root-path: ./data/files
    max-size: 20MB
    allowed-extensions: [pdf, png, jpg, jpeg, docx, xlsx]
    allowed-content-types:
      - application/pdf
      - image/png
      - image/jpeg
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
    inline-content-types: [application/pdf, image/png, image/jpeg]
```

默认单文件上限为 `20MB`，各环境允许通过配置调整。白名单的具体默认值在实现计划中以安全、最小可用为原则列明，但生产环境不得通过空白名单表达“允许全部”。

校验规则：

- 拒绝空文件、超限文件、缺少文件名或无法识别扩展名的文件。
- 扩展名统一转为小写后匹配 `allowed-extensions`。
- 客户端声明的 MIME 类型必须匹配 `allowed-content-types`。
- 扩展名与 MIME 类型还必须符合模块内维护的合法组合，避免仅分别命中两个白名单却组合异常。
- 第一阶段不做内容嗅探、病毒扫描或文件解码验证，客户端 MIME 不作为可信安全证明。
- `inline-content-types` 使用更严格的白名单；SVG、HTML、XML、JavaScript 和其他可执行或主动内容默认禁止内联。
- 原文件名剥离路径部分和控制字符，限制长度，并安全编码后用于响应头。
- 本地存储路径经过规范化后必须仍位于配置的根目录内。

Spring multipart 层的请求大小限制应与模块 `max-size` 协调：框架限制不得小于模块限制，并将框架抛出的超限异常映射为统一业务错误。

## 9. 错误处理与日志

业务错误至少覆盖：

- 文件为空或文件名无效。
- 文件大小超限。
- 文件扩展名、MIME 类型或二者组合不允许。
- 文件不存在。
- 文件状态不可用。
- 文件存储失败。
- 文件读取失败。
- 文件删除失败。

所有错误继续使用项目统一异常响应和 HTTP 状态语义。对外响应不得包含本地路径、`objectKey`、异常堆栈或底层存储异常信息。

存储失败日志至少记录 `tenantId`、文件 `id`、`objectKey` 和操作类型，但不得记录文件内容。对文件上传和删除的管理操作审计可复用现有审计能力；若现有审计接口不足，只记录与本模块直接相关的最小事件，不扩展为完整文件行为分析。

## 10. 测试设计

### 10.1 领域与应用单元测试

- 文件大小、扩展名、MIME 类型组合和安全文件名校验。
- 上传成功及创建元数据、物理写入、状态更新各阶段失败时的补偿。
- 删除成功、物理删除失败、元数据删除失败后的重试和状态异常。
- 非安全 MIME 请求 `inline` 时强制使用附件下载。

### 10.2 本地存储测试

- 写入、读取、删除闭环。
- 服务端生成 `objectKey`，不使用原文件名构造路径。
- 路径穿越及跨租户 `objectKey` 被拒绝。
- 删除不存在的物理文件保持幂等。

### 10.3 MyBatis 集成测试

- 元数据新增、状态更新、详情、模糊查询、类型和时间筛选、逻辑删除。
- 租户拦截生效，不同租户不能读取、列出或删除彼此文件。
- `(tenant_id, object_key)` 唯一约束和乐观锁生效。

### 10.4 Controller 测试

- multipart 上传、详情、分页、预览、附件下载和删除响应。
- `Content-Type`、`Content-Length`、`Content-Disposition` 和 `nosniff` 响应头正确。
- 未认证、越权、非法输入、超限请求及存储异常映射为稳定 HTTP 状态和业务错误码。

## 11. 验收标准

- 上传、详情、分页、预览、附件下载和删除形成可运行闭环。
- 所有文件访问都从认证上下文确定租户，跨租户访问不可绕过。
- 物理路径不受用户文件名控制，路径穿越与跨租户对象访问被拒绝。
- 删除物理文件后逻辑删除元数据，失败可定位并可幂等重试。
- 配置化大小和类型限制生效，危险内容不能内联预览。
- 对外 API 不泄露内部存储信息。
- 文件模块测试、架构测试和项目全量测试通过。
