package com.nl2sql.repository;

import com.nl2sql.model.entity.DatabaseOverview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据库概览Repository
 */
@Repository
public interface DatabaseOverviewRepository extends JpaRepository<DatabaseOverview, Integer> {

    /**
     * 按数据库名称查找
     */
    Optional<DatabaseOverview> findByDatabaseName(String databaseName);

    Optional<DatabaseOverview> findByOwnerUserIdAndHostConfigIdAndDatabaseName(
        Integer ownerUserId,
        Integer hostConfigId,
        String databaseName
    );

    /**
     * 查找所有激活的数据库
     */
    List<DatabaseOverview> findByIsActiveTrue();

    List<DatabaseOverview> findByOwnerUserIdAndIsActiveTrue(Integer ownerUserId);

    List<DatabaseOverview> findByOwnerUserIdAndDatabaseNameInAndIsActiveTrue(
        Integer ownerUserId,
        List<String> databaseNames
    );

    /**
     * 删除数据库概览
     */
    void deleteByDatabaseName(String databaseName);

    void deleteByOwnerUserIdAndHostConfigIdAndDatabaseName(
        Integer ownerUserId,
        Integer hostConfigId,
        String databaseName
    );
}
