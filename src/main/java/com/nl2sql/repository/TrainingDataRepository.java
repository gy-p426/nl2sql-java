package com.nl2sql.repository;

import com.nl2sql.model.entity.TrainingData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 训练数据Repository
 */
@Repository
public interface TrainingDataRepository extends JpaRepository<TrainingData, Integer> {
    
    /**
     * 按数据库名称查找
     */
    List<TrainingData> findByDatabaseName(String databaseName);
    
    /**
     * 分页查询
     */
    Page<TrainingData> findAll(Pageable pageable);
    
    /**
     * 按数据库名称分页查询
     */
    Page<TrainingData> findByDatabaseName(String databaseName, Pageable pageable);
    
    /**
     * 搜索问题或SQL
     */
    @Query("SELECT t FROM TrainingData t WHERE " +
           "t.question LIKE %:search% OR t.sql LIKE %:search%")
    Page<TrainingData> searchByQuestionOrSql(@Param("search") String search, Pageable pageable);
    
    /**
     * 按数据库搜索
     */
    @Query("SELECT t FROM TrainingData t WHERE t.databaseName = :database AND " +
           "(t.question LIKE %:search% OR t.sql LIKE %:search%)")
    Page<TrainingData> searchByDatabaseAndQuestionOrSql(
        @Param("database") String database, 
        @Param("search") String search, 
        Pageable pageable);
}
