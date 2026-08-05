package com.vccorp.eap.service.impl;

import com.vccorp.eap.model.User;
import com.vccorp.eap.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Lớp thực thi của JwtService.
 * Xử lý việc tạo sinh và xác thực JSON Web Token (JWT) thông qua khóa HMAC SHA-256.
 */
@Service
public class JwtServiceImpl implements JwtService {

    /** Khóa ký số của JWT */
    private final Key signingKey;
    
    /** Thời gian hiệu lực của Access Token (mili-giây) */
    private final long expirationMs;
    
    /** Thời gian hiệu lực của Refresh Token (mili-giây) */
    private final long refreshExpirationMs;

    /**
     * Khởi tạo JwtServiceImpl bằng các giá trị cấu hình từ application properties.
     */
    public JwtServiceImpl(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateRefreshToken(User user, String tokenId) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("id", user.getId().toString())
                .claim("tokenId", tokenId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("id", user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("departmentId", user.getDepartmentId() != null ? user.getDepartmentId().toString() : null)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
