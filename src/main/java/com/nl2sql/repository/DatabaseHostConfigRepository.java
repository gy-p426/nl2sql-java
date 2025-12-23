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
     * 查找所有激活的主机配置
     */
    List<DatabaseHostConfig> findByIsActiveTrue();

    /**
     * 根据名称删除
     */
    void deleteByName(String name);
}
