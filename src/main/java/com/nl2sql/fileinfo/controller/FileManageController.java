package com.nl2sql.fileinfo.controller;

import com.nl2sql.fileinfo.model.dto.FileInfoDTO;
import com.nl2sql.fileinfo.model.dto.FileUploadDTO;
import com.nl2sql.fileinfo.model.entity.FileInfo;
import com.nl2sql.fileinfo.service.FileInfoService;
import com.nl2sql.model.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件管理控制器
 * @author Ayaki
 * @since 2026/01/29
 */
@RestController
@RequestMapping("/file/manage")
@Tag(name = "文件管理接口", description = "文件管理接口")
public class FileManageController {

    private final FileInfoService fileInfoService;

    public FileManageController(FileInfoService fileInfoService) {
        this.fileInfoService = fileInfoService;
    }

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "将文件保存到本地（按后缀+日期分路径），并记录到数据库")
    public ApiResponse<FileInfoDTO> uploadFile(FileUploadDTO fileUploadDTO) {
        try {
            FileInfo fileInfo = fileInfoService.uploadFile(fileUploadDTO);
            // 转换为返回DTO
            FileInfoDTO fileInfoDTO = new FileInfoDTO();
            BeanUtils.copyProperties(fileInfo, fileInfoDTO);
            return ApiResponse.success("文件上传成功", fileInfoDTO);
        } catch (Exception e) {
            return ApiResponse.error("文件上传失败", e.getMessage());
        }
    }

        /**
         * 根据文件ID删除文件
         */
        @DeleteMapping("/delete/{fileId}")
        @Operation(summary = "删除文件", description = "删除本地文件及数据库中的记录")
        public ApiResponse<Void> deleteFileById(@PathVariable Long fileId) {
            try {
                fileInfoService.deleteFileById(fileId);
                return ApiResponse.success("文件删除成功（ID：" + fileId + "）", null);
            } catch (Exception e) {
                return ApiResponse.error("文件删除失败", e.getMessage());
            }
        }

    /**
     * 根据文件ID查询文件信息
     */
    @GetMapping("/detail/{fileId}")
    @Operation(summary = "查询文件详情", description = "根据文件ID查询文件完整信息")
    public ApiResponse<FileInfoDTO> getFileInfoById(@PathVariable Long fileId) {
        try {
            FileInfo fileInfo = fileInfoService.getFileInfoById(fileId);
            // 转换为返回DTO
            FileInfoDTO fileInfoDTO = new FileInfoDTO();
            BeanUtils.copyProperties(fileInfo, fileInfoDTO);
            return ApiResponse.success("查询文件详情成功", fileInfoDTO);
        } catch (Exception e) {
            return ApiResponse.error("查询文件详情失败", e.getMessage());
        }
    }

    /**
     * 查询所有文件列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询所有文件", description = "返回所有上传文件的列表信息")
    public ApiResponse<List<FileInfoDTO>> listAllFiles(@RequestParam(required = false) Long uploadUserId,
                                                       @RequestParam(required = false) String fileSuffix) {
        try {
            List<FileInfo> fileInfoList = fileInfoService.listFilesByCondition(uploadUserId, fileSuffix);
            // 转换为返回DTO列表
            List<FileInfoDTO> fileInfoDTOList = fileInfoList.stream()
                    .map(fileInfo -> {
                        FileInfoDTO dto = new FileInfoDTO();
                        BeanUtils.copyProperties(fileInfo, dto);
                        return dto;
                    })
                    .collect(Collectors.toList());
            return ApiResponse.success("查询文件列表成功", fileInfoDTOList);
        } catch (Exception e) {
            return ApiResponse.error("查询文件列表失败", e.getMessage());
        }
    }

    /**
     * 更新文件备注
     */
    @PutMapping("/update/remark/{fileId}")
    @Operation(summary = "更新文件备注", description = "仅更新文件的备注信息")
    public ApiResponse<FileInfoDTO> updateFileRemark(
            @PathVariable Long fileId,
            @RequestParam String remark
    ) {
        try {
            FileInfo fileInfo = fileInfoService.updateFileRemark(fileId, remark);
            // 转换为返回DTO
            FileInfoDTO fileInfoDTO = new FileInfoDTO();
            BeanUtils.copyProperties(fileInfo, fileInfoDTO);
            return ApiResponse.success("文件备注更新成功", fileInfoDTO);
        } catch (Exception e) {
            return ApiResponse.error("文件备注更新失败", e.getMessage());
        }
    }

    /**
     * 根据文件ID下载文件
     */
    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载文件", description = "根据文件ID下载本地文件（还原原始文件名）")
    public void downloadFileById(
            @PathVariable Long fileId,
            HttpServletResponse response
    ) throws IOException {
        try {
            fileInfoService.downloadFileById(fileId, response);
        } catch (Exception e) {
            // 若出现异常，向前端返回错误信息（通过响应流写入）
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
