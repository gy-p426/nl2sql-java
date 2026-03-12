package com.nl2sql.service;

import com.nl2sql.model.entity.User;
import com.nl2sql.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartupMigrationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StartupMigrationService startupMigrationService;

    @Test
    void getMigrationHistoryShouldReturnRowsWhenAdmin() {
        User admin = new User();
        admin.setId(1);
        admin.setRole("ADMIN");

        List<Map<String, Object>> rows = List.of(Map.of("migrationVersion", "20260312_user_role_admin"));

        when(userRepository.findById(eq(1))).thenReturn(Optional.of(admin));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        List<Map<String, Object>> result = startupMigrationService.getMigrationHistory(1);

        assertEquals(rows, result);
        verify(jdbcTemplate).queryForList(anyString());
    }

    @Test
    void getMigrationHistoryShouldRejectNonAdminUser() {
        User normalUser = new User();
        normalUser.setId(2);
        normalUser.setRole("USER");

        when(userRepository.findById(eq(2))).thenReturn(Optional.of(normalUser));

        assertThrows(SecurityException.class, () -> startupMigrationService.getMigrationHistory(2));
        verify(jdbcTemplate, never()).queryForList(anyString());
    }
}

