package com.nl2sql.repository;

import com.nl2sql.model.entity.TrainingData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
     * 按数据库范围查询
     */
    List<TrainingData> findByDatabaseNameIn(List<String> databaseNames);
    
    /**
     * 分页查询
     */
    Page<TrainingData> findAll(Pageable pageable);
    
    /**
     * 按数据库名称分页查询
     */
    Page<TrainingData> findByDatabaseName(String databaseName, Pageable pageable);

    /**
     * 按数据库范围分页查询
     */
    Page<TrainingData> findByDatabaseNameIn(List<String> databaseNames, Pageable pageable);

    /**
     * 按ID和数据库范围查询
     */
    Optional<TrainingData> findByIdAndDatabaseNameIn(Integer id, List<String> databaseNames);
    
    /**
     * 搜索问题或SQL
     */
    @Query("SELECT t FROM TrainingData t WHERE " +
           "t.question LIKE %:search% OR t.sql LIKE %:search%")
    Page<TrainingData> searchByQuestionOrSql(@Param("search") String search, Pageable pageable);

    /**
     * 在数据库范围内搜索问题或SQL
     */
    @Query("SELECT t FROM TrainingData t WHERE t.databaseName IN :databases AND " +
           "(t.question LIKE %:search% OR t.sql LIKE %:search%)")
    Page<TrainingData> searchByDatabasesAndQuestionOrSql(
        @Param("databases") List<String> databases,
        @Param("search") String search,
        Pageable pageable);
    
    /**
     * 按数据库搜索
     */
    @Query("SELECT t FROM TrainingData t WHERE t.databaseName = :database AND " +
           "(t.question LIKE %:search% OR t.sql LIKE %:search%)")
    Page<TrainingData> searchByDatabaseAndQuestionOrSql(
        @Param("database") String database, 
        @Param("search") String search, 
        Pageable pageable);

    /**
     * 在数据库范围内按指定数据库搜索
     */
    @Query("SELECT t FROM TrainingData t WHERE t.databaseName IN :databases AND t.databaseName = :database AND " +
           "(t.question LIKE %:search% OR t.sql LIKE %:search%)")
    Page<TrainingData> searchByDatabasesAndDatabaseAndQuestionOrSql(
        @Param("databases") List<String> databases,
        @Param("database") String database,
        @Param("search") String search,
        Pageable pageable);
}
