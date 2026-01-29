package com.excelmanage.repository;

import com.excelmanage.model.entity.ExcelManageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Excel管理仓库接口
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Repository
public interface ExcelManageRepository extends JpaRepository<ExcelManageEntity, Integer> {
    
    /**
     * 根据名称查找
     * 
     * @param name 名称
     * @return Excel管理实体
     */
    Optional<ExcelManageEntity> findByName(String name);
}
