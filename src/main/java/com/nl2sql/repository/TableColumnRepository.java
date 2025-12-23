package com.nl2sql.repository;

import com.nl2sql.model.entity.TableColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 表列信息Repository
 */
@Repository
public interface TableColumnRepository extends JpaRepository<TableColumn, Integer> {
    
    /**
     * 查找表的所有列
     */
    List<TableColumn> findByDatabaseNameAndTableNameOrderByOrdinalPosition(
        String databaseName, String tableName);
    
    /**
     * 删除表的所有列
     */
    void deleteByDatabaseNameAndTableName(String databaseName, String tableName);
}
