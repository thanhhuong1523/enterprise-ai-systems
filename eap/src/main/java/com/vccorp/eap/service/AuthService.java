package com.vccorp.eap.service;

import com.vccorp.eap.dto.LoginRequest;
import com.vccorp.eap.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse login(LoginRequest request, String userAgent, String ip);
    LoginResponse refresh(String refreshToken, String userAgent, String ip);
    void logout(String refreshToken);
}
