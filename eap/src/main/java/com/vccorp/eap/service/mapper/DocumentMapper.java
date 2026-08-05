package com.vccorp.eap.service.mapper;

import com.vccorp.eap.dto.DocumentResponse;
import com.vccorp.eap.model.Document;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    public DocumentResponse mapToResponse(Document doc) {
        if (doc == null) return null;
        return DocumentResponse.builder(
                        doc.getId(), doc.getBusinessCode(), doc.getTitle(),
                        doc.getOwnerDepartmentId(), doc.getCreatedAt())
                .fileSize(doc.getFileSize())
                .hash(doc.getHash())
                .parentId(doc.getParentId())
                .creatorDepartmentId(doc.getCreatorDepartmentId())
                .createdBy(doc.getCreatedBy())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
