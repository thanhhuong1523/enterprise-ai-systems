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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String userAgent, String ip) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập và mật khẩu không được rỗng.");
        }

        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Tên đăng nhập hoặc mật khẩu không chính xác."));

        if (!passwordEncoder.matches(request.getPassword().trim(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Tên đăng nhập hoặc mật khẩu không chính xác.");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user, userAgent, ip);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(900) // 15 mins (900s)
                .refreshToken(refreshToken)
                .refreshTokenExpiresIn(604800) // 7 days (604800s)
                .userInfo(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .departmentId(user.getDepartmentId())
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .build())
                .build();
    }

    @Override
    public LoginResponse refresh(String refreshToken, String userAgent, String ip) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Refresh Token không được rỗng.");
        }

        RefreshTokenService.TokenRotationResult result = refreshTokenService.rotateRefreshToken(refreshToken, userAgent, ip);
        User user = result.getUser();

        return LoginResponse.builder()
                .accessToken(result.getAccessToken())
                .tokenType("Bearer")
                .expiresIn(900)
                .refreshToken(result.getRefreshToken())
                .refreshTokenExpiresIn(604800)
                .userInfo(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .departmentId(user.getDepartmentId())
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .build())
                .build();
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }
    }
}
