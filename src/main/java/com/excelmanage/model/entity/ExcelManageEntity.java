package com.excelmanage.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Excel管理实体
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Data
@Entity
@Table(name = "excel_manage")
public class ExcelManageEntity {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Excel文件名称
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Excel文件路径
     */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 状态：1-启用，0-禁用
     */
    @Column(name = "status", nullable = false)
    private Boolean status = true;

    /**
     * 备注
     */
    @Column(name = "remark", length = 1000)
    private String remark;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
