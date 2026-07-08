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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final com.vccorp.eap.service.JwtService jwtService;

    public AuthController(AuthService authService, com.vccorp.eap.service.JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    private ResponseCookie createRefreshTokenCookie(String token, HttpServletRequest request) {
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        long maxAge = token == null || token.isEmpty() ? 0 : jwtService.getRefreshExpirationMs() / 1000;
        return ResponseCookie.from("refreshToken", token == null ? "" : token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

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
        ResponseCookie cookie = createRefreshTokenCookie(response.getRefreshToken(), servletRequest);
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        // Secure response body by nullifying refreshToken
        LoginResponse secureResponse = LoginResponse.builder(
                response.getAccessToken(),
                response.getTokenType(),
                response.getExpiresIn(),
                null,
                0,
                response.getUserInfo()
        ).build();

        return ApiResponse.success(secureResponse);
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
        ResponseCookie cookie = createRefreshTokenCookie(response.getRefreshToken(), servletRequest);
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        // Secure response body by nullifying refreshToken
        LoginResponse secureResponse = LoginResponse.builder(
                response.getAccessToken(),
                response.getTokenType(),
                response.getExpiresIn(),
                null,
                0,
                response.getUserInfo()
        ).build();

        return ApiResponse.success(secureResponse);
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
        
        if (refreshToken != null && !refreshToken.isEmpty()) {
            authService.logout(refreshToken);
        }
        
        // Clear HttpOnly cookie
        ResponseCookie cookie = createRefreshTokenCookie("", servletRequest);
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        
        return ApiResponse.success(null);
    }
}
