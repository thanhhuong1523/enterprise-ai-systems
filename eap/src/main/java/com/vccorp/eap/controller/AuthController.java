package com.vccorp.eap.controller;

import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.LoginRequest;
import com.vccorp.eap.dto.LoginResponse;
import com.vccorp.eap.dto.RefreshRequest;
import com.vccorp.eap.dto.LogoutRequest;
import com.vccorp.eap.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ip = servletRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = servletRequest.getRemoteAddr();
        }
        LoginResponse response = authService.login(request, userAgent, ip);
        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ip = servletRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = servletRequest.getRemoteAddr();
        }
        LoginResponse response = authService.refresh(request.getRefreshToken(), userAgent, ip);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ApiResponse.success(null);
    }
}
