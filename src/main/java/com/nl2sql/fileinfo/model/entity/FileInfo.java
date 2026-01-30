package com.nl2sql.fileinfo.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

/**
 * 文件信息实体类（映射 file_info 表）
 * @author Ayaki
 * @since 2026/01/29
 */
@Data
@Entity
@Table(name = "file_info")
public class FileInfo {

    /**
     * 文件ID（主键，自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    /**
     * 文件名称（包含后缀名）
     */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * 文件本地保存完整路径（如：/upload/docx/20250129/a.docx）
     */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /**
     * 所属目录ID（留空，默认0）
     */
    @Column(name = "category_id", nullable = false)
    private Long categoryId = 0L;

    /**
     * 上传用户ID（前端传递）
     */
    @Column(name = "upload_user_id", nullable = false)
    private Long uploadUserId;

    /**
     * 上传时间（自动填充）
     */
    @Column(name = "upload_time", nullable = false, insertable = false, updatable = false)
    private Date uploadTime;

    /**
     * 文件备注
     */
    @Column(name = "remark")
    private String remark = "";

    /**
     * 扩展字段：文件大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize = 0L;

    /**
     * 扩展字段：文件后缀名
     */
    @Column(name = "file_suffix")
    private String fileSuffix = "";
}