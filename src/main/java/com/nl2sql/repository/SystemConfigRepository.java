package com.nl2sql.repository;

import com.nl2sql.model.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置Repository
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Integer> {

    /**
     * 根据配置键查找
     */
    Optional<SystemConfig> findByConfigKey(String configKey);

    /**
     * 根据分类查找所有配置
     */
    List<SystemConfig> findByCategory(String category);

    /**
     * 根据配置键前缀查找
     */
    List<SystemConfig> findByConfigKeyStartingWith(String prefix);
}
