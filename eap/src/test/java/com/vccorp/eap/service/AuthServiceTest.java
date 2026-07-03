package com.vccorp.eap.service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.LoginRequest;
import com.vccorp.eap.dto.LoginResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.vccorp.eap.service.impl.AuthServiceImpl;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@vccorp.vn")
                .passwordHash("hashedpassword")
                .role(Role.ROLE_EMPLOYEE)
                .departmentId(UUID.randomUUID())
                .build();
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("testuser", "correctpassword");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("correctpassword", "hashedpassword")).thenReturn(true);
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("access-token-123");
        when(refreshTokenService.createRefreshToken(sampleUser, null, null)).thenReturn("refresh-token-123");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token-123", response.getAccessToken());
        assertEquals("refresh-token-123", response.getRefreshToken());
        assertEquals("testuser", response.getUserInfo().getUsername());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    void login_EmptyCredentials_ThrowsValidationException() {
        LoginRequest request = new LoginRequest("", "");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void login_UserNotFound_ThrowsUnauthenticated() {
        LoginRequest request = new LoginRequest("unknown", "password");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.ERR_UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void login_WrongPassword_ThrowsUnauthenticated() {
        LoginRequest request = new LoginRequest("testuser", "wrongpassword");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongpassword", "hashedpassword")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.ERR_UNAUTHENTICATED, ex.getErrorCode());
    }
}
