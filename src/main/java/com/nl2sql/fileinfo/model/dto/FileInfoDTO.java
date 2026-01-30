package com.nl2sql.fileinfo.model.dto;

import lombok.Data;

import java.util.Date;

/**
 * 文件信息返回DTO
 * @author Ayaki
 * @since 2026/01/29
 */
@Data
public class FileInfoDTO {
    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件保存路径
     */
    private String filePath;

    /**
     * 所属目录ID
     */
    private Long categoryId;

    /**
     * 上传用户ID
     */
    private Long uploadUserId;

    /**
     * 上传时间
     */
    private Date uploadTime;

    /**
     * 文件备注
     */
    private String remark;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件后缀名
     */
    private String fileSuffix;
}