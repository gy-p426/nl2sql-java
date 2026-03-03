package com.nl2sql.collection.service.manage;

import com.nl2sql.collection.model.dto.CategoryInfoDTO;
import com.nl2sql.collection.model.dto.CollectionTreeDTO;
import com.nl2sql.collection.model.entity.CategoryInfo;
import com.nl2sql.collection.model.entity.UserFavorites;
import com.nl2sql.collection.repository.CategoryInfoRepository;
import com.nl2sql.collection.repository.UserFavoritesRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 *
 * </p>
 *
 * @author Ayaki
 * @since 2026/1/29 15:39
 */
@Service  // 标记为业务层组件
public class CategoryInfoServiceImpl implements CategoryInfoService{

    private static final String CATEGORY_GROUP_COLLECTION = "favorite";

    @Autowired
    private CategoryInfoRepository categoryInfoRepository;

    @Autowired
    private UserFavoritesRepository userFavoritesRepository;

    /**
     * 新增目录
     */
    @Override
    public CategoryInfo addCategory(CategoryInfoDTO categoryInfoDTO) {

        if (categoryInfoDTO.getCategoryId() != null) {
            throw new IllegalArgumentException("新增目录时不应该传递categoryId参数，请移除该字段后重试。");
        }

        // 1. DTO转换为实体类
        CategoryInfo categoryInfo = new CategoryInfo();
        BeanUtils.copyProperties(categoryInfoDTO, categoryInfo);

        // 2. 保存到数据库（JPA的save方法：新增时无主键，自动执行insert）
        return categoryInfoRepository.save(categoryInfo);
    }

    /**
     * 根据ID删除目录
     */
    @Override
    public void deleteCategoryById(Long categoryId) {
        if(categoryId == null || categoryId <= 0L){
            throw new RuntimeException("目录ID格式错误");
        }
        // 1. 校验目录是否存在（可选，若不存在直接删除会抛异常，这里做友好处理）
        Optional<CategoryInfo> optionalCategory = categoryInfoRepository.findById(categoryId);
        if (optionalCategory.isEmpty()) {
            throw new RuntimeException("目录不存在，无法删除（目录ID：" + categoryId + "）");
        }

        // 2. 根据ID删除（JPA的deleteById方法）
        categoryInfoRepository.deleteById(categoryId);
    }

    /**
     * 修改目录
     */
    @Override
    public CategoryInfo updateCategory(CategoryInfoDTO categoryInfoDTO) {
        // 1. 获取要修改的目录ID
        Long categoryId = categoryInfoDTO.getCategoryId();
        if (categoryId == null) {
            throw new RuntimeException("修改目录时，目录ID不能为空");
        }

        // 2. 校验目录是否存在
        Optional<CategoryInfo> optionalCategory = categoryInfoRepository.findById(categoryId);
        if (optionalCategory.isEmpty()) {
            throw new RuntimeException("目录不存在，无法修改（目录ID：" + categoryId + "）");
        }

        // 3. DTO属性复制到已存在的实体类（保留数据库中的createTime、updateTime等字段）
        // 排除categoryId（主键不可改）、createTime（创建时间不可改）
        CategoryInfo existingCategory = optionalCategory.get();
        BeanUtils.copyProperties(categoryInfoDTO, existingCategory, "categoryId", "createTime");

        // 4. 保存到数据库（JPA的save方法：有主键时，自动执行update）
        return categoryInfoRepository.save(existingCategory);
    }

    /**
     * 按用户ID + 目录组别查询目录列表（新增userId过滤）
     * @param userId 用户ID（必填，目录归属用户）
     * @param categoryGroup 目录组别（可选，不传则查询该用户所有目录）
     * @return 该用户的目录列表
     */
    @Override
    public List<CategoryInfo> queryCategoryListByGroup(Integer userId, String categoryGroup) {
        // 1. 校验userId必填（避免查询所有用户的目录）
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        // 2. 逻辑判断：传参categoryGroup则按「用户+组别」过滤，不传则查「用户所有目录」
        if (categoryGroup != null && !categoryGroup.trim().isEmpty()) {
            // 按用户ID + 目录组别过滤查询
            return categoryInfoRepository.findByUserIdAndCategoryGroup(userId, categoryGroup.trim());
        } else {
            // 仅按用户ID查询所有目录
            return categoryInfoRepository.findByUserId(userId);
        }
    }

