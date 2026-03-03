package com.nl2sql.collection.model.dto;

import com.nl2sql.collection.model.entity.CategoryInfo;
import com.nl2sql.collection.model.entity.UserFavorites;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 树形节点DTO
 * @author Ayaki
 * @since 2026/2/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionTreeDTO {
    // 核心字段： 用于组织树形结构
    private String id; // 节点唯一标识（目录：category_${categoryId}，收藏：favorite_${id}，避免ID冲突）
    private String label; // 节点显示名称（目录名/收藏名）
    private List<CollectionTreeDTO> children; // 子节点（目录的子目录/目录下的收藏）

    // 扩展字段：区分节点类型（目录/收藏）
    private String nodeType; // "CATEGORY" 目录，"FAVORITE" 收藏

    // 扩展字段：目录专属属性（按需传递给前端）
    private Long categoryId; // 目录ID
    private String categoryName;
    private Integer categoryLevel; // 目录级别
    private Long parentCategoryId; // 父目录ID
    private String categoryGroup; // 目录组别

    // 扩展字段：收藏专属属性（按需传递给前端）
    private Long favoriteId; // 收藏ID
    private String favoriteName; // 收藏名称（冗余，与label一致，方便前端直接使用）


    /**
     * 构建目录树形节点（静态工厂方法）
     * @param categoryId 目录ID
     * @param categoryName 目录名称
     * @param categoryGroup 目录组别
     * @return 目录树形节点DTO
     */
    public static CollectionTreeDTO getCategoryTreeDTO(Long categoryId, String categoryName, String categoryGroup) {
        CollectionTreeDTO categoryNode = new CollectionTreeDTO();
        // 目录节点核心配置
        categoryNode.setNodeType("CATEGORY");
        categoryNode.setId("category_" + categoryId);
        categoryNode.setCategoryId(categoryId);
        categoryNode.setLabel(categoryName);
        categoryNode.setCategoryName(categoryName);
        categoryNode.setCategoryGroup(categoryGroup);
        // 初始化空children，避免前端报null
        categoryNode.setChildren(List.of());
        return categoryNode;
    }

    public static CollectionTreeDTO convertToCategoryTreeDTO(CategoryInfo category) {
        CollectionTreeDTO categoryNode = new CollectionTreeDTO();
        // 目录节点核心配置
        Long categoryId = category.getCategoryId();
        String categoryName = category.getCategoryName();

        categoryNode.setNodeType("CATEGORY");
        categoryNode.setId("category_" + categoryId);
        categoryNode.setCategoryId(categoryId);
        categoryNode.setLabel(categoryName);
        categoryNode.setCategoryName(categoryName);
        categoryNode.setCategoryGroup(category.getCategoryGroup());
        categoryNode.setParentCategoryId(category.getParentCategoryId());
        categoryNode.setCategoryLevel(category.getCategoryLevel());

        // 初始化空children，避免前端报null
        categoryNode.setChildren(List.of());
        return categoryNode;
    }

    /**
     * 构建收藏树形节点（静态工厂方法）
     * @param favoriteId 收藏ID（对应user_favorites表的id，Integer类型）
     * @param favoriteName 收藏名称
     * @param categoryId 关联的目录ID
     * @return 收藏树形节点DTO
     */
    public static CollectionTreeDTO getFavoriteTreeDTO(Long favoriteId, String favoriteName, Long categoryId) {
        CollectionTreeDTO favoriteNode = new CollectionTreeDTO();
        // 收藏节点核心配置
        favoriteNode.setNodeType("FAVORITE");
        favoriteNode.setId("favorite_" + favoriteId);
        favoriteNode.setLabel(favoriteName);
        favoriteNode.setFavoriteId(favoriteId);
        favoriteNode.setFavoriteName(favoriteName);
        favoriteNode.setCategoryId(categoryId);
        // 收藏节点无子节点，设为null（Element-UI 自动识别为叶子节点）
        favoriteNode.setChildren(null);
        return favoriteNode;
    }


    public static CollectionTreeDTO convertToFavoriteTreeDTO(UserFavorites favorite) {
        CollectionTreeDTO favoriteNode = new CollectionTreeDTO();
        // 收藏节点核心配置
        Long favoriteId = favorite.getId();
        String favoriteName = favorite.getFavoriteName();

        favoriteNode.setNodeType("FAVORITE");
        favoriteNode.setId("favorite_" + favoriteId);
        favoriteNode.setLabel(favoriteName);
        favoriteNode.setFavoriteId(favoriteId);
        favoriteNode.setFavoriteName(favoriteName);
        favoriteNode.setCategoryId(favorite.getCategoryId());
        // 收藏节点无子节点，设为null（Element-UI 自动识别为叶子节点）
        favoriteNode.setChildren(null);
        return favoriteNode;
    }
}