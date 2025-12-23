package com.nl2sql.repository;

import com.nl2sql.model.entity.DatabaseSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据库Schema Repository
 */
@Repository
public interface DatabaseSchemaRepository extends JpaRepository<DatabaseSchema, Integer> {
    
    /**
     * 查找数据库的所有表
     */
    List<DatabaseSchema> findByDatabaseName(String databaseName);
    
    /**
     * 查找特定表
     */
    Optional<DatabaseSchema> findByDatabaseNameAndTableName(String databaseName, String tableName);
    
    /**
     * 删除数据库的所有表
     */
    void deleteByDatabaseName(String databaseName);
    
    /**
     * 获取所有数据库名称
     */
    @Query("SELECT DISTINCT s.databaseName FROM DatabaseSchema s ORDER BY s.databaseName")
    List<String> findAllDatabaseNames();
}
