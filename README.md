# Cloud Coding Agent (Spring Boot 3 + Copilot SDK)

基于 **Spring Boot 3** 与 **GitHub Copilot SDK for Java** 重写的云端编码 Agent。参考原有 `cloud-coding-agent` 工程，提供工作区管理、会话、流式对话、工具执行、权限审批、文件浏览和 WebSocket 实时事件。

## 功能特性

- 多工作区：在受控的 `workspace-root` 下创建/切换工作区，路径越界保护
- 会话管理：创建、重命名、置顶、归档、删除，自动生成会话标题
- 流式对话：Markdown 渲染、推理过程折叠、输入/输出 Token 统计
- 工具调用：展示 Copilot 工具名称、参数、执行进度和结果
- 权限审批：按会话选择“每次确认 / 自动批准 / 完全访问”，审批请求持久化到 MySQL
- 文件面板：懒加载目录树、文本文件预览
- 实时通信：WebSocket 推送消息增量、工具状态、权限请求和会话变更
- 访问控制：可选 API Key（HTTP Header / WebSocket Query）
- 持久化：MySQL 8 + MyBatis + [mybatis-mapper](https://github.com/mybatis-mapper/mapper)

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 语言 / 框架 | Java 21、Spring Boot 3.5 |
| Web / 实时 | Spring MVC、Spring WebSocket |
| 存储 | MySQL 8、MyBatis Spring Boot Starter、io.mybatis:mybatis-mapper |
| Copilot | `com.microsoft:copilot-sdk:2.0.1`、`copilot-sdk-runtime:2.0.1` |
| 前端 | 原生 HTML/CSS/JS、marked、DOMPurify（本地 vendored） |
| 构建 / 部署 | Maven、Docker、Docker Compose |

## 目录结构

```text
cloud-coding-agent-spring
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── src/main/java/com/cca/spring
│   ├── config/          # 配置属性、WebSocket 配置
│   ├── domain/          # Workspace / Thread / Message / Event / Permission 实体
│   ├── mapper/          # mybatis-mapper 接口
│   ├── service/         # 工作区、会话、时间线、Copilot 会话与事件分发
│   └── web/             # REST Controller、DTO、鉴权过滤器、异常处理
├── src/main/resources
│   ├── application.yml
│   ├── schema.sql       # MySQL 建表脚本
│   ├── schema-h2.sql    # 测试用 H2 建表脚本
│   └── static/          # 单页前端
└── src/test             # H2 + Spring Boot 集成测试
```

## 快速开始（本地）

### 1. 准备 MySQL

默认使用外部 MySQL 实例 `192.168.100.231`，新库名为 `cgnew`：

```sql
CREATE DATABASE cgnew DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'cgnew'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON cgnew.* TO 'cgnew'@'%';
FLUSH PRIVILEGES;
```

应用启动时会自动执行 `schema.sql`（`CREATE TABLE IF NOT EXISTS`），也可以手动执行。

> 注意：新版数据表结构与旧版 `cloud-coding-agent` 不兼容（旧版使用 `threads(id, data JSON)` / `projects`，新版为多列结构）。请使用独立的新库 `cgnew`，不要与旧库混用。

### 2. 配置 GitHub Copilot

需要具有 GitHub Copilot 访问权限的 Token：

```bash
export COPILOT_GITHUB_TOKEN=github_pat_xxx
```

> Copilot SDK 运行时需要访问 GitHub Copilot 服务。若未配置 Token，应用仍可启动，但发送消息时会返回 Copilot 不可用错误。

### 3. 启动

要求 JDK 21+、Maven 3.9+。

```bash
mvn spring-boot:run
# 或
mvn clean package -DskipTests
java -jar target/cloud-coding-agent-spring-1.0.0.jar
```

打开 <http://localhost:8787>。

### 4. 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://192.168.100.231:3306/cgnew?...` | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `cgnew` | 数据库用户 |
| `SPRING_DATASOURCE_PASSWORD` | `123456` | 数据库密码 |
| `SERVER_PORT` | `8787` | HTTP 端口 |
| `CCA_DATA_DIR` | `./data` | Copilot 运行时数据目录 |
| `CCA_WORKSPACE_ROOT` | `./workspace` | 工作区根目录（所有工作区必须位于其下） |
| `CCA_BOOTSTRAP_DEFAULT_WORKSPACE` | `true` | 首次启动是否创建默认工作区 |
| `CCA_COPILOT_ENABLED` | `true` | 是否启用 Copilot |
| `CCA_COPILOT_MODEL` | `gpt-5` | 默认模型 |
| `COPILOT_GITHUB_TOKEN` | 空 | GitHub Copilot Token |
| `CCA_SECURITY_ENABLED` | `false` | 是否启用 API Key 访问控制 |
| `CCA_API_KEY` | 空 | API Key（启用鉴权时必填） |

## Docker Compose 部署

使用外部 MySQL（默认 `192.168.100.231:3306/cgnew`），Compose 中不再内置数据库服务：

```bash
cp .env.example .env
# 编辑 .env，至少填写 COPILOT_GITHUB_TOKEN；数据库账号密码与默认值不同时一并修改

docker compose up -d --build
```

- 应用：<http://localhost:8787>
- MySQL：`192.168.100.231:3306/cgnew`（用户 `cgnew`，可通过 `SPRING_DATASOURCE_*` 覆盖）
- 数据卷：`app-data`、`app-workspace`

若希望使用容器内置 MySQL，可参考旧版方案自行添加 `mysql:8.4` 服务并把 `SPRING_DATASOURCE_URL` 指向 `mysql:3306`。

生产环境建议开启鉴权：

```bash
CCA_SECURITY_ENABLED=true
CCA_API_KEY=请替换为强随机字符串
```

前端会提示输入 API Key，并保存到浏览器 `localStorage`。也可以直接带请求头：

```http
X-API-Key: <your-key>
```

WebSocket 使用：

```text
ws://host/ws?token=<your-key>
```

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| GET | `/api/config` | 前端启动配置（公开） |
| GET | `/api/models` | Copilot 模型列表 |
| GET / POST | `/api/workspaces` | 工作区列表 / 创建 |
| PATCH / DELETE | `/api/workspaces/{id}` | 更新 / 删除工作区 |
| GET | `/api/workspaces/{id}/files?path=` | 目录列表 |
| GET | `/api/workspaces/{id}/file?path=` | 读取文本文件 |
| PUT | `/api/workspaces/{id}/file` | 写入文件 |
| GET / POST | `/api/workspaces/{workspaceId}/threads` | 会话列表 / 创建 |
| GET / PATCH / DELETE | `/api/threads/{id}` | 会话详情 / 更新 / 删除 |
| GET | `/api/threads/{id}/timeline` | 消息 + 工具活动 + 待审批 |
| POST | `/api/threads/{id}/messages` | 发送用户消息 |
| POST | `/api/threads/{id}/abort` | 中止当前回合 |
| GET | `/api/threads/{id}/permissions` | 待审批权限列表 |
| POST | `/api/permissions/{id}/resolve` | 批准 / 拒绝权限 |

## WebSocket 事件

客户端连接 `/ws` 后发送：

```json
{ "type": "subscribe", "threadId": "..." }
{ "type": "unsubscribe", "threadId": "..." }
{ "type": "ping" }
```

服务端事件：

| 类型 | 说明 |
| --- | --- |
| `connection` | 连接成功 |
| `pong` | 心跳响应 |
| `thread.snapshot` | 订阅后的完整时间线 |
| `thread.created` / `thread.updated` / `thread.deleted` | 会话变更 |
| `thread.title` | 会话标题变更 |
| `turn.started` / `turn.completed` | 回合开始 / 结束 |
| `user.message` | 用户消息 |
| `message.started` / `message.delta` / `message.completed` | 助手消息流 |
| `message.reasoning` / `message.reasoning_delta` | 推理过程 |
| `tool.started` / `tool.progress` / `tool.completed` | 工具执行 |
| `permission.requested` / `permission.resolved` | 权限审批 |
| `thread.error` / `thread.warning` | 错误 / 警告 |
| `usage.updated` | Token 用量 |
| `error` | 协议错误 |

## 数据表

| 表 | 说明 |
| --- | --- |
| `workspaces` | 工作区 |
| `threads` | 会话（状态、模型、权限模式、Token 统计） |
| `thread_messages` | 用户 / 助手消息 |
| `thread_events` | Copilot 事件与工具调用明细 |
| `permission_requests` | 权限审批请求及决定 |

## 测试

测试使用 H2（MySQL 兼容模式）和随机端口的 Spring Boot 集成测试：

```bash
mvn test
```

覆盖工作区 CRUD、会话 CRUD、时间线聚合、权限请求列表、文件读写和路径越界保护。

## 说明与限制

- Copilot SDK 运行时通过 `copilot-sdk-runtime` 的 classifier 引入，默认 `linux-x64`。其他平台请修改 `pom.xml` 的 `copilot.runtime.classifier`：macOS 为 `darwin-x64` / `darwin-arm64`，Windows 为 `win32-x64` / `win32-arm64`，Linux arm64 为 `linux-arm64`，Alpine 为 `linuxmusl-x64` / `linuxmusl-arm64`。
- 工作区路径必须位于 `CCA_WORKSPACE_ROOT` 下；文件接口会解析并校验规范化路径，阻止 `../` 和绝对路径越界。
- 文件预览仅支持 UTF-8 文本、单文件不超过 1MB。
- 生产环境请务必通过反向代理启用 HTTPS，并开启 `CCA_SECURITY_ENABLED`。
