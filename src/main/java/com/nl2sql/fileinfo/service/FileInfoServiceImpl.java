package com.nl2sql.fileinfo.service;

/**
 * <p>
 *
 * </p>
 *
 * @author Ayaki
 * @since 2026/1/29 17:29
 */

import com.nl2sql.fileinfo.model.dto.FileUploadDTO;
import com.nl2sql.fileinfo.model.entity.FileInfo;
import com.nl2sql.fileinfo.repository.FileInfoRepository;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 文件信息业务层实现类
 * @author Ayaki
 * @since 2026/01/29
 */
@Service
public class FileInfoServiceImpl implements FileInfoService {

    /**
     * 从配置文件读取文件上传根目录
     */
    @Value("${file-upload.root-path}")
    private String fileUploadRootPath;

    /**
     * 日期格式化（用于生成日期目录，格式：yyyyMMdd）
     */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

    @Resource
    private FileInfoRepository fileInfoRepository;

    @Override
    public FileInfo uploadFile(FileUploadDTO fileUploadDTO) {
        // 1. 校验参数
        MultipartFile file = fileUploadDTO.getFile();
        Long uploadUserId = fileUploadDTO.getUploadUserId();
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }
        if (uploadUserId == null || uploadUserId <= 0) {
            throw new RuntimeException("上传用户ID不能为空且必须为正整数");
        }

        // 2. 获取文件基础信息
        String originalFileName = file.getOriginalFilename(); // 原始文件名（如 a.docx）
        String fileSuffix = StringUtils.getFilenameExtension(originalFileName); // 获取文件后缀（如 docx）
        long fileSize = file.getSize(); // 文件大小（字节）
        String currentDate = dateFormat.format(new Date()); // 当前日期（yyyyMMdd）

        // 3. 构建文件存储路径
        // 3.1 构建子路径：后缀名/日期（如 docx/20250129）
        String fileSubPath = fileSuffix + File.separator + currentDate;
        // 3.2 构建完整存储目录（根目录 + 子路径）
        File storeDir = new File(fileUploadRootPath + fileSubPath);
        // 3.3 若目录不存在，创建多级目录
        if (!storeDir.exists()) {
            boolean mkdirsSuccess = storeDir.mkdirs();
            if (!mkdirsSuccess) {
                throw new RuntimeException("创建文件存储目录失败：" + storeDir.getAbsolutePath());
            }
        }

        // 4. 构建唯一文件名（避免同名文件覆盖，可选：UUID + 原始文件名）
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String uniqueFileName = timestamp + "_" + originalFileName;
        // 5. 构建完整文件路径（目录 + 唯一文件名）
        File targetFile = new File(storeDir, uniqueFileName);

        // 6. 保存文件到本地
        try {
            file.transferTo(targetFile);
        } catch (IOException e) {
            throw new RuntimeException("文件保存到本地失败：" + e.getMessage(), e);
        }

        // 7. 封装 FileInfo 实体类（准备存入数据库）
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName(originalFileName); // 保存原始文件名
        fileInfo.setFilePath(fileSubPath + File.separator + uniqueFileName); // 保存相对路径（便于迁移）
        fileInfo.setUploadUserId(uploadUserId); // 前端传递的用户ID
        fileInfo.setRemark(fileUploadDTO.getRemark()); // 前端传递的备注
        fileInfo.setFileSize(fileSize); // 文件大小
        fileInfo.setFileSuffix(fileSuffix); // 文件后缀名

