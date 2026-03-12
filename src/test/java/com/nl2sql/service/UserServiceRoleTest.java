package com.nl2sql.service;

import com.nl2sql.model.entity.User;
import com.nl2sql.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRoleTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DatabaseService databaseService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserService userService;

    @Test
    void updateUserRoleShouldSucceedWhenOperatorIsAdmin() {
        User admin = new User();
        admin.setId(1);
        admin.setRole("ADMIN");

        User target = new User();
        target.setId(2);
        target.setRole("USER");

        when(userRepository.findById(eq(1))).thenReturn(Optional.of(admin));
        when(userRepository.findById(eq(2))).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = userService.updateUserRole(1, 2, "admin");

        assertTrue((Boolean) result.get("success"));
        assertEquals("ADMIN", target.getRole());
        verify(userRepository).save(target);
    }

    @Test
    void updateUserRoleShouldFailWhenOperatorIsNotAdmin() {
        User normalUser = new User();
        normalUser.setId(1);
        normalUser.setRole("USER");

        when(userRepository.findById(eq(1))).thenReturn(Optional.of(normalUser));

        Map<String, Object> result = userService.updateUserRole(1, 2, "ADMIN");

        assertFalse((Boolean) result.get("success"));
        assertEquals("仅管理员可修改用户角色", result.get("message"));
    }
}

