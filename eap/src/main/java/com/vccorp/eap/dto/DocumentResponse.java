package com.vccorp.eap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private UUID id;
    private String businessCode;
    private String title;
    private Long fileSize;
    private String hash;
    private UUID ownerDepartmentId;
    private UUID parentId;
    private UUID creatorDepartmentId;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isAlias() {
        return id != null && (id.getLeastSignificantBits() & 1L) == 1L;
    }

    public boolean isOriginal() {
        return id != null && (id.getLeastSignificantBits() & 1L) == 0L;
    }
}
