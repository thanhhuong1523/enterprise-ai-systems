package com.vccorp.eap.service;

import com.vccorp.eap.model.User;

/**
 * Dịch vụ quản lý vòng đời và xoay vòng Refresh Token (Token Rotation).
 * Đảm bảo cơ chế bảo mật chống replay attacks bằng cách lưu và thu hồi token trên Redis.
 */
public interface RefreshTokenService {
    
    /**
     * Tạo Refresh Token mới cho người dùng và lưu trữ thông tin metadata phiên làm việc vào Redis.
     */
    String createRefreshToken(User user, String userAgent, String ip);
    
    /**
     * Thực hiện xoay vòng token (Token Rotation).
     * Xác thực token cũ, xóa nó khỏi Redis một cách an toàn và trả về cặp token (Access/Refresh) mới.
     * @throws com.vccorp.eap.common.exception.BusinessException nếu token không hợp lệ hoặc đã qua sử dụng.
     */
    TokenRotationResult rotateRefreshToken(String refreshToken, String userAgent, String ip);
    
    /**
     * Thu hồi và vô hiệu hóa Refresh Token (xóa khỏi bộ nhớ Redis).
     */
    void revokeRefreshToken(String refreshToken);

    /**
     * Dữ liệu mô tả thông tin phiên làm việc lưu trong Redis cache.
     */
    public record RefreshTokenMetadata(
        String userId,
        String username,
        String tokenId,
        String createdAt,
        String expiresAt,
        String userAgent,
        String ip
    ) {
        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getTokenId() { return tokenId; }
        public String getCreatedAt() { return createdAt; }
        public String getExpiresAt() { return expiresAt; }
        public String getUserAgent() { return userAgent; }
        public String getIp() { return ip; }

        public static Builder builder(String userId, String username, String tokenId, String createdAt, String expiresAt) {
            return new Builder(userId, username, tokenId, createdAt, expiresAt);
        }

        public static class Builder {
            private final String userId;
            private final String username;
            private final String tokenId;
            private final String createdAt;
            private final String expiresAt;
            private String userAgent;
            private String ip;

            public Builder(String userId, String username, String tokenId, String createdAt, String expiresAt) {
                this.userId = userId;
                this.username = username;
                this.tokenId = tokenId;
                this.createdAt = createdAt;
                this.expiresAt = expiresAt;
            }

            public Builder userAgent(String userAgent) {
                this.userAgent = userAgent;
                return this;
            }

            public Builder ip(String ip) {
                this.ip = ip;
                return this;
            }

            public RefreshTokenMetadata build() {
                return new RefreshTokenMetadata(userId, username, tokenId, createdAt, expiresAt, userAgent, ip);
            }
        }
    }

    /**
     * Kết quả trả về sau khi xoay vòng Refresh Token thành công.
     */
    public record TokenRotationResult(
        String accessToken,
        String refreshToken,
        User user
    ) {}
}
