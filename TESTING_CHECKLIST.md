# API测试清单

## 测试环境准备

### 前置条件
- [ ] Java 17+ 已安装
- [ ] Maven 3.6+ 已安装
- [ ] MySQL数据库已配置
- [ ] config-m.ini配置文件已准备
- [ ] 端口8080可用

### 启动服务
```bash
cd nl2sql-java
mvn clean package
java -jar target/nl2sql-java-1.0.0.jar
```

或使用Docker:
```bash
docker-compose up -d
```

## 1. 查询接口测试 (QueryController)

### 1.1 健康检查
```bash
curl http://localhost:8080/query/health
```
**预期**: `{"success":true,"data":"NL2SQL Service is running"}`
- [ ] 响应状态码200
- [ ] 返回正确的JSON格式

### 1.2 标准查询
```bash
curl -X POST http://localhost:8080/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "查询所有员工",
    "windowId": "default",
    "sessionId": null
  }'
```
- [ ] 响应状态码200
- [ ] 返回SQL语句
- [ ] 返回查询结果
- [ ] 包含session_id

### 1.3 流式查询 ⭐ 新增
```bash
# Linux/Mac
./test-stream-endpoint.sh

# Windows
test-stream-endpoint.bat
```
或手动测试:
```bash
curl -N -X POST http://localhost:8080/query/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "question": "查询所有员工",
    "windowId": "default"
  }'
```
**预期SSE事件**:
```
event: progress
data: {"step":"start","status":"processing","message":"开始处理查询"}

event: result
data: {"success":true,"sql":"...","results":[...]}
```
- [ ] 接收到progress事件
- [ ] 接收到result事件
- [ ] 连接正常关闭
- [ ] 错误时接收error事件

## 2. Session管理测试 (SessionController)

### 2.1 获取Session缓存
```bash
curl http://localhost:8080/session-cache?limit=10
```
- [ ] 返回session列表
- [ ] 包含question、timestamp等字段

### 2.2 清除Session缓存
```bash
curl -X DELETE http://localhost:8080/session-cache
```
- [ ] 响应成功
- [ ] 缓存已清空

## 3. 窗口管理测试 (WindowController)

### 3.1 获取所有窗口
```bash
curl http://localhost:8080/windows
```
- [ ] 返回窗口列表
- [ ] 包含窗口ID和session数量

### 3.2 获取指定窗口信息
```bash
curl http://localhost:8080/windows/default
```
- [ ] 返回窗口详细信息
- [ ] 包含最新问题

### 3.3 获取窗口的Session列表
```bash
curl http://localhost:8080/windows/default/sessions?limit=10
```
- [ ] 返回该窗口的session列表

### 3.4 清除指定窗口
```bash
curl -X DELETE http://localhost:8080/windows/test-window
```
- [ ] 响应成功
- [ ] 窗口上下文已清除

### 3.5 清除所有窗口
```bash
curl -X DELETE http://localhost:8080/windows
```
- [ ] 响应成功
- [ ] 所有窗口已清除

## 4. Schema管理测试 (SchemaController)

### 4.1 刷新Schema
```bash
curl -X POST http://localhost:8080/refresh-schema?forceRefresh=true
```
- [ ] Schema文件已更新
- [ ] 返回刷新的数据库列表

### 4.2 重新加载注释
```bash
curl -X POST http://localhost:8080/reload-annotations
```
- [ ] 注释已重新加载
- [ ] 返回成功消息

## 5. 配置管理测试 (ConfigController)

### 5.1 获取系统配置
```bash
curl http://localhost:8080/config
```
- [ ] 返回完整配置
- [ ] 包含数据库、模型等配置

### 5.2 获取统计信息
```bash
curl http://localhost:8080/config/stats
```
- [ ] 返回数据库数量
- [ ] 返回训练数据数量

### 5.3 热重载配置
```bash
curl -X POST http://localhost:8080/config/reload
```
- [ ] 配置已重新加载
- [ ] 不需要重启服务

### 5.4 数据库主机管理
```bash
# 获取所有主机
curl http://localhost:8080/config/database-hosts

# 添加主机
curl -X POST http://localhost:8080/config/database-hosts \
  -H "Content-Type: application/json" \
  -d '{
    "section": "test_db",
    "host": "localhost",
    "user": "root",
    "password": "password",
    "databases": ["test"]
  }'

# 删除主机
curl -X DELETE http://localhost:8080/config/database-hosts/test_db
```
- [ ] 可以添加主机
- [ ] 可以删除主机
- [ ] 可以查询主机列表

