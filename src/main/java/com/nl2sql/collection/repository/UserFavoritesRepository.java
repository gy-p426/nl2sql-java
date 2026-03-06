package com.nl2sql.collection.repository;

import com.nl2sql.collection.model.entity.UserFavorites;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户收藏 Repository
 * @author Ayaki
 * @since 2026/2/5
 */
@Repository
public interface UserFavoritesRepository extends JpaRepository<UserFavorites, Long> {

    /**
     * 按用户ID和目录ID查询未删除的收藏列表
     * @param userId 用户ID
     * @param categoryId 目录ID
     * @param deleted 是否删除（false=未删除）
     * @return 对应目录下的用户收藏列表
     */
    List<UserFavorites> findByUserIdAndCategoryIdAndDeleted(Integer userId, Long categoryId, Boolean deleted);

    /**
     * 按用户ID查询所有未删除的收藏列表（用于批量关联目录）
     * @param userId 用户ID
     * @param deleted 是否删除（false=未删除）
     * @return 用户所有未删除的收藏
     */
    List<UserFavorites> findByUserIdAndDeleted(Integer userId, Boolean deleted);
}