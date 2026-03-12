package com.nl2sql.repository;

import com.nl2sql.model.entity.CustomAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 自定义注释Repository
 */
@Repository
public interface CustomAnnotationRepository extends JpaRepository<CustomAnnotation, Integer> {
    
    /**
     * 查找数据库的所有注释
     */
    List<CustomAnnotation> findByDatabaseName(String databaseName);
    
    /**
     * 查找表的所有注释
     */
    List<CustomAnnotation> findByDatabaseNameAndTableName(String databaseName, String tableName);

    List<CustomAnnotation> findByOwnerUserIdAndDatabaseName(Integer ownerUserId, String databaseName);

    List<CustomAnnotation> findByOwnerUserIdAndDatabaseNameAndTableName(
        Integer ownerUserId, String databaseName, String tableName);
    
    /**
     * 查找表注释
     */
    Optional<CustomAnnotation> findByDatabaseNameAndTableNameAndColumnNameIsNull(
        String databaseName, String tableName);

    Optional<CustomAnnotation> findByOwnerUserIdAndDatabaseNameAndTableNameAndColumnNameIsNull(
        Integer ownerUserId, String databaseName, String tableName);
    
    /**
     * 查找列注释
     */
    Optional<CustomAnnotation> findByDatabaseNameAndTableNameAndColumnName(
        String databaseName, String tableName, String columnName);

    Optional<CustomAnnotation> findByOwnerUserIdAndDatabaseNameAndTableNameAndColumnName(
        Integer ownerUserId, String databaseName, String tableName, String columnName);
}
