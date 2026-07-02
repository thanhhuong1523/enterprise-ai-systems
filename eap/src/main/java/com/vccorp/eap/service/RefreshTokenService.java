package com.vccorp.eap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RedisService redisService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshTokenMetadata {
        private String userId;
        private String username;
        private String tokenId;
        private String createdAt;
        private String expiresAt;
        private String userAgent;
        private String ip;
    }

    public String createRefreshToken(User user, String userAgent, String ip) {
        String tokenId = UUID.randomUUID().toString();
        String token = jwtService.generateRefreshToken(user, tokenId);

        RefreshTokenMetadata metadata = RefreshTokenMetadata.builder()
                .userId(user.getId().toString())
                .username(user.getUsername())
                .tokenId(tokenId)
                .createdAt(LocalDateTime.now().toString())
                .expiresAt(LocalDateTime.now().plusNanos(jwtService.getRefreshExpirationMs() * 1_000_000).toString())
                .userAgent(userAgent != null ? userAgent : "Unknown")
                .ip(ip != null ? ip : "Unknown")
                .build();

        try {
            String json = objectMapper.writeValueAsString(metadata);
            String redisKey = getRedisKey(user.getId().toString(), tokenId);
            redisService.set(redisKey, json, jwtService.getRefreshExpirationMs());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể lưu thông tin phiên làm việc.");
        }

        return token;
    }

    public TokenRotationResult rotateRefreshToken(String refreshToken, String userAgent, String ip) {
        // 1. Validate signature & expiration of Refresh Token JWT
        Claims claims;
        try {
            claims = jwtService.parseToken(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Refresh Token không hợp lệ hoặc đã hết hạn.");
        }

        String userIdStr = claims.get("id", String.class);
        String tokenId = claims.get("tokenId", String.class);
        String username = claims.getSubject();

        if (userIdStr == null || tokenId == null) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Refresh Token sai định dạng.");
        }

        // 2. Token Rotation: Atomically delete key to prevent concurrent replay attacks
        String redisKey = getRedisKey(userIdStr, tokenId);
        Boolean deleted = redisService.delete(redisKey);
        if (deleted == null || !deleted) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Refresh Token không tồn tại, đã hết hạn hoặc đã bị thu hồi.");
        }

        // 3. Check if user is active & exists in database
        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Người dùng không tồn tại hoặc đã bị khóa."));

        // 4. Token Rotation (generate new)

        String newTokenId = UUID.randomUUID().toString();
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user, newTokenId);

        // 5. Store new Refresh Token in Redis
        RefreshTokenMetadata metadata = RefreshTokenMetadata.builder()
                .userId(userIdStr)
                .username(username)
                .tokenId(newTokenId)
                .createdAt(LocalDateTime.now().toString())
                .expiresAt(LocalDateTime.now().plusNanos(jwtService.getRefreshExpirationMs() * 1_000_000).toString())
                .userAgent(userAgent != null ? userAgent : "Unknown")
                .ip(ip != null ? ip : "Unknown")
                .build();

        try {
            String json = objectMapper.writeValueAsString(metadata);
            String newRedisKey = getRedisKey(userIdStr, newTokenId);
            redisService.set(newRedisKey, json, jwtService.getRefreshExpirationMs());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể lưu thông tin phiên làm việc mới.");
        }

        return new TokenRotationResult(newAccessToken, newRefreshToken, user);
    }

    public void revokeRefreshToken(String refreshToken) {
        try {
            Claims claims = jwtService.parseToken(refreshToken);
            String userIdStr = claims.get("id", String.class);
            String tokenId = claims.get("tokenId", String.class);
            if (userIdStr != null && tokenId != null) {
                redisService.delete(getRedisKey(userIdStr, tokenId));
            }
        } catch (Exception e) {
            // If already expired/invalid, do nothing
        }
    }

    private String getRedisKey(String userId, String tokenId) {
        return "refresh:" + userId + ":" + tokenId;
    }

    @Data
    @AllArgsConstructor
    public static class TokenRotationResult {
        private final String accessToken;
        private final String refreshToken;
        private final User user;
    }
}
