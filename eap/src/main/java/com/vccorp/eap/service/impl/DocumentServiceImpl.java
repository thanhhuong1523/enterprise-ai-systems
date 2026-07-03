package com.vccorp.eap.service.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.util.HashUtils;
import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.dto.DocumentResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.service.DocumentService;
import com.vccorp.eap.service.StorageService;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DepartmentRepository departmentRepository;
    private final StorageService storageService;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DepartmentRepository departmentRepository,
                               StorageService storageService) {
        this.documentRepository = documentRepository;
        this.departmentRepository = departmentRepository;
        this.storageService = storageService;
    }

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".docx", ".xlsx", ".pptx");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private Document findDocumentById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));
    }

    private DocumentResponse mapToResponse(Document doc) {
        if (doc == null) return null;
        return DocumentResponse.builder(doc.getId(), doc.getBusinessCode(), doc.getTitle(), doc.getOwnerDepartmentId(), doc.getCreatedAt())
                .fileSize(doc.getFileSize())
                .hash(doc.getHash())
                .parentId(doc.getParentId())
                .creatorDepartmentId(doc.getCreatorDepartmentId())
                .createdBy(doc.getCreatedBy())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public DocumentResponse uploadOriginalDocument(String title, MultipartFile file, User currentUser) {
        if (title == null || title.trim().isEmpty() || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề và tệp đính kèm không được trống.");
        }

        if (title.trim().length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề tài liệu không được vượt quá 255 ký tự.");
        }

        if (currentUser.getDepartmentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Người dùng chưa được gán vào phòng ban. Vui lòng liên hệ quản trị viên.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên tệp không hợp lệ hoặc thiếu phần mở rộng.");
        }

        String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng file không được hỗ trợ.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dung lượng file vượt quá giới hạn 50MB.");
        }

        // Validate real file content using Apache Tika
        Tika tika = new Tika();
        try {
            String mimeType = tika.detect(file.getInputStream());
            if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng file thực tế không được hỗ trợ.");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể xác minh định dạng file thực tế.");
        }

        UUID rawUuid = UUID.randomUUID();
        long mostSigBits = rawUuid.getMostSignificantBits();
        long originalLsb = rawUuid.getLeastSignificantBits() & ~1L;
        UUID documentId = new UUID(mostSigBits, originalLsb);

        String businessCode = "ORIG_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String fileReference;
        String hash;
        try {
            fileReference = storageService.storeFile(file, documentId.toString());
            hash = HashUtils.calculateSha256(storageService.loadFile(fileReference));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể lưu tệp vật lý.");
        }

        Document document = Document.builder()
                .id(documentId)
                .businessCode(businessCode)
                .title(title.trim())
                .fileReference(fileReference)
                .fileSize(file.getSize())
                .hash(hash)
                .ownerDepartmentId(currentUser.getDepartmentId())
                .createdBy(currentUser.getId())
                .createdAt(LocalDateTime.now())
                .build();

        return mapToResponse(documentRepository.save(document));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listOriginalDocuments(int page, int size, User currentUser) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Document> documents;
        if (currentUser.getRole() == Role.SYSTEM_ADMIN) {
            documents = documentRepository.findByParentIdIsNull(pageRequest);
        } else {
            documents = documentRepository.findByParentIdIsNullAndOwnerDepartmentId(currentUser.getDepartmentId(), pageRequest);
        }
        return documents.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listSharedDocuments(int page, int size, User currentUser) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (currentUser.getRole() == Role.SYSTEM_ADMIN) {
            return Page.empty();
        }
        Page<Document> documents = documentRepository.findByParentIdIsNotNullAndOwnerDepartmentId(currentUser.getDepartmentId(), pageRequest);
        return documents.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getOriginalDocumentDetail(UUID id, User currentUser) {
        Document document = findDocumentById(id);

        if (document.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            if (document.isOriginal()) {
                if (!document.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
                    boolean hasAlias = documentRepository.existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(document.getId(), currentUser.getDepartmentId());
                    if (!hasAlias) {
                        throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
                    }
                }
            } else {
                if (!document.getOwnerDepartmentId().equals(currentUser.getDepartmentId()) &&
                    !document.getCreatorDepartmentId().equals(currentUser.getDepartmentId())) {
                    throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
                }
            }
        }

        return mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocumentAliases(UUID id, User currentUser) {
        DocumentResponse doc = getOriginalDocumentDetail(id, currentUser);
        return documentRepository.findAllByParentIdAndDeletedAtIsNull(doc.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DocumentResponse updateOriginalDocument(UUID id, String title, User currentUser) {
        Document document = findDocumentById(id);
        
        if (document.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            if (!document.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
            }
        }

        if (currentUser.getRole() != Role.ROLE_DEPT_MANAGER && currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }
        
        if (title != null && !title.trim().isEmpty()) {
            if (title.trim().length() > 255) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề tài liệu không được vượt quá 255 ký tự.");
            }
            document.setTitle(title.trim());
        }
        document.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public void deleteOriginalDocument(UUID id, User currentUser) {
        Document originalDoc = documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (!originalDoc.isOriginal()) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        if (currentUser.getRole() != Role.ROLE_DEPT_MANAGER) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        if (!originalDoc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        originalDoc.setDeletedAt(now);
        documentRepository.save(originalDoc);

        documentRepository.softDeleteAliasesByOriginalId(id, now);
    }

    @Override
    @Transactional
    public DocumentResponse createAlias(CreateAliasRequest request, User currentUser) {
        if (currentUser.getRole() == Role.ROLE_BOARD) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE, "Ban Giám Đốc không được phép tạo liên kết Alias.");
        }

        if (request.originalDocumentId() == null || request.aliasDepartmentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ID tài liệu gốc và ID phòng ban nhận không được trống.");
        }

        Document originalDoc = documentRepository.findByIdForUpdate(request.originalDocumentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (!originalDoc.isOriginal()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tạo liên kết cho một tài liệu Alias khác.");
        }

        if (!departmentRepository.existsById(request.aliasDepartmentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban nhận không tồn tại.");
        }

        if (!originalDoc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
        }

        if (isBoardDepartment(originalDoc.getOwnerDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_BOARD_PROTECTION);
        }

        if (originalDoc.getOwnerDepartmentId().equals(request.aliasDepartmentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự chia sẻ tài liệu cho chính phòng ban của mình.");
        }

        boolean exists = documentRepository.existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(
                originalDoc.getId(), request.aliasDepartmentId()
        );
        if (exists) {
            throw new BusinessException(ErrorCode.ERR_DUPLICATE_ALIAS);
        }

        UUID rawUuid = UUID.randomUUID();
        long mostSigBits = rawUuid.getMostSignificantBits();
        long aliasLsb = rawUuid.getLeastSignificantBits() | 1L;
        UUID aliasId = new UUID(mostSigBits, aliasLsb);

        String businessCode = "ALIA_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Document aliasDoc = Document.builder()
                .id(aliasId)
                .businessCode(businessCode)
                .title(originalDoc.getTitle())
                .fileReference(null)
                .fileSize(null)
                .hash(null)
                .ownerDepartmentId(request.aliasDepartmentId())
                .parentId(originalDoc.getId())
                .creatorDepartmentId(originalDoc.getOwnerDepartmentId())
                .createdBy(currentUser.getId())
                .createdAt(LocalDateTime.now())
                .build();

        return mapToResponse(documentRepository.save(aliasDoc));
    }

    @Override
    @Transactional
    public void deleteAlias(UUID id, User currentUser) {
        Document alias = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (alias.getDeletedAt() != null || !alias.isAlias()) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        Document original = documentRepository.findByIdForUpdate(alias.getParentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (!original.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
        }

        if (currentUser.getRole() != Role.ROLE_DEPT_MANAGER && currentUser.getRole() != Role.ROLE_EMPLOYEE) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE, "Chỉ Trưởng phòng hoặc Nhân viên mới được phép thu hồi liên kết Alias.");
        }

        alias.setDeletedAt(LocalDateTime.now());
        documentRepository.save(alias);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] resolveAlias(UUID id, User currentUser) {
        Document doc = findDocumentById(id);

        if (doc.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        if (doc.isAlias()) {
            if (!doc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
            }
            doc = documentRepository.findById(doc.getParentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

            if (doc.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
            }
        } else {
            if (!doc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
            }
        }

        try {
            return storageService.loadFile(doc.getFileReference());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể đọc dữ liệu tệp.");
        }
    }

    private boolean isBoardDepartment(UUID deptId) {
        if (deptId == null) return false;
        return departmentRepository.findById(deptId)
                .map(d -> "BOARD".equalsIgnoreCase(d.getCode()))
                .orElse(false);
    }
}
