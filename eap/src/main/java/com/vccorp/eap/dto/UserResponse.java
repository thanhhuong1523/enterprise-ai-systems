package com.vccorp.eap.dto;

import com.vccorp.eap.enums.Role;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String email,
    Role role,
    UUID departmentId,
    String fullName,
    String phone,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    // Java Bean style getters for compatibility
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public UUID getDepartmentId() { return departmentId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public static Builder builder(UUID id, String username, String email, Role role) {
        return new Builder(id, username, email, role);
    }

    public static class Builder {
        private final UUID id;
        private final String username;
        private final String email;
        private final Role role;
        private UUID departmentId;
        private String fullName;
        private String phone;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder(UUID id, String username, String email, Role role) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role;
        }

        public Builder departmentId(UUID departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public UserResponse build() {
            return new UserResponse(id, username, email, role, departmentId, fullName, phone, createdAt, updatedAt);
        }
    }
}
