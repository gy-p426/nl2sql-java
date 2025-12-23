package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库Schema实体
 */
@Data
@Entity
@Table(name = "database_schema", 
    uniqueConstraints = @UniqueConstraint(name = "uk_db_table", columnNames = {"database_name", "table_name"}),
    indexes = @Index(name = "idx_database", columnList = "database_name")
)
public class DatabaseSchema {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;
    
    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;
    
    @Column(name = "table_comment", length = 500)
    private String tableComment;
    
    @Column(name = "primary_keys", length = 500)
    private String primaryKeys;
    
    @Column(name = "foreign_keys", columnDefinition = "TEXT")
    private String foreignKeys;
    
    @Column(name = "table_rows")
    private Long tableRows = 0L;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "schema", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TableColumn> columns = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