### 5.5 测试数据库连接
```bash
# 测试新连接
curl -X POST http://localhost:8080/config/test-connection \
  -H "Content-Type: application/json" \
  -d '{
    "host": "localhost",
    "user": "root",
    "password": "password"
  }'

# 测试已保存的连接
curl -X POST http://localhost:8080/config/database-hosts/test_db/test \
  -H "Content-Type: application/json" \
  -d '{"database": "test"}'
```
- [ ] 连接成功返回数据库列表
- [ ] 连接失败返回错误信息

### 5.6 模型配置
```bash
# 获取火山引擎配置
curl http://localhost:8080/config/volcano-engine

# 更新火山引擎配置
curl -X PUT http://localhost:8080/config/volcano-engine \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "your-api-key",
    "model": "doubao-pro-32k",
    "timeout": 1800
  }'

# 获取Ollama配置
curl http://localhost:8080/config/ollama

# 更新Ollama配置
curl -X PUT http://localhost:8080/config/ollama \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "host": "http://localhost:11434",
    "model": "qwen2.5:32b",
    "timeout": 1800
  }'

# 测试Ollama连接
curl -X POST http://localhost:8080/config/ollama/test \
  -H "Content-Type: application/json" \
  -d '{
    "host": "http://localhost:11434",
    "model": "qwen2.5:32b"
  }'

# 获取模型提供商
curl http://localhost:8080/config/model-provider

# 设置模型提供商
curl -X PUT http://localhost:8080/config/model-provider \
  -H "Content-Type: application/json" \
  -d '{"provider": "ollama"}'
```
- [ ] 可以获取和更新配置
- [ ] 可以测试连接
- [ ] 可以切换模型提供商

## 6. 注释管理测试 (AnnotationController)

### 6.1 获取数据库列表
```bash
curl http://localhost:8080/annotations/databases
```
- [ ] 返回所有数据库名称

### 6.2 获取数据库结构
```bash
curl http://localhost:8080/annotations/databases/your_database
```
- [ ] 返回表和列信息
- [ ] 包含注释

### 6.3 更新注释
```bash
curl -X POST http://localhost:8080/annotations \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "your_database",
    "tableName": "users",
    "columnName": "id",
    "comment": "用户ID"
  }'
```
- [ ] 注释更新成功
- [ ] 保存到annotations.json

## 7. 训练数据测试 (TrainingDataController)

### 7.1 获取训练数据
```bash
curl "http://localhost:8080/training-data?page=1&pageSize=20&search=员工"
```
- [ ] 返回分页数据
- [ ] 支持搜索

### 7.2 新增训练数据
```bash
curl -X POST http://localhost:8080/training-data \
  -H "Content-Type: application/json" \
  -d '{
    "question": "查询所有员工",
    "sql": "SELECT * FROM employees",
    "database": "hr",
    "description": "查询员工表"
  }'
```
- [ ] 数据添加成功
- [ ] 返回新增的ID

### 7.3 批量新增
```bash
curl -X POST http://localhost:8080/training-data/batch \
  -H "Content-Type: application/json" \
  -d '{
    "dataList": [
      {
        "question": "问题1",
        "sql": "SQL1",
        "database": "db1"
      },
      {
        "question": "问题2",
        "sql": "SQL2",
        "database": "db2"
      }
    ]
  }'
```
- [ ] 批量添加成功
- [ ] 返回成功数量

### 7.4 上传文件
```bash
curl -X POST http://localhost:8080/training-data/upload \
  -F "file=@train-data.txt" \
  -F "database=hr"
```
- [ ] 文件上传成功
- [ ] 数据导入成功

### 7.5 更新训练数据
```bash
curl -X PUT http://localhost:8080/training-data/1 \
  -H "Content-Type: application/json" \
  -d '{
    "question": "更新后的问题",
    "sql": "更新后的SQL"
  }'
```
- [ ] 数据更新成功

### 7.6 删除训练数据
```bash
curl -X DELETE http://localhost:8080/training-data/1
```
- [ ] 数据删除成功

## 8. 测试记录测试 (TestRecordController)

### 8.1 获取测试记录
```bash
curl "http://localhost:8080/test-records?page=1&pageSize=20&status=passed"
```
- [ ] 返回分页数据
- [ ] 支持状态筛选

### 8.2 新增测试记录
```bash
curl -X POST http://localhost:8080/test-records \
  -F "question=测试问题" \
  -F "expected_result=预期结果" \
  -F "actual_result=实际结果" \
  -F "status=passed" \
  -F "notes=备注" \
  -F "images=@screenshot.png"
```
- [ ] 记录添加成功
- [ ] 图片上传成功

