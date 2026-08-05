package com.vccorp.eap.service;

import com.vccorp.eap.model.User;
import io.jsonwebtoken.Claims;

/**
 * Dịch vụ xử lý JSON Web Token (JWT) trong hệ thống.
 * Cung cấp các phương thức để tạo, parse và validate cả Access Token và Refresh Token.
 */
public interface JwtService {
    
    /**
     * Lấy thời gian hết hạn của Access Token (ms).
     */
    long getExpirationMs();
    
    /**
     * Lấy thời gian hết hạn của Refresh Token (ms).
     */
    long getRefreshExpirationMs();
    
    /**
     * Tạo Refresh Token chứa mã số phiên (tokenId) và thông tin người dùng.
     */
    String generateRefreshToken(User user, String tokenId);
    
    /**
     * Tạo Access Token chứa thông tin định danh và phân quyền của người dùng.
     */
    String generateAccessToken(User user);
    
    /**
     * Phân tích (parse) token để lấy thông tin các Claims bên trong.
     */
    Claims parseToken(String token);
    
    /**
     * Kiểm tra tính hợp lệ về mặt chữ ký và thời gian hết hạn của token.
     */
    boolean validateToken(String token);
}
