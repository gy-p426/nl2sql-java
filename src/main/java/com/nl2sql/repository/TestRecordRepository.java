package com.nl2sql.repository;

import com.nl2sql.model.entity.TestRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 测试记录Repository
 */
@Repository
public interface TestRecordRepository extends JpaRepository<TestRecord, Integer> {
    
    /**
     * 按测试ID查找
     */
    Optional<TestRecord> findByTestId(String testId);
    
    /**
     * 按状态查找
     */
    Page<TestRecord> findByStatus(String status, Pageable pageable);
    
    /**
     * 搜索测试记录
     */
    @Query("SELECT t FROM TestRecord t WHERE " +
           "t.question LIKE %:search% OR t.notes LIKE %:search%")
    Page<TestRecord> searchByQuestionOrNotes(@Param("search") String search, Pageable pageable);
    
    /**
     * 按状态搜索
     */
    @Query("SELECT t FROM TestRecord t WHERE t.status = :status AND " +
           "(t.question LIKE %:search% OR t.notes LIKE %:search%)")
    Page<TestRecord> searchByStatusAndQuestionOrNotes(
        @Param("status") String status,
        @Param("search") String search,
        Pageable pageable);
    
    /**
     * 统计各状态数量
     */
    long countByStatus(String status);
    
    /**
     * 删除测试记录
     */
    void deleteByTestId(String testId);
}