### 8.3 获取单个记录
```bash
curl http://localhost:8080/test-records/test-id-123
```
- [ ] 返回记录详情
- [ ] 包含图片列表

### 8.4 更新测试记录
```bash
curl -X PUT http://localhost:8080/test-records/test-id-123 \
  -F "status=failed" \
  -F "notes=更新备注"
```
- [ ] 记录更新成功

### 8.5 删除测试记录
```bash
curl -X DELETE http://localhost:8080/test-records/test-id-123
```
- [ ] 记录删除成功
- [ ] 关联图片已删除

### 8.6 获取测试图片
```bash
curl http://localhost:8080/test-records/images/screenshot.png -o downloaded.png
```
- [ ] 图片下载成功

### 8.7 备份测试记录
```bash
curl -X POST http://localhost:8080/test-records/backup
```
- [ ] 备份文件创建成功

### 8.8 获取测试统计
```bash
curl http://localhost:8080/test-records/stats
```
- [ ] 返回统计信息
- [ ] 包含通过率

## 9. SQL执行测试 (SqlExecutionController)

### 9.1 执行SQL
```bash
curl -X POST http://localhost:8080/execute-sql \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM employees LIMIT 10",
    "limit": 100
  }'
```
- [ ] SQL执行成功
- [ ] 返回查询结果
- [ ] 限制结果数量

## 10. 错误处理测试

### 10.1 参数验证
```bash
# 缺少必要参数
curl -X POST http://localhost:8080/query \
  -H "Content-Type: application/json" \
  -d '{}'
```
- [ ] 返回400错误
- [ ] 错误信息清晰

### 10.2 数据库连接错误
```bash
curl -X POST http://localhost:8080/config/test-connection \
  -H "Content-Type: application/json" \
  -d '{
    "host": "invalid-host",
    "user": "root",
    "password": "wrong"
  }'
```
- [ ] 返回连接错误
- [ ] 错误信息有帮助

### 10.3 SQL执行错误
```bash
curl -X POST http://localhost:8080/execute-sql \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM non_existent_table"
  }'
```
- [ ] 返回SQL错误
- [ ] 包含错误详情

## 11. 性能测试

### 11.1 并发查询
```bash
# 使用Apache Bench
ab -n 100 -c 10 -p query.json -T application/json \
  http://localhost:8080/query
```
- [ ] 响应时间 < 2秒
- [ ] 无错误响应
- [ ] 内存使用稳定

### 11.2 流式查询性能
```bash
# 测试多个并发流式连接
for i in {1..10}; do
  curl -N -X POST http://localhost:8080/query/stream \
    -H "Content-Type: application/json" \
    -d '{"question":"测试'$i'"}' &
done
wait
```
- [ ] 支持多个并发流
- [ ] 无连接泄漏
- [ ] 正确处理超时

## 12. 集成测试

### 12.1 完整查询流程
1. [ ] 发起查询请求
2. [ ] 选择数据库
3. [ ] 提取关键词
4. [ ] 选择候选表
5. [ ] 生成SQL
6. [ ] 执行SQL
7. [ ] 返回结果
8. [ ] 保存到session

### 12.2 多窗口场景
1. [ ] 创建多个窗口
2. [ ] 每个窗口独立查询
3. [ ] 上下文不互相干扰
4. [ ] 可以清除单个窗口
5. [ ] 可以清除所有窗口

## 13. Swagger文档测试

### 13.1 访问Swagger UI
```
http://localhost:8080/swagger-ui.html
```
- [ ] 页面正常加载
- [ ] 显示所有端点
- [ ] 可以在线测试

### 13.2 API文档
```
http://localhost:8080/v3/api-docs
```
- [ ] 返回OpenAPI规范
- [ ] 包含所有端点定义

## 测试总结

### 通过标准
- [ ] 所有52个端点测试通过
- [ ] 无编译错误
- [ ] 无运行时错误
- [ ] 响应时间合理
- [ ] 内存使用正常
- [ ] 日志记录完整

### 测试报告
- 测试日期: ___________
- 测试人员: ___________
- 通过端点: ___ / 52
- 失败端点: ___________
- 性能评分: ___________
- 总体评价: ___________

### 问题记录
| 端点 | 问题描述 | 严重程度 | 状态 |
|------|---------|---------|------|
|      |         |         |      |

---
**注意**: 
1. 测试前确保数据库已正确配置
2. 某些测试需要真实的API密钥
3. 性能测试建议在独立环境进行
4. 流式查询测试需要支持SSE的客户端
