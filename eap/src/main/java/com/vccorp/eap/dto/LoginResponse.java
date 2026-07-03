package com.vccorp.eap.dto;

import com.vccorp.eap.enums.Role;
import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    long refreshTokenExpiresIn,
    UserInfo userInfo
) {
    // Java Bean style getters for compatibility
    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public String getRefreshToken() { return refreshToken; }
    public long getRefreshTokenExpiresIn() { return refreshTokenExpiresIn; }
    public UserInfo getUserInfo() { return userInfo; }

    public static Builder builder(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshTokenExpiresIn,
        UserInfo userInfo
    ) {
        return new Builder(accessToken, tokenType, expiresIn, refreshToken, refreshTokenExpiresIn, userInfo);
    }

    public static class Builder {
        private final String accessToken;
        private final String tokenType;
        private final long expiresIn;
        private final String refreshToken;
        private final long refreshTokenExpiresIn;
        private final UserInfo userInfo;

        public Builder(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            long refreshTokenExpiresIn,
            UserInfo userInfo
        ) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
            this.refreshToken = refreshToken;
            this.refreshTokenExpiresIn = refreshTokenExpiresIn;
            this.userInfo = userInfo;
        }

        public LoginResponse build() {
            return new LoginResponse(accessToken, tokenType, expiresIn, refreshToken, refreshTokenExpiresIn, userInfo);
        }
    }

    public record UserInfo(
        UUID id,
        String username,
        String email,
        Role role,
        UUID departmentId,
        String fullName,
        String phone
    ) {
        // Java Bean style getters for compatibility
        public UUID getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public Role getRole() { return role; }
        public UUID getDepartmentId() { return departmentId; }
        public String getFullName() { return fullName; }
        public String getPhone() { return phone; }

        public static UserInfoBuilder builder(UUID id, String username, String email, Role role) {
            return new UserInfoBuilder(id, username, email, role);
        }

        public static class UserInfoBuilder {
            private final UUID id;
            private final String username;
            private final String email;
            private final Role role;
            private UUID departmentId;
            private String fullName;
            private String phone;

            public UserInfoBuilder(UUID id, String username, String email, Role role) {
                this.id = id;
                this.username = username;
                this.email = email;
                this.role = role;
            }

            public UserInfoBuilder departmentId(UUID departmentId) {
                this.departmentId = departmentId;
                return this;
            }

            public UserInfoBuilder fullName(String fullName) {
                this.fullName = fullName;
                return this;
            }

            public UserInfoBuilder phone(String phone) {
                this.phone = phone;
                return this;
            }

            public UserInfo build() {
                return new UserInfo(id, username, email, role, departmentId, fullName, phone);
            }
        }
    }
}
