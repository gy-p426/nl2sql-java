package com.nl2sql.collection.service.manage;

import com.nl2sql.collection.model.dto.CategoryInfoDTO;
import com.nl2sql.collection.model.dto.CollectionTreeDTO;
import com.nl2sql.collection.model.entity.CategoryInfo;

import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author Ayaki
 * @since 2026/1/29 15:39
 */
public interface CategoryInfoService {

    /**
     * 新增目录
     * @param categoryInfoDTO 新增目录请求参数
     * @return 新增成功的目录实体
     */
    CategoryInfo addCategory(CategoryInfoDTO categoryInfoDTO);

    /**
     * 根据ID删除目录
     * @param categoryId 目录ID
     */
    void deleteCategoryById(Long categoryId);

    /**
     * 修改目录
     * @param categoryInfoDTO 修改目录请求参数
     * @return 修改成功的目录实体
     */
    CategoryInfo updateCategory(CategoryInfoDTO categoryInfoDTO);

    List<CategoryInfo> queryCategoryListByGroup(Integer userId, String categoryGroup);

    /**
     * 获取收藏树形结构列表（供前端Element-UI渲染）
     * @param userId 用户ID（用于查询该用户的收藏）
     * @return 树形结构列表
     */
    List<CollectionTreeDTO> getCollectionTreeList(Integer userId);
}
