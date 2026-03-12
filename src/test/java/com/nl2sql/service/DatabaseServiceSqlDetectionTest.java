package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.client.OllamaClient;
import com.nl2sql.client.VolcanoEngineClient;
import com.nl2sql.repository.DatabaseHostConfigRepository;
import com.nl2sql.repository.DatabaseOverviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseServiceSqlDetectionTest {

    @Mock
    private DatabaseHostConfigRepository databaseHostConfigRepository;

    @Mock
    private DatabaseOverviewRepository databaseOverviewRepository;

    @Mock
    private DatabaseAccessScopeService databaseAccessScopeService;

    @Mock
    private DatabasePoolService databasePoolService;

    @Mock
    private VolcanoEngineClient volcanoEngineClient;

    @Mock
    private OllamaClient ollamaClient;

    @Mock
    private DynamicConfigProvider configProvider;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DatabaseService databaseService;

    @Test
    void detectDatabaseFromSqlShouldRejectFallbackWhenUserHasMultipleDatabases() {
        Integer userId = 1001;
        when(databaseAccessScopeService.getAllowedDatabases(eq(userId)))
                .thenReturn(List.of("yibin2", "yibinsalary"));

        String sql = "SELECT * FROM employees";

        String detected = databaseService.detectDatabaseFromSql(userId, sql);

        assertNull(detected);
    }

    @Test
    void detectDatabaseFromSqlShouldAllowSingleDatabaseFallback() {
        Integer userId = 1002;
        when(databaseAccessScopeService.getAllowedDatabases(eq(userId)))
                .thenReturn(List.of("yibin2"));

        String sql = "SELECT * FROM employees";

        String detected = databaseService.detectDatabaseFromSql(userId, sql);

        assertEquals("yibin2", detected);
    }

    @Test
    void detectDatabaseFromSqlShouldReturnExplicitDatabaseWhenQualified() {
        Integer userId = 1003;
        when(databaseAccessScopeService.getAllowedDatabases(eq(userId)))
                .thenReturn(List.of("yibin2", "yibinsalary"));

        String sql = "SELECT * FROM yibinsalary.salary_detail LIMIT 10";

        String detected = databaseService.detectDatabaseFromSql(userId, sql);

        assertEquals("yibinsalary", detected);
    }
}

