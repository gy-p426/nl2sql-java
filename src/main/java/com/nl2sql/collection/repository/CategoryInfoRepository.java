package com.nl2sql.collection.repository;

import com.nl2sql.collection.model.entity.CategoryInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author Ayaki
 * @since 2026/1/29 15:38
 */
@Repository
public interface CategoryInfoRepository extends JpaRepository<CategoryInfo, Long> {

    /**
     * 按目录组别（categoryGroup）查询目录列表（Spring Data JPA 方法名命名查询）
     * @param categoryGroup 目录组别
     * @return 对应组别的目录列表
     */
    List<CategoryInfo> findByCategoryGroup(String categoryGroup);

    /**
     * 按用户ID查询所有目录（核心新增）
     * @param userId 用户ID
     * @return 该用户的所有目录列表
     */
    List<CategoryInfo> findByUserId(Integer userId);

    /**
     * 按用户ID + 目录组别查询目录列表（核心新增）
     * @param userId 用户ID
     * @param categoryGroup 目录组别
     * @return 该用户对应组别的目录列表
     */
    List<CategoryInfo> findByUserIdAndCategoryGroup(Integer userId, String categoryGroup);


    /**
     * 按父目录ID查询子目录列表
     * @param parentCategoryId 父目录ID
     * @return 子目录列表
     */
    List<CategoryInfo> findByParentCategoryId(Long parentCategoryId);

    /**
     * 按父目录ID和目录组别查询子目录列表
     * @param parentCategoryId 父目录ID
     * @param categoryGroup 目录组别
     * @return 对应组别的子目录列表
     */
    List<CategoryInfo> findByParentCategoryIdAndCategoryGroup(Long parentCategoryId, String categoryGroup);
}

