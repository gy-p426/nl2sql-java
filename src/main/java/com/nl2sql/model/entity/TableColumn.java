package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 表列信息实体
 */
@Data
@Entity
@Table(name = "table_columns",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_owner_host_db_table_column",
        columnNames = {"owner_user_id", "host_config_id", "database_name", "table_name", "column_name"}
    ),
    indexes = {
        @Index(name = "idx_schema", columnList = "schema_id"),
        @Index(name = "idx_db_table", columnList = "database_name, table_name"),
        @Index(name = "idx_owner_host_table", columnList = "owner_user_id,host_config_id,database_name,table_name")
    }
)
public class TableColumn {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_id", nullable = false, foreignKey = @ForeignKey(name = "fk_column_schema"))
    private DatabaseSchema schema;

    @Column(name = "owner_user_id")
    private Integer ownerUserId;

    @Column(name = "host_config_id")
    private Integer hostConfigId;
    
    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;
    
    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;
    
    @Column(name = "column_name", nullable = false, length = 100)
    private String columnName;
    
    @Column(name = "column_type", length = 100)
    private String columnType;
    
    @Column(name = "data_type", length = 50)
    private String dataType;
    
    @Column(name = "column_comment", length = 500)
    private String columnComment;
    
    @Column(name = "is_nullable")
    private Boolean isNullable = true;
    
    @Column(name = "column_default", length = 500)
    private String columnDefault;
    
    @Column(name = "column_key", length = 10)
    private String columnKey;
    
    @Column(name = "ordinal_position")
    private Integer ordinalPosition;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
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
