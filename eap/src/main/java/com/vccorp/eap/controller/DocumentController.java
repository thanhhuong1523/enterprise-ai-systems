package com.vccorp.eap.controller;

import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.dto.DocumentResponse;
import com.vccorp.eap.infrastructure.security.SecurityContextHelper;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/original-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadOriginalDocument(
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        DocumentResponse document = documentService.uploadOriginalDocument(title, file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(document));
    }

    @GetMapping("/original-documents")
    public ApiResponse<Page<DocumentResponse>> listOriginalDocuments(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        Page<DocumentResponse> documents = documentService.listOriginalDocuments(page, size, currentUser);
        return ApiResponse.success(documents);
    }

    @GetMapping("/original-documents/{id}")
    public ApiResponse<DocumentResponse> getOriginalDocumentDetail(@PathVariable("id") UUID id) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        DocumentResponse document = documentService.getOriginalDocumentDetail(id, currentUser);
        return ApiResponse.success(document);
    }

    @GetMapping("/original-documents/{id}/aliases")
    public ApiResponse<List<DocumentResponse>> listDocumentAliases(@PathVariable("id") UUID id) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        List<DocumentResponse> aliases = documentService.listDocumentAliases(id, currentUser);
        return ApiResponse.success(aliases);
    }

    @DeleteMapping("/original-documents/{id}")
    public ApiResponse<Void> deleteOriginalDocument(@PathVariable("id") UUID id) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        documentService.deleteOriginalDocument(id, currentUser);
        return ApiResponse.success(null);
    }

    @PutMapping("/original-documents/{id}")
    public ApiResponse<DocumentResponse> updateOriginalDocument(
            @PathVariable("id") UUID id,
            @Valid @RequestBody com.vccorp.eap.dto.UpdateDocumentRequest request) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        DocumentResponse document = documentService.updateOriginalDocument(id, request.title(), currentUser);
        return ApiResponse.success(document);
    }

    @PostMapping("/alias-documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentResponse> createAlias(@Valid @RequestBody CreateAliasRequest request) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        DocumentResponse alias = documentService.createAlias(request, currentUser);
        return ApiResponse.success(alias);
    }

    @GetMapping("/alias-documents")
    public ApiResponse<Page<DocumentResponse>> listSharedDocuments(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        Page<DocumentResponse> documents = documentService.listSharedDocuments(page, size, currentUser);
        return ApiResponse.success(documents);
    }

    @GetMapping("/alias-documents/{id}")
    public ResponseEntity<byte[]> resolveAlias(@PathVariable("id") UUID id) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        byte[] fileBytes = documentService.resolveAlias(id, currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .body(fileBytes);
    }

    @DeleteMapping("/alias-documents/{id}")
    public ApiResponse<Void> deleteAlias(@PathVariable("id") UUID id) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        documentService.deleteAlias(id, currentUser);
        return ApiResponse.success(null);
    }
}
