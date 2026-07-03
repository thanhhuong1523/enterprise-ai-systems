package com.vccorp.eap.service;

import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.dto.DocumentResponse;
import com.vccorp.eap.model.User;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    DocumentResponse uploadOriginalDocument(String title, MultipartFile file, User currentUser);
    Page<DocumentResponse> listOriginalDocuments(int page, int size, User currentUser);
    Page<DocumentResponse> listSharedDocuments(int page, int size, User currentUser);
    DocumentResponse getOriginalDocumentDetail(UUID id, User currentUser);
    List<DocumentResponse> listDocumentAliases(UUID id, User currentUser);
    DocumentResponse updateOriginalDocument(UUID id, String title, User currentUser);
    void deleteOriginalDocument(UUID id, User currentUser);
    DocumentResponse createAlias(CreateAliasRequest request, User currentUser);
    void deleteAlias(UUID id, User currentUser);
    byte[] resolveAlias(UUID id, User currentUser);
}