        // 8. 保存到数据库并返回
        return fileInfoRepository.save(fileInfo);
    }

    @Override
    public void deleteFileById(Long fileId) {
        // 1. 查询文件信息
        FileInfo fileInfo = fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在，无法删除（ID：" + fileId + "）"));

        // 2. 构建完整本地文件路径
        File targetFile = new File(fileUploadRootPath + fileInfo.getFilePath());

        // 3. 删除本地文件（若存在）
        if (targetFile.exists()) {
            boolean deleteSuccess = targetFile.delete();
            if (!deleteSuccess) {
                throw new RuntimeException("本地文件删除失败：" + targetFile.getAbsolutePath());
            }
        }

        // 4. 删除数据库记录
        fileInfoRepository.delete(fileInfo);
    }

    @Override
    public FileInfo getFileInfoById(Long fileId) {
        return fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在（ID：" + fileId + "）"));
    }

    @Override
    public List<FileInfo> listFilesByCondition(Long uploadUserId, String fileSuffix) {
        // 1. 处理参数：去除文件后缀的首尾空格，统一格式（避免空字符串干扰）
        String cleanFileSuffix = StringUtils.hasText(fileSuffix) ? fileSuffix.trim() : null;

        // 2. 多条件分支判断（支持4种场景）
        if (uploadUserId != null && uploadUserId > 0 && cleanFileSuffix != null) {
            // 场景1：双重条件过滤（用户ID + 文件后缀）
            return fileInfoRepository.findByUploadUserIdAndFileSuffix(uploadUserId, cleanFileSuffix);
        } else if (uploadUserId != null && uploadUserId > 0) {
            // 场景2：单条件过滤（仅用户ID）
            return fileInfoRepository.findByUploadUserId(uploadUserId);
        } else if (cleanFileSuffix != null) {
            // 场景3：单条件过滤（仅文件后缀）
            return fileInfoRepository.findByFileSuffix(cleanFileSuffix);
        } else {
            // 场景4：无条件查询（所有文件）
            return fileInfoRepository.findAll();
        }
    }

    @Override
    public FileInfo updateFileRemark(Long fileId, String remark) {
        // 1. 查询文件信息
        FileInfo fileInfo = fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在，无法更新备注（ID：" + fileId + "）"));

        // 2. 更新备注
        fileInfo.setRemark(StringUtils.hasText(remark) ? remark : "");

        // 3. 保存到数据库并返回
        return fileInfoRepository.save(fileInfo);
    }

    @Override
    public void downloadFileById(Long fileId, HttpServletResponse response) throws IOException {
        // 1. 根据文件ID查询文件信息
        FileInfo fileInfo = fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在，无法下载（ID：" + fileId + "）"));

        // 2. 构建完整本地文件路径
        String standardRootPath = fileUploadRootPath.endsWith(File.separator)
                ? fileUploadRootPath
                : fileUploadRootPath + File.separator;
        File targetFile = new File(standardRootPath + fileInfo.getFilePath());

        // 3. 校验文件是否存在
        if (!targetFile.exists() || !targetFile.isFile()) {
            throw new RuntimeException("本地文件不存在，无法下载（路径：" + targetFile.getAbsolutePath() + "）");
        }

        // 4. 配置响应头（关键：让浏览器识别为下载文件）
        response.setContentType("application/octet-stream"); // 二进制流格式（支持所有文件类型）
        response.setContentLengthLong(targetFile.length()); // 设置文件大小
        // 处理中文文件名乱码，编码后设置下载文件名（还原原始文件名）
        String encodedFileName = URLEncoder.encode(fileInfo.getFileName(), "UTF-8").replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
        response.setHeader("File-Name", encodedFileName);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition, File-Name"); // 允许前端获取响应头

        // 5. 读取本地文件，写入响应流（供前端下载）
        try (InputStream in = new FileInputStream(targetFile);
             OutputStream out = response.getOutputStream()) {

            byte[] buffer = new byte[1024 * 8]; // 8KB 缓冲区，提升读写效率
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush(); // 刷新流，确保文件完整返回
        } catch (IOException e) {
            throw new RuntimeException("文件流写入失败，无法下载：" + e.getMessage(), e);
        }
    }

    @Override
    public List<FileInfo> listFilesByIds(List<Long> fileIds) {
        // 1. 健壮性处理：若 fileIds 为空/为null，返回空列表（避免查询全表）
        if (CollectionUtils.isEmpty(fileIds)) {
            return new ArrayList<>();
        }

        // 2. 清理无效ID（过滤 <= 0 的ID，避免无效查询）
        List<Long> validFileIds = fileIds.stream()
                .filter(fileId -> fileId != null && fileId > 0)
                .distinct() // 去重，避免重复查询相同ID
                .toList();

        // 3. 若清理后无有效ID，返回空列表
        if (CollectionUtils.isEmpty(validFileIds)) {
            return new ArrayList<>();
        }

        // 4. 调用 Repository 层查询指定ID列表的文件
        return fileInfoRepository.findByFileIdIn(validFileIds);
    }
}
