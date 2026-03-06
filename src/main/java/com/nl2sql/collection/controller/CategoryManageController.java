package com.nl2sql.collection.controller;

import com.nl2sql.collection.model.dto.CategoryInfoDTO;
import com.nl2sql.collection.model.dto.CollectionTreeDTO;
import com.nl2sql.collection.model.entity.CategoryInfo;
import com.nl2sql.collection.service.manage.CategoryInfoService;
import com.nl2sql.model.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 *  收藏管理接口
 * </p>
 *
 * @author Ayaki
 * @since 2026/1/29 15:43
 */
@RestController
@RequestMapping("/collection/manage")
@Tag(name = "收藏管理接口", description = "收藏管理接口")
public class CategoryManageController {

    @Autowired
    private CategoryInfoService categoryInfoService;

    /**
     * 新增目录
     * @param categoryInfoDTO 新增目录参数（@Validated开启参数校验）
     * @return 新增结果（直接返回ApiResponse格式）
     */
    @PostMapping("/add")
    @Operation(summary = "新增目录")
    public ApiResponse<CategoryInfo> addCategory(@Validated @RequestBody CategoryInfoDTO categoryInfoDTO) {
        try {
            CategoryInfo categoryInfo = categoryInfoService.addCategory(categoryInfoDTO);
            // 成功：返回操作成功+新增的目录数据
            return ApiResponse.success("新增目录成功", categoryInfo);
        } catch (Exception e) {
            // 失败：返回错误信息
            return ApiResponse.error("新增目录失败", e.getMessage());
        }
    }

    /**
     * 根据ID删除目录
     * @param categoryId 目录ID（路径参数）
     * @return 删除结果（直接返回ApiResponse格式）
     */
    @DeleteMapping("/delete/{categoryId}")
    @Operation(summary = "删除目录")
    public ApiResponse<Void> deleteCategoryById(@PathVariable Long categoryId) {
        try {
            categoryInfoService.deleteCategoryById(categoryId);
            // 成功：返回操作成功提示，无业务数据（用Void表示无数据）
            return ApiResponse.success("目录删除成功（目录ID：" + categoryId + "）", null);
        } catch (Exception e) {
            // 失败：返回错误信息
            return ApiResponse.error("删除目录失败", e.getMessage());
        }
    }

    /**
     * 修改目录
     * @param categoryInfoDTO 修改目录参数（@Validated开启参数校验）
     * @return 修改结果（直接返回ApiResponse格式）
     */
    @PutMapping("/update")
    @Operation(summary = "更新目录")
    public ApiResponse<CategoryInfo> updateCategory(@Validated @RequestBody CategoryInfoDTO categoryInfoDTO) {
        try {
            CategoryInfo categoryInfo = categoryInfoService.updateCategory(categoryInfoDTO);
            // 成功：返回操作成功+修改后的目录数据
            return ApiResponse.success("更新目录成功", categoryInfo);
        } catch (Exception e) {
            // 失败：返回错误信息
            return ApiResponse.error("更新目录失败", e.getMessage());
        }
    }

    /**
     * 新增：查询目录列表（支持查询所有目录，也可扩展条件查询）
     * @return 目录列表数据（直接返回ApiResponse格式）
     */
    @GetMapping("/list")
    @Operation(summary = "查询目录列表", description = "查询所有目录数据，返回完整列表")
    public ApiResponse<List<CategoryInfo>> queryCategoryList(
            @RequestParam(required = true) Integer userId,
            @RequestParam(required = false) String categoryGroup ) {
        try {
            // 调用服务层方法，查询所有目录列表
            List<CategoryInfo> categoryList = categoryInfoService.queryCategoryListByGroup(userId, categoryGroup);
            // 成功：返回操作成功+目录列表数据（如果列表为空，也返回空列表，避免null）
            return ApiResponse.success("查询目录列表成功", categoryList);
        } catch (Exception e) {
            // 失败：返回错误信息
            return ApiResponse.error("查询目录列表失败", e.getMessage());
        }
    }

    /**
     * 获取收藏树形结构列表（供前端Element-UI渲染）
     * @param userId 用户ID（必填，用于查询该用户的收藏）
     * @return 树形结构列表数据（直接返回ApiResponse格式）
     */
    @GetMapping("/tree/list")
    @Operation(summary = "获取收藏树形结构列表", description = "查询指定用户的收藏目录+收藏项树形结构，供Element-UI Tree组件渲染")
    public ApiResponse<List<CollectionTreeDTO>> getCollectionTreeList(
            @RequestParam(required = true) Integer userId
    ) {
        try {
            // 调用扩展后的Service方法（需同步修改Service层支持categoryGroup参数）
            List<CollectionTreeDTO> collectionTreeList = categoryInfoService.getCollectionTreeList(userId);
            return ApiResponse.success("获取收藏树形结构列表成功", collectionTreeList);
        } catch (Exception e) {
            return ApiResponse.error("获取收藏树形结构列表失败", e.getMessage());
        }
    }
}
