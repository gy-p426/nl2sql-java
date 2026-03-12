# AGENTS.md

## Project at a glance
- 本项目是 Spring Boot 3.2 / Java 21 的 NL2SQL 后端，包含 Excel 导入管理模块。 / Spring Boot 3.2 + Java 21 backend for NL2SQL with an Excel ingestion module.
- 启动时扫描两个平级包：`com.nl2sql` 与 `com.excelmanage`（见 `src/main/java/com/nl2sql/NL2SQLApplication.java`）。 / Startup scans both `com.nl2sql` and `com.excelmanage`.
- API 基础路径为 `/api`（见 `src/main/resources/application.yml`），主要控制器在 `/query`、`/config`、`/export`、`/excel-manage`。 / API base path is `/api` with controllers under those routes.

## Architecture and data flow (important)
- 核心查询链路为分阶段处理：选库 -> 关键词提取 -> 候选表 -> SQL 生成 -> SQL 执行（`src/main/java/com/nl2sql/service/NL2SQLService.java`）。 / Core query flow is staged end-to-end.
- 流式查询使用 SSE，返回 step/status/timestamp 等进度字段，并异步执行（`src/main/java/com/nl2sql/controller/QueryController.java`，`src/main/java/com/nl2sql/service/StreamingService.java`）。 / Streaming uses SSE with structured progress payloads.
- SQL 生成采用多模型并行、执行结果过滤、再由模型评估选优（`src/main/java/com/nl2sql/service/SQLGeneratorService.java`）。 / SQL generation is parallel + execution-filtered + model-evaluated.
- 运行时数据库来自 `database_host_config`，通过动态连接池管理，不要写死目标库（`src/main/java/com/nl2sql/service/DatabasePoolService.java`）。 / Runtime DB targets are dynamic, not hardcoded.
- 启动阶段有 `CommandLineRunner` 初始化：YAML 配置同步入库、Schema/Overview 导出（`src/main/java/com/nl2sql/service/ConfigInitializationService.java`，`src/main/java/com/nl2sql/service/InitializationService.java`）。 / Startup initialization syncs config and metadata.

## Conventions this repo actually uses
- REST 响应统一使用 `ApiResponse<T>`（`success/message/data/error`），新增接口保持一致（`src/main/java/com/nl2sql/model/dto/ApiResponse.java`）。 / Keep all REST responses wrapped in `ApiResponse<T>`.
- 虽有全局异常处理，控制器仍普遍本地 try/catch 并返回 `ApiResponse.error(...)`（`src/main/java/com/nl2sql/exception/GlobalExceptionHandler.java`）。 / Controllers commonly catch exceptions explicitly.
- 控制器广泛使用 OpenAPI 注解（`@Tag`、`@Operation`），新增接口遵循现有风格。 / Follow existing OpenAPI annotation style.
- CORS 在全局配置里放开，同时很多控制器也重复声明。 / CORS is permissive globally and often repeated per controller.
- `com.excelmanage` 按 controller/service/repository 分层，业务逻辑放 service（`src/main/java/com/excelmanage/README.md`）。 / Keep business logic in service layer for Excel module.

## Integrations and external dependencies
- LLM 按任务路由：`CONTINUOUS_QUESTION`、`DATABASE_SELECTION`、`KEYWORD_EXTRACTION`、`SQL_GENERATION`、`SQL_EVALUATION`，由 `LLMRouter` + `nl2sql.llm.*` 配置驱动。 / Task-based LLM routing is config-driven.
- Volcano Engine 与 Ollama 共存，服务层内置提供方切换/降级逻辑（`src/main/java/com/nl2sql/service/DatabaseService.java`）。 / Volcano/Ollama coexist with fallback behavior.
- 系统元数据与运行配置依赖 MySQL，参考结构文件：`src/main/resources/schema.sql`、`src/main/resources/schema-config.sql`。 / MySQL is required for metadata and runtime config.
- 导出接口会先移除 SQL 的 `LIMIT` 再执行全量导出，支持 CSV/Excel 流式输出（`src/main/java/com/nl2sql/service/ExportService.java`）。 / Export removes `LIMIT` before full export.

## Developer workflows (verified from files/docs)
- 构建 JAR：`mvn clean package -DskipTests`（`Dockerfile` 也使用该命令）。 / Build with Maven skip-tests package command.
- 运行测试：`mvn test`（当前测试覆盖较少，见 `src/test/java/com/nl2sql/NL2SQLApplicationTests.java`）。 / Test coverage is currently minimal.
- 本地运行 JAR：`java -jar target/nl2sql-service-1.0.0.jar`。 / Run the packaged JAR directly.
- Docker 编排启动：`docker-compose up -d`（见 `docker-compose.yml`）。 / Start stack via docker-compose.

## Practical gotchas for coding agents
- 端口信息不一致：应用配置常见 `8081`，但 `Dockerfile`/compose 暴露 `8080`；改 URL 前先确认目标环境。 / Port assumptions differ across files; verify environment first.
- 查询质量依赖元数据表是否已初始化；出现选库/选表异常时先检查初始化链路。 / Metadata initialization strongly affects query quality.
- `application.yml` 含默认样例密钥/占位值，修改敏感配置时优先走环境变量覆盖。 / Prefer env overrides for secrets/config-sensitive changes.
- 流式响应字段结构（`step`、`status`、`timestamp` 及相关 payload）需保持稳定，前端流程文档依赖它（`query-stream 流程.md`）。 / Keep SSE payload shape stable for frontend compatibility.

