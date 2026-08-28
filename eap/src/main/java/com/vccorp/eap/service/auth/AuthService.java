package com.vccorp.eap.service.auth;

import com.vccorp.eap.dto.auth.LoginRequest;
import com.vccorp.eap.dto.auth.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse login(LoginRequest request, String userAgent, String ip);
    LoginResponse refresh(String refreshToken, String userAgent, String ip);
    void logout(String refreshToken);
}
