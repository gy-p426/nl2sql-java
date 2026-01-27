# Excel管理模块 (excelmanage)

## 📁 模块结构

```
com.excelmanage/
├── controller/          # 控制器层
│   └── ExcelManageController.java
├── service/             # 服务层
│   └── ExcelManageService.java
├── repository/          # 数据访问层
│   └── ExcelManageRepository.java
└── model/               # 数据模型
    ├── entity/          # 实体类
    │   └── ExcelManageEntity.java
    └── dto/             # 数据传输对象
        └── ExcelManageDTO.java
```

## 🎯 模块说明

这是一个独立的Excel管理功能模块，与 `com.nl2sql` 模块平级，但共用：
- 数据库配置（JPA/Hibernate）
- 统一响应格式（ApiResponse）
- 全局异常处理
- 日志配置

## 📝 当前状态

- ✅ 基础框架已搭建
- ✅ Controller、Service、Repository 结构已创建
- ✅ Entity 和 DTO 占位类已创建
- ⏳ 业务逻辑待实现

## 🚀 使用说明

### API 接口

- **健康检查**: `GET /api/excel-manage/health`
- **获取Excel列表**: `GET /api/excel-manage` (示例接口，待实现)
- **上传Excel文件**: `POST /api/excel-manage` (示例接口，待实现)

### 数据库表

模块使用 `excel_manage` 表存储Excel文件信息，表结构由 `ExcelManageEntity` 定义。

## 📌 开发指南

1. **添加新功能**：在 `ExcelManageService` 中实现业务逻辑
2. **添加新接口**：在 `ExcelManageController` 中添加新的端点
3. **数据库操作**：使用 `ExcelManageRepository` 进行数据访问
4. **实体修改**：修改 `ExcelManageEntity` 后，JPA会自动更新表结构（ddl-auto: update）

## ⚠️ 注意事项

- 所有业务逻辑应在 Service 层实现
- Controller 层只负责接收请求和返回响应
- 使用 `@Transactional` 注解管理事务
- 遵循现有代码风格和命名规范
