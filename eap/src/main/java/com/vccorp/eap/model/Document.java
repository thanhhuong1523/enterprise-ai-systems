package com.vccorp.eap.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    public boolean isAlias() {
        return id != null && (id.getLeastSignificantBits() & 1L) == 1L;
    }

    public boolean isOriginal() {
        return id != null && (id.getLeastSignificantBits() & 1L) == 0L;
    }
}
