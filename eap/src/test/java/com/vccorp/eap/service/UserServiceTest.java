package com.vccorp.eap.service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void deleteUser_SystemAdmin_ThrowsValidationException() {
        UUID adminId = UUID.randomUUID();
        User adminUser = User.builder()
                .id(adminId)
                .username("admin")
                .role(Role.SYSTEM_ADMIN)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.deleteUser(adminId));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertEquals("Không thể xóa tài khoản quản trị hệ thống (SYSTEM_ADMIN).", ex.getMessage());
    }
}
