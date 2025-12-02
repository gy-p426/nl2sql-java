# NL2SQL Java Service

自然语言转SQL服务 - Java Spring Boot 实现

## 项目简介

这是一个将自然语言问题转换为SQL查询的服务，支持多数据库、智能关键词提取、连续问题判断等功能。

## 技术栈

- **框架**: Spring Boot 3.2.0
- **Java**: 17+
- **数据库**: MySQL 8.0+
- **连接池**: HikariCP
- **缓存**: Caffeine
- **中文分词**: HanLP
- **AI模型**: 火山引擎 DeepSeek-V3
- **API文档**: SpringDoc OpenAPI

## 项目结构

```
nl2sql-java/
├── src/main/java/com/nl2sql/
│   ├── NL2SQLApplication.java          # 主应用程序
│   ├── config/                         # 配置类
│   │   ├── DatabaseConfig.java         # 数据库配置
│   │   ├── NL2SQLProperties.java       # 配置属性
│   │   ├── CacheConfig.java            # 缓存配置
│   │   └── CorsConfig.java             # CORS配置
│   ├── controller/                     # 控制器
│   │   ├── QueryController.java        # 查询接口
│   │   └── AnnotationController.java   # 注释管理接口
│   ├── service/                        # 服务层
│   │   ├── NL2SQLService.java          # 核心服务
│   │   ├── DatabaseService.java        # 数据库服务
│   │   ├── SessionService.java         # Session管理
│   │   ├── KeywordExtractorService.java # 关键词提取
│   │   ├── AnnotationService.java      # 注释管理
│   │   └── CacheService.java           # 缓存服务
│   ├── client/                         # 外部客户端
│   │   └── VolcanoEngineClient.java    # 火山引擎客户端
│   ├── model/                          # 数据模型
│   │   ├── dto/                        # 数据传输对象
│   │   └── entity/                     # 实体类
│   ├── util/                           # 工具类
│   │   └── JsonParser.java             # JSON解析工具
│   └── exception/                      # 异常处理
│       └── GlobalExceptionHandler.java
└── src/main/resources/
    └── application.yml                 # 应用配置
```

## 快速开始

### 1. 环境要求

- JDK 17 或更高版本
- Maven 3.6+
- MySQL 8.0+

### 2. 配置

编辑 `src/main/resources/application.yml`：

```yaml
nl2sql:
  databases:
    - name: db1
      host: localhost
      port: 3306
      username: root
      password: your_password
      databases:
        - database1
        - database2
  
  volcano-engine:
    api-key: your_api_key
    model: deepseek-v3-1-250821
```

### 3. 构建

```bash
cd nl2sql-java
mvn clean package
```

### 4. 运行

```bash
java -jar target/nl2sql-service-1.0.0.jar
```

或使用 Maven：

```bash
mvn spring-boot:run
```

### 5. 访问

- **主服务**: http://localhost:8080/api
- **API文档**: http://localhost:8080/api/swagger-ui.html
- **健康检查**: http://localhost:8080/api/query/health

## API 接口

### 查询接口

**POST** `/api/query`

请求体：
```json
{
  "question": "查询所有员工的工资",
  "windowId": "default",
  "sessionId": null
}
```

响应：
```json
{
  "success": true,
  "data": {
    "sessionId": "uuid",
    "question": "查询所有员工的工资",
    "sql": "SELECT * FROM ...",
    "results": [...],
    "resultCount": 10
  }
}
```

### 注释管理接口

**GET** `/api/annotations/databases` - 获取所有数据库

**GET** `/api/annotations/databases/{dbName}` - 获取数据库结构

**POST** `/api/annotations` - 更新注释

## 核心功能

### 1. 多数据库支持

- 支持配置多个数据库主机
- 动态数据源路由
- 连接池管理

### 2. 智能关键词提取

- AI驱动的关键词提取
- HanLP中文分词回退
- 支持中英文关键词

### 3. 连续问题判断

- 基于AI的问题连续性判断
- 窗口级别的上下文管理
- 自动问题合并

### 4. 缓存机制

- Caffeine本地缓存
- 关键词缓存
- SQL结果缓存

### 5. 注释管理

- 自定义表和列注释
- 注释导入/导出
- 与数据库注释合并

## 配置说明

### 数据库配置

```yaml
nl2sql:
  databases:
    - name: db1              # 主机名称
      host: localhost        # 主机地址
      port: 3306            # 端口
      username: root        # 用户名
      password: root        # 密码
      databases:            # 数据库列表
        - db1
        - db2
      pool:                 # 连接池配置
        maximum-pool-size: 10
        minimum-idle: 5
```

### AI模型配置

```yaml
nl2sql:
  volcano-engine:
    api-key: your_key
    model: deepseek-v3-1-250821
    timeout: 1800000
    base-url: https://ark.cn-beijing.volces.com/api/v3
```

### 缓存配置

```yaml
nl2sql:
  settings:
    cache-max-size: 1000
    cache-expire-minutes: 60
```

## 开发指南

### 添加新的数据库

1. 在 `application.yml` 中添加数据库配置
2. 重启应用，连接池会自动初始化

### 扩展关键词提取

修改 `KeywordExtractorService.java` 中的提取逻辑

### 自定义SQL生成

修改 `NL2SQLService.java` 中的 `generateSQL` 方法

## 测试

```bash
mvn test
```

## 部署

### Docker 部署

```bash
docker build -t nl2sql-service .
docker run -p 8080:8080 nl2sql-service
```

### 生产环境配置

创建 `application-prod.yml`：

```yaml
spring:
  profiles:
    active: prod

logging:
  level:
    root: WARN
    com.nl2sql: INFO
```

运行：
```bash
java -jar -Dspring.profiles.active=prod nl2sql-service-1.0.0.jar
```

## 性能优化

1. **连接池调优**: 根据并发量调整 `maximum-pool-size`
2. **缓存策略**: 调整缓存大小和过期时间
3. **JVM参数**: `-Xms2g -Xmx4g -XX:+UseG1GC`

## 故障排查

### 数据库连接失败

检查：
- 数据库地址和端口
- 用户名密码
- 防火墙设置

### AI调用失败

检查：
- API密钥是否正确
- 网络连接
- 账户余额

## 从Python迁移的差异

| 功能 | Python | Java |
|------|--------|------|
| Web框架 | Flask | Spring Boot |
| 连接池 | mysql-connector-python | HikariCP |
| 缓存 | 内存字典 | Caffeine |
| 分词 | jieba | HanLP |
| 配置 | INI文件 | YAML |

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

- 项目地址: [GitHub]
- 文档: [Wiki]
