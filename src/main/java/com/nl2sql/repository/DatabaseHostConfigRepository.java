package com.nl2sql.repository;

import com.nl2sql.model.entity.DatabaseHostConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据库主机配置Repository
 */
@Repository
public interface DatabaseHostConfigRepository extends JpaRepository<DatabaseHostConfig, Integer> {

    /**
     * 根据名称查找
     */
    Optional<DatabaseHostConfig> findByName(String name);

    /**
     * 根据用户ID和名称查找
     */
    Optional<DatabaseHostConfig> findByOwnerUserIdAndName(Integer ownerUserId, String name);

    /**
     * 查找所有激活的主机配置
     */
    List<DatabaseHostConfig> findByIsActiveTrue();

    /**
     * 查找指定用户所有激活的主机配置
     */
    List<DatabaseHostConfig> findByOwnerUserIdAndIsActiveTrue(Integer ownerUserId);

    /**
     * 查找owner为空的激活配置（迁移过渡期）
     */
    List<DatabaseHostConfig> findByOwnerUserIdIsNullAndIsActiveTrue();

    /**
     * 根据名称删除
     */
    void deleteByName(String name);

    /**
     * 根据用户ID和名称删除
     */
    void deleteByOwnerUserIdAndName(Integer ownerUserId, String name);

    /**
     * 判断用户是否拥有指定名称配置
     */
    boolean existsByOwnerUserIdAndName(Integer ownerUserId, String name);
}
