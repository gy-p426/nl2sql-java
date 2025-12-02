# 快速启动指南

## 前置要求

- ✅ JDK 17 或更高版本
- ✅ Maven 3.6+
- ✅ MySQL 8.0+
- ✅ 火山引擎 API Key

## 5分钟快速启动

### 1. 配置数据库

编辑 `src/main/resources/application.yml`：

```yaml
nl2sql:
  databases:
    - name: db1
      host: localhost          # 修改为你的数据库地址
      port: 3306
      username: root           # 修改为你的用户名
      password: your_password  # 修改为你的密码
      databases:
        - database1            # 修改为你的数据库名
        - database2
```

### 2. 配置 API Key

方式一：修改配置文件
```yaml
nl2sql:
  volcano-engine:
    api-key: your_api_key_here
```

方式二：使用环境变量（推荐）
```bash
export VOLCANO_API_KEY=your_api_key_here
```

### 3. 构建项目

```bash
cd nl2sql-java
mvn clean package -DskipTests
```

### 4. 运行服务

**Windows:**
```bash
run.bat
```

**Linux/Mac:**
```bash
chmod +x run.sh
./run.sh
```

**或直接运行 JAR:**
```bash
java -jar target/nl2sql-service-1.0.0.jar
```

### 5. 测试服务

打开浏览器访问：
- 健康检查: http://localhost:8080/api/query/health
- API 文档: http://localhost:8080/api/swagger-ui.html

## 测试 API

### 使用 curl

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "查询所有员工的工资",
    "windowId": "default"
  }'
```

### 使用 Postman

1. 创建 POST 请求到 `http://localhost:8080/api/query`
2. 设置 Header: `Content-Type: application/json`
3. Body (raw JSON):
```json
{
  "question": "查询所有员工的工资",
  "windowId": "default"
}
```

## 常见问题

### Q: 启动失败，提示数据库连接错误
A: 检查 `application.yml` 中的数据库配置是否正确，确保数据库服务已启动。

### Q: API 调用返回错误
A: 检查火山引擎 API Key 是否正确，账户是否有余额。

### Q: 端口 8080 被占用
A: 修改 `application.yml` 中的 `server.port` 配置。

### Q: 如何查看日志
A: 日志文件位于 `logs/nl2sql.log`

## 下一步

- 📖 阅读完整文档: [README.md](README.md)
- 🔧 查看迁移指南: [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md)
- 🚀 部署到生产环境: 使用 Docker 或直接部署 JAR

## 获取帮助

如有问题，请查看：
1. 日志文件 `logs/nl2sql.log`
2. API 文档 http://localhost:8080/api/swagger-ui.html
3. 项目 README.md
