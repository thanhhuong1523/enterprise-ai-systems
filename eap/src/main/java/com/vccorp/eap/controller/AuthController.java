package com.vccorp.eap.controller;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.LoginRequest;
import com.vccorp.eap.dto.LoginResponse;
import com.vccorp.eap.dto.RefreshRequest;
import com.vccorp.eap.dto.LogoutRequest;
import com.vccorp.eap.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, 
            HttpServletRequest servletRequest, 
            HttpServletResponse servletResponse) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ip = servletRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = servletRequest.getRemoteAddr();
        }
        LoginResponse response = authService.login(request, userAgent, ip);
        
        // Write refreshToken to HttpOnly cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(604800) // 7 days
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @RequestBody(required = false) RefreshRequest request, 
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        
        String refreshToken = null;
        
        // 1. Try reading from cookie
        if (servletRequest.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : servletRequest.getCookies()) {
                if ("refreshToken".equals(c.getName())) {
                    refreshToken = c.getValue();
                }
            }
        }
        
        // 2. Fallback to request body
        if ((refreshToken == null || refreshToken.isEmpty()) && request != null) {
            refreshToken = request.getRefreshToken();
        }
        
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Refresh Token không được rỗng.");
        }

        String userAgent = servletRequest.getHeader("User-Agent");
        String ip = servletRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = servletRequest.getRemoteAddr();
        }
        
        LoginResponse response = authService.refresh(refreshToken, userAgent, ip);
        
        // Write rotated refreshToken to HttpOnly cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(604800) // 7 days
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        
        String refreshToken = null;
        
        // 1. Try reading from cookie
        if (servletRequest.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : servletRequest.getCookies()) {
                if ("refreshToken".equals(c.getName())) {
                    refreshToken = c.getValue();
                }
            }
        }
        
        // 2. Fallback to request body
        if ((refreshToken == null || refreshToken.isEmpty()) && request != null) {
            refreshToken = request.getRefreshToken();
        }
        
        if (refreshToken != null && !refreshToken.isEmpty()) {
            authService.logout(refreshToken);
        }
        
        // Clear HttpOnly cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0) // immediately expire
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        return ApiResponse.success(null);
    }
}
