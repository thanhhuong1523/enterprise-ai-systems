package com.vccorp.eap.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepartmentResponse(
    UUID id,
    String code,
    String name,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    // Java Bean style getters for compatibility
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public static Builder builder(UUID id, String code, String name) {
        return new Builder(id, code, name);
    }

    public static class Builder {
        private final UUID id;
        private final String code;
        private final String name;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder(UUID id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
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

        public DepartmentResponse build() {
            return new DepartmentResponse(id, code, name, description, createdAt, updatedAt);
        }
    }
}
