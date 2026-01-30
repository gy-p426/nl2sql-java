package com.nl2sql.fileinfo.repository;

import com.nl2sql.fileinfo.model.entity.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文件信息数据访问层
 * @author Ayaki
 * @since 2026/01/29
 */
@Repository
public interface FileInfoRepository extends JpaRepository<FileInfo, Long> {

    /**
     * 按上传用户ID查询文件列表
     * @param uploadUserId 上传用户ID
     * @return 该用户上传的所有文件
     */
    List<FileInfo> findByUploadUserId(Long uploadUserId);

    /**
     * 按文件后缀名查询文件列表
     * @param fileSuffix 文件后缀名
     * @return 该后缀的所有文件
     */
    List<FileInfo> findByFileSuffix(String fileSuffix);

    /**
     * 组合条件查询：按上传用户ID + 文件后缀名查询（多条件过滤）
     * @param uploadUserId 上传用户ID
     * @param fileSuffix 文件后缀名
     * @return 满足双重条件的文件列表
     */
    List<FileInfo> findByUploadUserIdAndFileSuffix(Long uploadUserId, String fileSuffix);

    List<FileInfo> findByFileIdIn(List<Long> fileIds);
}