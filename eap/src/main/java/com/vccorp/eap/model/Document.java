package com.vccorp.eap.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@FilterDef(
    name = "deptIsolationFilter",
    parameters = @ParamDef(name = "userDeptId", type = java.util.UUID.class)
)
@Filter(
    name = "deptIsolationFilter",
    condition = "(owner_department_id = :userDeptId OR creator_department_id = :userDeptId OR (parent_id IS NULL AND id IN (SELECT parent_id FROM documents WHERE owner_department_id = :userDeptId AND parent_id IS NOT NULL AND deleted_at IS NULL))) AND deleted_at IS NULL"
)
public class Document {
    @Id
    private UUID id;

    @Column(name = "business_code", nullable = false, unique = true)
    private String businessCode;

    @Column(nullable = false)
    private String title;

    @Column(name = "file_reference")
    private String fileReference;

    @Column(name = "file_size")
    private Long fileSize;

    private String hash;

    @Column(name = "owner_department_id", nullable = false)
    private UUID ownerDepartmentId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "creator_department_id")
    private UUID creatorDepartmentId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Document() {}

    public Document(UUID id, String businessCode, String title, String fileReference, Long fileSize, String hash,
                    UUID ownerDepartmentId, UUID parentId, UUID creatorDepartmentId, UUID createdBy,
                    LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.businessCode = businessCode;
        this.title = title;
        this.fileReference = fileReference;
        this.fileSize = fileSize;
        this.hash = hash;
        this.ownerDepartmentId = ownerDepartmentId;
        this.parentId = parentId;
        this.creatorDepartmentId = creatorDepartmentId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public boolean isAlias() {
        return id != null && (id.getLeastSignificantBits() & 1L) == 1L;
    }

    public boolean isOriginal() {
        return id != null && (id.getLeastSignificantBits() & 1L) == 0L;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFileReference() { return fileReference; }
    public void setFileReference(String fileReference) { this.fileReference = fileReference; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public UUID getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(UUID ownerDepartmentId) { this.ownerDepartmentId = ownerDepartmentId; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public UUID getCreatorDepartmentId() { return creatorDepartmentId; }
    public void setCreatorDepartmentId(UUID creatorDepartmentId) { this.creatorDepartmentId = creatorDepartmentId; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public static DocumentBuilder builder() {
        return new DocumentBuilder();
    }

    public static class DocumentBuilder {
        private UUID id;
        private String businessCode;
        private String title;
        private String fileReference;
        private Long fileSize;
        private String hash;
        private UUID ownerDepartmentId;
        private UUID parentId;
        private UUID creatorDepartmentId;
        private UUID createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime deletedAt;

        public DocumentBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public DocumentBuilder businessCode(String businessCode) {
            this.businessCode = businessCode;
            return this;
        }

        public DocumentBuilder title(String title) {
            this.title = title;
            return this;
        }

        public DocumentBuilder fileReference(String fileReference) {
            this.fileReference = fileReference;
            return this;
        }

        public DocumentBuilder fileSize(Long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public DocumentBuilder hash(String hash) {
            this.hash = hash;
            return this;
        }

        public DocumentBuilder ownerDepartmentId(UUID ownerDepartmentId) {
            this.ownerDepartmentId = ownerDepartmentId;
            return this;
        }

        public DocumentBuilder parentId(UUID parentId) {
            this.parentId = parentId;
            return this;
        }

        public DocumentBuilder creatorDepartmentId(UUID creatorDepartmentId) {
            this.creatorDepartmentId = creatorDepartmentId;
            return this;
        }

        public DocumentBuilder createdBy(UUID createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public DocumentBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public DocumentBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public DocumentBuilder deletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
            return this;
        }

        public Document build() {
            return new Document(id, businessCode, title, fileReference, fileSize, hash,
                    ownerDepartmentId, parentId, creatorDepartmentId, createdBy,
                    createdAt, updatedAt, deletedAt);
        }
    }
}
