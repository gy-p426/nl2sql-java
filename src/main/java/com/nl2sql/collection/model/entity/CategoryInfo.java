package com.nl2sql.collection.model.entity;

/**
 * <p>
 *  目录结构表
 * </p>
 *
 * @author Ayaki
 * @since 2026/1/29 15:32
 */

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "category_info")
public class CategoryInfo {

    @Id  // 标记为主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 自增策略（对应MySQL AUTO_INCREMENT）
    @Column(name = "category_id", nullable = false, updatable = false)  // 映射到表字段，不可为null，不可更新
    private Long categoryId;

    @Column(name = "user_id", nullable = false)
    private Integer userId; // 用户ID

    /**
     * 目录名称
     */
    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    /**
     * 目录级别（1=顶级目录，2=二级目录，以此类推）
     */
    @Column(name = "category_level", nullable = false)
    private Integer categoryLevel = 1;  // 默认值1

    /**
     * 父目录ID（顶级目录父ID为0）
     */
    @Column(name = "parent_category_id", nullable = false)
    private Long parentCategoryId = 0L;  // 默认值0

    /**
     * 目录组别（collection收藏、file文件）
     */
    @Column(name = "category_group", nullable = false, length = 50)
    private String categoryGroup;

    /**
     * 创建时间
     */
    @Column(
            name = "create_time",
            nullable = false,
            insertable = false, // 关键：插入时不将该字段写入SQL（由数据库自动填充）
            updatable = false    // 关键：更新时不将该字段写入SQL（由数据库自动维护）
    )
    private Date createTime;

    /**
     * 更新时间
     */
    @Column(
            name = "update_time",
            nullable = false,
            insertable = false, // 插入时不传递，用数据库默认值
            updatable = false   // 更新时不手动传递，由数据库ON UPDATE自动维护
    )
    private Date updateTime;
}
