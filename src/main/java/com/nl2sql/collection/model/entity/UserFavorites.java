package com.nl2sql.collection.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * <p>
 * 用户收藏表
 * </p>
 *
 * @author Ayaki
 * @since 2026/2/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_favorites")
public class UserFavorites {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 对应MySQL AUTO_INCREMENT
    @Column(name = "id", nullable = false, updatable = false)
    private Long id; // 收藏ID

    @Column(name = "user_id", nullable = false)
    private Integer userId; // 用户ID

    @Column(name = "category_id", nullable = false)
    private Long categoryId; // 关联目录ID

    @Column(name = "favorite_name", nullable = false, length = 200)
    private String favoriteName; // 收藏名称

    @Column(name = "original_question", nullable = false, columnDefinition = "TEXT")
    private String originalQuestion; // 用户原始问题

    @Column(name = "algorithm_type", nullable = false, length = 50)
    private String algorithmType; // 算法类型

    @Column(name = "window_id", length = 100)
    private String windowId; // 窗口ID

    @Column(name = "session_id", length = 100)
    private String sessionId; // 会话ID

    @Column(name = "algorithm_params", columnDefinition = "JSON")
    private String algorithmParams; // 算法参数（JSON格式，这里先用String接收，后续可优化为JsonObject）

    @Column(name = "original_sql", columnDefinition = "TEXT")
    private String originalSql; // 原始生成的完整SQL

    @Column(name = "where_template", columnDefinition = "TEXT")
    private String whereTemplate; // WHERE条件模板（带占位符）

    @Column(name = "where_params", columnDefinition = "JSON")
    private String whereParams; // WHERE条件参数

    @Column(name = "options_query_sqls", columnDefinition = "JSON")
    private String optionsQuerySqls; // 查询参数options的SQL语句

    @Column(name = "execution_count")
    private Integer executionCount = 0; // 执行次数，默认值0

    @Column(name = "last_executed_at")
    private Date lastExecutedAt; // 最后执行时间

    @Column(name = "last_where_params", columnDefinition = "JSON")
    private String lastWhereParams; // 最后一次执行的WHERE参数

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Date createdAt; // 创建时间（数据库自动填充）

    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Date updatedAt; // 更新时间（数据库自动维护）

    @Column(name = "deleted")
    private Boolean deleted = false; // 是否删除，默认值false

    @Column(name = "tags", length = 500)
    private String tags; // 标签（逗号分隔）

    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 收藏描述
}