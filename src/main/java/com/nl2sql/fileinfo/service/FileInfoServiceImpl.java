package com.nl2sql.fileinfo.service;

import com.nl2sql.fileinfo.model.dto.FileUploadDTO;
import com.nl2sql.fileinfo.model.entity.FileInfo;
import com.nl2sql.fileinfo.repository.FileInfoRepository;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        // 1. 操作开始日志：记录上传请求基本信息
        log.info("【文件上传】开始处理文件上传请求，上传用户ID：{}，备注：{}",
                fileUploadDTO.getUploadUserId(), fileUploadDTO.getRemark());

        try {
            // 2. 校验参数
            MultipartFile file = fileUploadDTO.getFile();
            Long uploadUserId = fileUploadDTO.getUploadUserId();
            if (file == null || file.isEmpty()) {
                log.error("【文件上传】上传文件为空，用户ID：{}", uploadUserId);
                throw new RuntimeException("上传文件不能为空");
            }
            if (uploadUserId == null || uploadUserId <= 0) {
                log.error("【文件上传】上传用户ID无效，用户ID：{}", uploadUserId);
                throw new RuntimeException("上传用户ID不能为空且必须为正整数");
            }

            // 3. 获取文件基础信息
            String originalFileName = file.getOriginalFilename(); // 原始文件名（如 a.docx）
            String fileSuffix = StringUtils.getFilenameExtension(originalFileName); // 获取文件后缀（如 docx）
            long fileSize = file.getSize(); // 文件大小（字节）
            String currentDate = dateFormat.format(new Date()); // 当前日期（yyyyMMdd）
            log.info("【文件上传】获取文件基础信息成功，原始文件名：{}，文件后缀：{}，文件大小：{} 字节",
                    originalFileName, fileSuffix, fileSize);

            // 4. 构建文件存储路径
            // 4.1 构建子路径：后缀名/日期（如 docx/20250129）
            String fileSubPath = fileSuffix + File.separator + currentDate;
            // 4.2 构建完整存储目录（根目录 + 子路径）
            File storeDir = new File(fileUploadRootPath + fileSubPath);
            // 4.3 若目录不存在，创建多级目录
            if (!storeDir.exists()) {
                log.info("【文件上传】文件存储目录不存在，开始创建目录：{}", storeDir.getAbsolutePath());
                boolean mkdirsSuccess = storeDir.mkdirs();
                if (!mkdirsSuccess) {
                    log.error("【文件上传】创建文件存储目录失败，目录路径：{}", storeDir.getAbsolutePath());
                    throw new RuntimeException("创建文件存储目录失败：" + storeDir.getAbsolutePath());
                }
                log.info("【文件上传】创建文件存储目录成功，目录路径：{}", storeDir.getAbsolutePath());
            }

            // 5. 构建唯一文件名（避免同名文件覆盖，可选：UUID + 原始文件名）
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmmss");
            String timestamp = LocalDateTime.now().format(formatter);
            String uniqueFileName = timestamp + "_" + originalFileName;
            // 6. 构建完整文件路径（目录 + 唯一文件名）
            File targetFile = new File(storeDir, uniqueFileName);
            log.info("【文件上传】构建唯一文件名成功，唯一文件名：{}，目标文件路径：{}",
                    uniqueFileName, targetFile.getAbsolutePath());

            // 7. 保存文件到本地
            file.transferTo(targetFile);
            log.info("【文件上传】文件保存到本地成功，目标文件路径：{}，文件大小：{} 字节",
                    targetFile.getAbsolutePath(), fileSize);

            // 8. 封装 FileInfo 实体类（准备存入数据库）
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(originalFileName); // 保存原始文件名
            fileInfo.setFilePath(fileSubPath + File.separator + uniqueFileName); // 保存相对路径（便于迁移）
            fileInfo.setUploadUserId(uploadUserId); // 前端传递的用户ID
            fileInfo.setRemark(fileUploadDTO.getRemark()); // 前端传递的备注
            fileInfo.setFileSize(fileSize); // 文件大小
            fileInfo.setFileSuffix(fileSuffix); // 文件后缀名

            // 9. 保存到数据库并返回
            FileInfo savedFileInfo = fileInfoRepository.save(fileInfo);
            log.info("【文件上传】文件信息存入数据库成功，数据库记录ID：{}", savedFileInfo.getFileId()); // 假设文件主键为 id，若为 fileId 替换即可

            return savedFileInfo;
        } catch (Exception e) {
            log.error("【文件上传】文件上传失败，异常信息：{}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFileById(Long fileId) {
        // 1. 操作开始日志
        log.info("【文件删除】开始处理文件删除请求，文件ID：{}", fileId);

        try {
            // 2. 查询文件信息
            FileInfo fileInfo = fileInfoRepository.findById(fileId)
                    .orElseThrow(() -> {
                        log.error("【文件删除】文件不存在，无法删除，文件ID：{}", fileId);
                        return new RuntimeException("文件不存在，无法删除（ID：" + fileId + "）");
                    });
            log.info("【文件删除】查询文件信息成功，文件ID：{}，文件名：{}，文件路径：{}",
                    fileId, fileInfo.getFileName(), fileInfo.getFilePath());

            // 3. 构建完整本地文件路径
            File targetFile = new File(fileUploadRootPath + fileInfo.getFilePath());
            log.info("【文件删除】构建本地文件路径成功，目标文件路径：{}", targetFile.getAbsolutePath());

            // 4. 删除本地文件（若存在）
            if (targetFile.exists()) {
                boolean deleteSuccess = targetFile.delete();
                if (!deleteSuccess) {
                    log.error("【文件删除】本地文件删除失败，目标文件路径：{}", targetFile.getAbsolutePath());
                    throw new RuntimeException("本地文件删除失败：" + targetFile.getAbsolutePath());
                }
                log.info("【文件删除】本地文件删除成功，目标文件路径：{}", targetFile.getAbsolutePath());
            } else {
                log.warn("【文件删除】本地文件不存在，无需删除，目标文件路径：{}", targetFile.getAbsolutePath());
            }

            // 5. 删除数据库记录
            fileInfoRepository.delete(fileInfo);
            log.info("【文件删除】数据库记录删除成功，文件ID：{}", fileId);
        } catch (Exception e) {
            log.error("【文件删除】文件删除失败，文件ID：{}，异常信息：{}", fileId, e.getMessage(), e);
            throw new RuntimeException("文件删除失败：" + e.getMessage(), e);
        }
    }

    @Override
    public FileInfo getFileInfoById(Long fileId) {
        log.info("【文件查询】开始处理文件详情查询请求，文件ID：{}", fileId);

        try {
            FileInfo fileInfo = fileInfoRepository.findById(fileId)
                    .orElseThrow(() -> {
                        log.error("【文件查询】文件不存在，无法查询详情，文件ID：{}", fileId);
                        return new RuntimeException("文件不存在（ID：" + fileId + "）");
                    });

            log.info("【文件查询】文件详情查询成功，文件ID：{}，文件名：{}，上传用户ID：{}",
                    fileId, fileInfo.getFileName(), fileInfo.getUploadUserId());
            return fileInfo;
        } catch (Exception e) {
            log.error("【文件查询】文件详情查询失败，文件ID：{}，异常信息：{}", fileId, e.getMessage(), e);
            throw new RuntimeException("文件查询失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<FileInfo> listFilesByCondition(Long uploadUserId, String fileSuffix) {
        log.info("【文件列表查询】开始处理条件文件列表查询请求，上传用户ID：{}，文件后缀：{}",
                uploadUserId, fileSuffix);

        try {
            // 1. 处理参数：去除文件后缀的首尾空格，统一格式（避免空字符串干扰）
            String cleanFileSuffix = StringUtils.hasText(fileSuffix) ? fileSuffix.trim() : null;
            log.info("【文件列表查询】参数处理完成，清理后文件后缀：{}", cleanFileSuffix);

            // 2. 多条件分支判断（支持4种场景）
            List<FileInfo> fileInfoList;
            if (uploadUserId != null && uploadUserId > 0 && cleanFileSuffix != null) {
                // 场景1：双重条件过滤（用户ID + 文件后缀）
                log.info("【文件列表查询】采用场景1：双重条件过滤（用户ID + 文件后缀）");
                fileInfoList = fileInfoRepository.findByUploadUserIdAndFileSuffix(uploadUserId, cleanFileSuffix);
            } else if (uploadUserId != null && uploadUserId > 0) {
                // 场景2：单条件过滤（仅用户ID）
                log.info("【文件列表查询】采用场景2：单条件过滤（仅用户ID）");
                fileInfoList = fileInfoRepository.findByUploadUserId(uploadUserId);
            } else if (cleanFileSuffix != null) {
                // 场景3：单条件过滤（仅文件后缀）
                log.info("【文件列表查询】采用场景3：单条件过滤（仅文件后缀）");
                fileInfoList = fileInfoRepository.findByFileSuffix(cleanFileSuffix);
            } else {
                // 场景4：无条件查询（所有文件）
                log.info("【文件列表查询】采用场景4：无条件查询（所有文件）");
                fileInfoList = fileInfoRepository.findAll();
            }

            log.info("【文件列表查询】条件文件列表查询成功，返回文件数量：{}", fileInfoList.size());
            return fileInfoList;
        } catch (Exception e) {
            log.error("【文件列表查询】条件文件列表查询失败，上传用户ID：{}，文件后缀：{}，异常信息：{}",
                    uploadUserId, fileSuffix, e.getMessage(), e);
            throw new RuntimeException("文件列表查询失败：" + e.getMessage(), e);
        }
    }

    @Override
    public FileInfo updateFileRemark(Long fileId, String remark) {
        log.info("【文件备注更新】开始处理文件备注更新请求，文件ID：{}，新备注：{}", fileId, remark);

        try {
            // 1. 查询文件信息
            FileInfo fileInfo = fileInfoRepository.findById(fileId)
                    .orElseThrow(() -> {
                        log.error("【文件备注更新】文件不存在，无法更新备注，文件ID：{}", fileId);
                        return new RuntimeException("文件不存在，无法更新备注（ID：" + fileId + "）");
                    });

            // 2. 更新备注
            String newRemark = StringUtils.hasText(remark) ? remark : "";
            fileInfo.setRemark(newRemark);
            log.info("【文件备注更新】文件备注赋值完成，文件ID：{}，更新后备注：{}", fileId, newRemark);

            // 3. 保存到数据库并返回
            FileInfo updatedFileInfo = fileInfoRepository.save(fileInfo);
            log.info("【文件备注更新】文件备注更新成功，文件ID：{}，数据库记录已同步", fileId);

            return updatedFileInfo;
        } catch (Exception e) {
            log.error("【文件备注更新】文件备注更新失败，文件ID：{}，异常信息：{}", fileId, e.getMessage(), e);
            throw new RuntimeException("文件备注更新失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void downloadFileById(Long fileId, HttpServletResponse response) throws IOException {
        log.info("【文件下载】开始处理文件下载请求，文件ID：{}", fileId);

        try {
            // 1. 根据文件ID查询文件信息
            FileInfo fileInfo = fileInfoRepository.findById(fileId)
                    .orElseThrow(() -> {
                        log.error("【文件下载】文件不存在，无法下载，文件ID：{}", fileId);
                        return new RuntimeException("文件不存在，无法下载（ID：" + fileId + "）");
                    });
            log.info("【文件下载】查询文件信息成功，文件ID：{}，文件名：{}，文件路径：{}",
                    fileId, fileInfo.getFileName(), fileInfo.getFilePath());

            // 2. 构建完整本地文件路径
            String standardRootPath = fileUploadRootPath.endsWith(File.separator)
                    ? fileUploadRootPath
                    : fileUploadRootPath + File.separator;
            File targetFile = new File(standardRootPath + fileInfo.getFilePath());
            log.info("【文件下载】构建标准本地文件路径成功，目标文件路径：{}", targetFile.getAbsolutePath());

            // 3. 校验文件是否存在
            if (!targetFile.exists() || !targetFile.isFile()) {
                log.error("【文件下载】本地文件不存在，无法下载，目标文件路径：{}", targetFile.getAbsolutePath());
                throw new RuntimeException("本地文件不存在，无法下载（路径：" + targetFile.getAbsolutePath() + "）");
            }
            log.info("【文件下载】本地文件校验通过，文件大小：{} 字节", targetFile.length());

            // 4. 配置响应头（关键：让浏览器识别为下载文件）
            response.setContentType("application/octet-stream"); // 二进制流格式（支持所有文件类型）
            response.setContentLengthLong(targetFile.length()); // 设置文件大小
            // 处理中文文件名乱码，编码后设置下载文件名（还原原始文件名）
            String encodedFileName = URLEncoder.encode(fileInfo.getFileName(), "UTF-8").replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setHeader("File-Name", encodedFileName);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition, File-Name"); // 允许前端获取响应头
            log.info("【文件下载】响应头配置完成，下载文件名（编码后）：{}", encodedFileName);

            // 5. 读取本地文件，写入响应流（供前端下载）
            try (InputStream in = new FileInputStream(targetFile);
                 OutputStream out = response.getOutputStream()) {

                byte[] buffer = new byte[1024 * 8]; // 8KB 缓冲区，提升读写效率
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush(); // 刷新流，确保文件完整返回
            }

            log.info("【文件下载】文件流写入响应成功，文件ID：{}，文件名：{}，下载完成",
                    fileId, fileInfo.getFileName());
        } catch (Exception e) {
            log.error("【文件下载】文件下载失败，文件ID：{}，异常信息：{}", fileId, e.getMessage(), e);
            throw new RuntimeException("文件下载失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<FileInfo> listFilesByIds(List<Long> fileIds) {
        log.info("【文件ID列表查询】开始处理文件ID列表查询请求，传入文件ID列表：{}", fileIds);

        try {
            // 1. 健壮性处理：若 fileIds 为空/为null，返回空列表（避免查询全表）
            if (CollectionUtils.isEmpty(fileIds)) {
                log.info("【文件ID列表查询】传入文件ID列表为空，直接返回空列表");
                return new ArrayList<>();
            }

            // 2. 清理无效ID（过滤 <= 0 的ID，避免无效查询）
            List<Long> validFileIds = fileIds.stream()
                    .filter(fileId -> fileId != null && fileId > 0)
                    .distinct() // 去重，避免重复查询相同ID
                    .toList();
            log.info("【文件ID列表查询】无效ID过滤完成，原始ID数量：{}，有效ID数量：{}，有效ID列表：{}",
                    fileIds.size(), validFileIds.size(), validFileIds);

            // 3. 若清理后无有效ID，返回空列表
            if (CollectionUtils.isEmpty(validFileIds)) {
                log.info("【文件ID列表查询】过滤后无有效文件ID，直接返回空列表");
                return new ArrayList<>();
            }

            // 4. 调用 Repository 层查询指定ID列表的文件
            List<FileInfo> fileInfoList = fileInfoRepository.findByFileIdIn(validFileIds);
            log.info("【文件ID列表查询】文件ID列表查询成功，有效ID数量：{}，返回文件数量：{}",
                    validFileIds.size(), fileInfoList.size());

            return fileInfoList;
        } catch (Exception e) {
            log.error("【文件ID列表查询】文件ID列表查询失败，传入文件ID列表：{}，异常信息：{}",
                    fileIds, e.getMessage(), e);
            throw new RuntimeException("文件ID列表查询失败：" + e.getMessage(), e);
        }
    }
}