    @Override
    public List<CollectionTreeDTO> getCollectionTreeList(Integer userId) {
        // 步骤1：查询所有收藏组别的目录
        List<CategoryInfo> allCategoryList = categoryInfoRepository.findByCategoryGroup(CATEGORY_GROUP_COLLECTION);
        if (CollectionUtils.isEmpty(allCategoryList)) {
            return new ArrayList<>();
        }

        // 步骤2：查询该用户所有未删除的收藏
        List<UserFavorites> userFavoritesList = userFavoritesRepository.findByUserIdAndDeleted(userId, false);
        if (CollectionUtils.isEmpty(userFavoritesList)) {
            // 无收藏时，仅返回目录树形结构
            return buildCategoryTree(0L, allCategoryList, new ArrayList<>());
        }

        // 步骤3：递归组装目录+收藏树形结构（根目录父ID=0）
        return buildCategoryTree(0L, allCategoryList, userFavoritesList);
    }

    /**
     * 递归组装目录树形结构，并挂载对应收藏
     * @param parentCategoryId 父目录ID
     * @param allCategoryList 所有目录列表
     * @param userFavoritesList 用户收藏列表
     * @return 组装完成的树形节点列表
     */
    private List<CollectionTreeDTO> buildCategoryTree(Long parentCategoryId,
                                                      List<CategoryInfo> allCategoryList,
                                                      List<UserFavorites> userFavoritesList) {
        // 步骤1：筛选当前父目录下的所有子目录
        List<CategoryInfo> currentChildCategoryList = allCategoryList.stream()
                .filter(category -> parentCategoryId.equals(category.getParentCategoryId()))
                .toList();

        // 步骤2：初始化返回结果列表
        List<CollectionTreeDTO> treeNodeList = new ArrayList<>();

        // 步骤3：遍历当前子目录，逐个组装树形节点
        for (CategoryInfo category : currentChildCategoryList) {

            // 3.1：创建目录节点
            CollectionTreeDTO categoryTreeNode = CollectionTreeDTO.getCategoryTreeDTO(
                    category.getCategoryId(),
                    category.getCategoryName(),
                    category.getCategoryGroup()
            );

            // 补充目录的其他扩展字段（按需）
            categoryTreeNode.setCategoryLevel(category.getCategoryLevel());
            categoryTreeNode.setParentCategoryId(category.getParentCategoryId());

            // 3.2：递归查询当前目录的子目录，组装子节点（目录的子目录）
            List<CollectionTreeDTO> childCategoryTreeNodeList = buildCategoryTree(
                    category.getCategoryId(),
                    allCategoryList,
                    userFavoritesList
            );

            // 3.3：筛选当前目录下的所有收藏，组装收藏节点
            List<CollectionTreeDTO> favoriteTreeNodeList = userFavoritesList.stream()
                    .filter(favorite -> category.getCategoryId().equals(favorite.getCategoryId()))
                    .map(favorite -> CollectionTreeDTO.getFavoriteTreeDTO(
                            favorite.getId(),
                            favorite.getFavoriteName(),
                            favorite.getCategoryId()
                    ))
                    .toList();

            // 3.4：合并当前目录的「子目录节点」和「收藏节点」作为当前节点的children
            List<CollectionTreeDTO> allChildrenNodeList = new ArrayList<>();
            allChildrenNodeList.addAll(childCategoryTreeNodeList); // 先加子目录（目录在前）
            allChildrenNodeList.addAll(favoriteTreeNodeList); // 再加收藏（收藏在后）
            categoryTreeNode.setChildren(CollectionUtils.isEmpty(allChildrenNodeList) ? null : allChildrenNodeList);

            // 3.5：将当前目录节点加入返回列表
            treeNodeList.add(categoryTreeNode);
        }

        // 步骤4：返回当前层级的树形节点列表
        return treeNodeList;
    }

}
