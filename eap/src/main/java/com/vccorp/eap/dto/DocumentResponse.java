package com.vccorp.eap.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO phản hồi cho tài liệu gốc và alias.
 * Trường {@code duplicated} được thêm ở Week 2 (DetailedDesign §3.2):
 * - false: tải lên thành công tệp mới → HTTP 201 Created
 * - true:  phát hiện tệp trùng lặp trong phòng ban → HTTP 200 OK
 */
public record DocumentResponse(
    UUID id,
    String businessCode,
    String title,
    Long fileSize,
    String hash,
    UUID ownerDepartmentId,
    UUID parentId,
    UUID creatorDepartmentId,
    UUID createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public boolean isAlias() {
        return id != null && (id.getLeastSignificantBits() & 1L) == 1L;
    }

    public boolean isOriginal() {
        return id != null && (id.getLeastSignificantBits() & 1L) == 0L;
    }

    // Java Bean style getters for compatibility
    public UUID getId() { return id; }
    public String getBusinessCode() { return businessCode; }
    public String getTitle() { return title; }
    public Long getFileSize() { return fileSize; }
    public String getHash() { return hash; }
    public UUID getOwnerDepartmentId() { return ownerDepartmentId; }
    public UUID getParentId() { return parentId; }
    public UUID getCreatorDepartmentId() { return creatorDepartmentId; }
    public UUID getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public static Builder builder(UUID id, String businessCode, String title, UUID ownerDepartmentId, LocalDateTime createdAt) {
        return new Builder(id, businessCode, title, ownerDepartmentId, createdAt);
    }

    public static class Builder {
        private final UUID id;
        private final String businessCode;
        private final String title;
        private final UUID ownerDepartmentId;
        private final LocalDateTime createdAt;
        private Long fileSize;
        private String hash;
        private UUID parentId;
        private UUID creatorDepartmentId;
        private UUID createdBy;
        private LocalDateTime updatedAt;

        public Builder(UUID id, String businessCode, String title, UUID ownerDepartmentId, LocalDateTime createdAt) {
            this.id = id;
            this.businessCode = businessCode;
            this.title = title;
            this.ownerDepartmentId = ownerDepartmentId;
            this.createdAt = createdAt;
        }

        public Builder fileSize(Long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public Builder hash(String hash) {
            this.hash = hash;
            return this;
        }

        public Builder parentId(UUID parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder creatorDepartmentId(UUID creatorDepartmentId) {
            this.creatorDepartmentId = creatorDepartmentId;
            return this;
        }

        public Builder createdBy(UUID createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public DocumentResponse build() {
            return new DocumentResponse(
                id, businessCode, title, fileSize, hash, ownerDepartmentId,
                parentId, creatorDepartmentId, createdBy, createdAt, updatedAt
            );
        }
    }
}
