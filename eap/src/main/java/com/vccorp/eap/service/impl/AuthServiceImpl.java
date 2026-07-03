package com.vccorp.eap.service.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.LoginRequest;
import com.vccorp.eap.dto.LoginResponse;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.AuthService;
import com.vccorp.eap.service.JwtService;
import com.vccorp.eap.service.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String userAgent, String ip) {
        if (request.username() == null || request.username().trim().isEmpty() ||
            request.password() == null || request.password().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập và mật khẩu không được rỗng.");
        }

        User user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Tên đăng nhập hoặc mật khẩu không chính xác."));

        if (!passwordEncoder.matches(request.password().trim(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Tên đăng nhập hoặc mật khẩu không chính xác.");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user, userAgent, ip);

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRole()
                )
                .departmentId(user.getDepartmentId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .build();

        return LoginResponse.builder(
                    accessToken,
                    "Bearer",
                    900,
                    refreshToken,
                    604800,
                    userInfo
                )
                .build();
    }

    @Override
    public LoginResponse refresh(String refreshToken, String userAgent, String ip) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Refresh Token không được rỗng.");
        }

        RefreshTokenService.TokenRotationResult result = refreshTokenService.rotateRefreshToken(refreshToken, userAgent, ip);
        User user = result.getUser();

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRole()
                )
                .departmentId(user.getDepartmentId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .build();

        return LoginResponse.builder(
                    result.getAccessToken(),
                    "Bearer",
                    900,
                    result.getRefreshToken(),
                    604800,
                    userInfo
                )
                .build();
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }
    }
}
