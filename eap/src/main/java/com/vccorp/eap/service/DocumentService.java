package com.vccorp.eap.service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.util.HashUtils;
import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DepartmentRepository departmentRepository;

    @Value("${eap.upload.dir:./eap-storage}")
    private String uploadDir;

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".docx", ".xlsx", ".pptx");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @Transactional
    public Document uploadOriginalDocument(String title, MultipartFile file, User currentUser) {
        if (title == null || title.trim().isEmpty() || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề và tệp đính kèm không được trống.");
        }

        if (title.trim().length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề tài liệu không được vượt quá 255 ký tự.");
        }

        // User must belong to a department (SYSTEM_ADMIN has no dept, cannot upload)
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

        // Generate original UUID with LSB = 0
        UUID rawUuid = UUID.randomUUID();
        long mostSigBits = rawUuid.getMostSignificantBits();
        long originalLsb = rawUuid.getLeastSignificantBits() & ~1L;
        UUID documentId = new UUID(mostSigBits, originalLsb);

        // Generate business code
        String businessCode = "ORIG_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Check if directory exists
        File dir = new File(uploadDir).getAbsoluteFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File targetFile = new File(dir, documentId.toString());
        String hash;
        try {
            java.nio.file.Files.copy(file.getInputStream(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            hash = HashUtils.calculateSha256(Files.readAllBytes(targetFile.toPath()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể lưu tệp vật lý.");
        }

        Document document = Document.builder()
                .id(documentId)
                .businessCode(businessCode)
                .title(title.trim())
                .fileReference(targetFile.getAbsolutePath())
                .fileSize(file.getSize())
                .hash(hash)
                .ownerDepartmentId(currentUser.getDepartmentId())
                .createdBy(currentUser.getId())
                .createdAt(LocalDateTime.now())
                .build();

        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public Page<Document> listOriginalDocuments(int page, int size, User currentUser) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (currentUser.getRole() == Role.SYSTEM_ADMIN) {
            return documentRepository.findByParentIdIsNull(pageRequest);
        }
        return documentRepository.findByParentIdIsNullAndOwnerDepartmentId(currentUser.getDepartmentId(), pageRequest);
    }

    @Transactional(readOnly = true)
    public Page<Document> listSharedDocuments(int page, int size, User currentUser) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (currentUser.getRole() == Role.SYSTEM_ADMIN) {
            return Page.empty();
        }
        return documentRepository.findByParentIdIsNotNullAndOwnerDepartmentId(currentUser.getDepartmentId(), pageRequest);
    }

    @Transactional(readOnly = true)
    public Document getOriginalDocumentDetail(UUID id, User currentUser) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (document.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        // Check permissions
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

        return document;
    }

    @Transactional(readOnly = true)
    public List<Document> listDocumentAliases(UUID id, User currentUser) {
        Document doc = getOriginalDocumentDetail(id, currentUser);
        return documentRepository.findAllByParentIdAndDeletedAtIsNull(doc.getId());
    }

    @Transactional
    public Document updateOriginalDocument(UUID id, String title, User currentUser) {
        Document document = getOriginalDocumentDetail(id, currentUser);
        
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
        return documentRepository.save(document);
    }

    @Transactional
    public void deleteOriginalDocument(UUID id, User currentUser) {
        Document originalDoc = documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (!originalDoc.isOriginal()) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        // Verify role and department ownership
        if (currentUser.getRole() != Role.ROLE_DEPT_MANAGER) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        if (!originalDoc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        originalDoc.setDeletedAt(now);
        documentRepository.save(originalDoc);

        // Cascade soft delete of aliases
        documentRepository.softDeleteAliasesByOriginalId(id, now);
    }

    @Transactional
    public Document createAlias(CreateAliasRequest request, User currentUser) {
        if (currentUser.getRole() == Role.ROLE_BOARD) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE, "Ban Giám Đốc không được phép tạo liên kết Alias.");
        }

        if (request.getOriginalDocumentId() == null || request.getAliasDepartmentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ID tài liệu gốc và ID phòng ban nhận không được trống.");
        }

        // Lock original document
        Document originalDoc = documentRepository.findByIdForUpdate(request.getOriginalDocumentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        // Anti-chaining
        if (!originalDoc.isOriginal()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tạo liên kết cho một tài liệu Alias khác.");
        }

        if (!departmentRepository.existsById(request.getAliasDepartmentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban nhận không tồn tại.");
        }

        // Verify current user's department owns the original document
        if (!originalDoc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
        }

        // BOARD boundary rules
        if (isBoardDepartment(originalDoc.getOwnerDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_BOARD_PROTECTION);
        }

        // No self-sharing
        if (originalDoc.getOwnerDepartmentId().equals(request.getAliasDepartmentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự chia sẻ tài liệu cho chính phòng ban của mình.");
        }

        // Unique active alias check
        boolean exists = documentRepository.existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(
                originalDoc.getId(), request.getAliasDepartmentId()
        );
        if (exists) {
            throw new BusinessException(ErrorCode.ERR_DUPLICATE_ALIAS);
        }

        // Generate Alias UUID with LSB = 1
        UUID rawUuid = UUID.randomUUID();
        long mostSigBits = rawUuid.getMostSignificantBits();
        long aliasLsb = rawUuid.getLeastSignificantBits() | 1L;
        UUID aliasId = new UUID(mostSigBits, aliasLsb);

        // Generate Alias business code
        String businessCode = "ALIA_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Document aliasDoc = Document.builder()
                .id(aliasId)
                .businessCode(businessCode)
                .title(originalDoc.getTitle())
                .fileReference(null) // Must be null for alias
                .fileSize(null)      // Must be null for alias
                .hash(null)          // Must be null for alias
                .ownerDepartmentId(request.getAliasDepartmentId())
                .parentId(originalDoc.getId())
                .creatorDepartmentId(originalDoc.getOwnerDepartmentId())
                .createdBy(currentUser.getId())
                .createdAt(LocalDateTime.now())
                .build();

        return documentRepository.save(aliasDoc);
    }

    @Transactional
    public void deleteAlias(UUID id, User currentUser) {
        Document alias = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (alias.getDeletedAt() != null || !alias.isAlias()) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        // Lock original document
        Document original = documentRepository.findByIdForUpdate(alias.getParentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        // Check ownership of the creator department
        if (!original.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
        }

        // Allow ROLE_DEPT_MANAGER and ROLE_EMPLOYEE to delete aliases
        if (currentUser.getRole() != Role.ROLE_DEPT_MANAGER && currentUser.getRole() != Role.ROLE_EMPLOYEE) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE, "Chỉ Trưởng phòng hoặc Nhân viên mới được phép thu hồi liên kết Alias.");
        }

        alias.setDeletedAt(LocalDateTime.now());
        documentRepository.save(alias);
    }

    @Transactional(readOnly = true)
    public byte[] resolveAlias(UUID id, User currentUser) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (doc.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        // If it is an Alias document:
        if (doc.isAlias()) {
            // Check permission: user's department must be the owner of the alias document
            if (!doc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
            }

            // Get original document
            doc = documentRepository.findById(doc.getParentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

            if (doc.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
            }
        } else {
            // If it is an Original document, user's department must be the owner of this original document
            if (!doc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
            }
        }

        File file = new File(doc.getFileReference());
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Tệp tin vật lý không tồn tại.");
        }

        try {
            return Files.readAllBytes(file.toPath());
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
