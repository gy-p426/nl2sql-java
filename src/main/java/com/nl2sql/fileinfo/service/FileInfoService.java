package com.nl2sql.fileinfo.service;

import com.nl2sql.fileinfo.model.dto.FileUploadDTO;
import com.nl2sql.fileinfo.model.entity.FileInfo;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author Ayaki
 * @since 2026/1/29 17:28
 */
public interface FileInfoService {

        /**
         * 上传文件（保存到本地 + 记录到数据库）
         * @param fileUploadDTO 上传请求参数
         * @return 保存后的文件信息
         */
        FileInfo uploadFile(FileUploadDTO fileUploadDTO);

        /**
         * 根据文件ID删除文件（删除本地文件 + 删除数据库记录）
         * @param fileId 文件ID
         */
        void deleteFileById(Long fileId);

        /**
         * 根据文件ID查询文件信息
         * @param fileId 文件ID
         * @return 文件信息
         */
        FileInfo getFileInfoById(Long fileId);

        /**
         * 带条件查询文件列表（支持用户ID、文件后缀、组合条件、无条件查询所有）
         * @param uploadUserId 上传用户ID（可选，null 则不过滤）
         * @param fileSuffix 文件后缀名（可选，null/空字符串 则不过滤）
         * @return 满足条件的文件列表
         */
        List<FileInfo> listFilesByCondition(Long uploadUserId, String fileSuffix);

        /**
         * 更新文件备注（仅更新备注，其他字段不允许修改）
         * @param fileId 文件ID
         * @param remark 新备注
         * @return 更新后的文件信息
         */
        FileInfo updateFileRemark(Long fileId, String remark);

        /**
         * 根据文件ID下载文件（写入响应流，供前端下载）
         * @param fileId 文件ID
         * @param response 响应对象（用于写入文件流）
         * @throws IOException 流操作异常
         */
        void downloadFileById(Long fileId, HttpServletResponse response) throws IOException;

        List<FileInfo> listFilesByIds(List<Long> fileIds);
}
