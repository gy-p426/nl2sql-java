package com.nl2sql.service;

import com.nl2sql.model.entity.TrainingData;
import com.nl2sql.repository.TrainingDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingDataServiceScopeTest {

    @Mock
    private TrainingDataRepository trainingDataRepository;

    @Mock
    private DatabaseAccessScopeService databaseAccessScopeService;

    @InjectMocks
    private TrainingDataService trainingDataService;

    @Test
    void getTrainingDataShouldOnlyReadAllowedDatabases() {
        when(databaseAccessScopeService.getAllowedDatabases(eq(1))).thenReturn(List.of("db1", "db2"));

        TrainingData td = new TrainingData();
        td.setId(1);
        td.setQuestion("问题");
        td.setSql("SELECT * FROM db1.t");
        td.setDatabaseName("db1");

        when(trainingDataRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")))
            .thenReturn(List.of(td));

        Map<String, Object> result = trainingDataService.getTrainingData(1, null, null, 1, 20);

        assertEquals(1, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        assertEquals(1, data.size());
        assertEquals("db1", data.get(0).get("database"));
    }

    @Test
    void getTrainingDataShouldIncludeMultiDatabaseRecordWhenAnyDatabaseMatches() {
        when(databaseAccessScopeService.getAllowedDatabases(eq(2))).thenReturn(List.of("yibinsalary"));

        TrainingData td = new TrainingData();
        td.setId(2);
        td.setQuestion("跨库问题");
        td.setSql("SELECT * FROM yibinsalary.a JOIN yibin2.b ON a.id=b.id");
        td.setDatabaseName("yibin2,yibinsalary,yibinhumanresources");

        when(trainingDataRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")))
            .thenReturn(List.of(td));

        Map<String, Object> result = trainingDataService.getTrainingData(2, null, null, 1, 20);

        assertEquals(1, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        assertEquals(1, data.size());
        assertEquals("yibin2,yibinsalary,yibinhumanresources", data.get(0).get("database"));
    }

    @Test
    void addTrainingDataShouldRejectUnauthorizedDatabase() {
        when(databaseAccessScopeService.getAllowedDatabases(eq(1))).thenReturn(List.of("db1"));

        Map<String, Object> result = trainingDataService.addTrainingData(1, "Q", "SELECT 1", "db2", null);

        assertFalse((Boolean) result.get("success"));
        assertTrue(String.valueOf(result.get("error")).contains("无权访问数据库"));
    }

    @Test
    void deleteTrainingDataShouldRejectUnauthorizedRecord() {
        when(databaseAccessScopeService.getAllowedDatabases(eq(1))).thenReturn(List.of("db1"));
        TrainingData td = new TrainingData();
        td.setId(10);
        td.setDatabaseName("db1,db2");
        when(trainingDataRepository.findById(eq(10))).thenReturn(Optional.of(td));

        Map<String, Object> result = trainingDataService.deleteTrainingData(1, 10);

        assertFalse((Boolean) result.get("success"));
        assertEquals("无权限删除该训练数据", result.get("error"));
    }

    @Test
    void deleteTrainingDataShouldAllowWhenUserHasFullDatabaseScope() {
        when(databaseAccessScopeService.getAllowedDatabases(eq(3))).thenReturn(List.of("db1", "db2"));
        TrainingData td = new TrainingData();
        td.setId(11);
        td.setDatabaseName("db1,db2");
        when(trainingDataRepository.findById(eq(11))).thenReturn(Optional.of(td));

        Map<String, Object> result = trainingDataService.deleteTrainingData(3, 11);

        assertTrue((Boolean) result.get("success"));
    }
}
