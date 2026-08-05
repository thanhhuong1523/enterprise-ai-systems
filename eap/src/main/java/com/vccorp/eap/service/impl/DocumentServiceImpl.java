package com.vccorp.eap.service.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.exception.ConcurrentUploadTimeoutException;
import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.dto.DocumentResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.service.DocumentService;
import com.vccorp.eap.service.allocator.BusinessCodeAllocator;
import com.vccorp.eap.service.coordinator.DocumentUploadCoordinator;
import com.vccorp.eap.service.helper.DeduplicationQueryResult;
import com.vccorp.eap.service.helper.DocumentDeduplicationManager;
import com.vccorp.eap.service.helper.UploadTransactionResult;
import com.vccorp.eap.service.mapper.DocumentMapper;
import com.vccorp.eap.service.storage.FileStorageService;
import com.vccorp.eap.service.storage.SinglePassStorageResult;
import com.vccorp.eap.service.validation.UploadValidator;

@Service
public class DocumentServiceImpl implements DocumentService {
    @Value("${eap.retry.max-attempts:5}")
    private final int maxLockRetries = 5;

    @Value("${eap.retry.base-delay-ms:350}")
    private final long baseDelayMs = 350;

    @Value("${eap.retry.max-delay-ms:2000}")
    private final long maxDelayMs = 2000;

    private final DocumentRepository documentRepository;
    private final DepartmentRepository departmentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentDeduplicationManager deduplicationManager;
    private final DocumentUploadCoordinator uploadCoordinator;
    private final BusinessCodeAllocator businessCodeAllocator;
    private final TransactionTemplate transactionTemplate;
    private final UploadValidator uploadValidator;
    private final DocumentMapper documentMapper;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DepartmentRepository departmentRepository,
                               FileStorageService fileStorageService,
                               DocumentDeduplicationManager deduplicationManager,
                               DocumentUploadCoordinator uploadCoordinator,
                               BusinessCodeAllocator businessCodeAllocator,
                               PlatformTransactionManager transactionManager,
                               UploadValidator uploadValidator,
                               DocumentMapper documentMapper) {
        this.documentRepository = documentRepository;
        this.departmentRepository = departmentRepository;
        this.fileStorageService = fileStorageService;
        this.deduplicationManager = deduplicationManager;
        this.uploadCoordinator = uploadCoordinator;
        this.businessCodeAllocator = businessCodeAllocator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.uploadValidator = uploadValidator;
        this.documentMapper = documentMapper;
    }

    @Override
    public DocumentResponse uploadOriginalDocument(String title, MultipartFile file, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
        uploadValidator.validateTitle(title);
        uploadValidator.validateUserDepartment(currentUser);

        SinglePassStorageResult storageResult;
        try {
            storageResult = uploadCoordinator.coordinate(file);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể lưu tệp tạm thời.");
        }

        String hash = storageResult.getHash();
        Path tempFilePath = storageResult.getTempFilePath();
        long fileSize = storageResult.getFileSize();
        UUID departmentId = currentUser.getDepartmentId();

        AtomicBoolean tempFileMoved = new AtomicBoolean(false);
        try {
            deduplicationManager.fastCheckDuplicate(hash, departmentId);

            // Kiểm tra trùng lặp gộp ngoài transaction để tìm file cũ có thể tái sử dụng
            DeduplicationQueryResult preCheck = deduplicationManager.doubleCheckDuplicate(hash, departmentId);
            String oldestFileRef = preCheck != null ? preCheck.getOldestFileRef() : null;

            String fileReference;
            if (oldestFileRef != null && fileStorageService.exists(oldestFileRef)) {
                fileReference = oldestFileRef;
                fileStorageService.deleteTempFileQuietly(tempFilePath);
                tempFileMoved.set(true);
            } else {
                // Di chuyển file tạm sang thư mục chính thức bên ngoài Transaction DB để rút ngắn transaction
                fileReference = fileStorageService.moveTempToPermanent(tempFilePath, hash);
                tempFileMoved.set(true);
            }

            for (int i = 0; i < maxLockRetries; i++) {
                try {
                    UploadTransactionResult txResult = transactionTemplate.execute(status -> {
                        boolean acquired = deduplicationManager.tryAcquireLock(departmentId, hash);
                        if (!acquired) {
                            status.setRollbackOnly();
                            return UploadTransactionResult.lockBusy();
                        }

                        DeduplicationQueryResult doubleCheck = deduplicationManager.doubleCheckDuplicate(hash, departmentId);
                        if (doubleCheck.isHasActiveInDept()) {
                            status.setRollbackOnly();
                            throw new BusinessException(ErrorCode.ERR_DUPLICATE_DOCUMENT, "Tài liệu đã tồn tại trong phòng ban.");
                        }

                        Document document = createDocument(title, hash, fileReference, fileSize, departmentId, currentUser);
                        Document saved = documentRepository.saveAndFlush(document);
                        return UploadTransactionResult.success(documentMapper.mapToResponse(saved));
                    });

                    if (txResult != null && txResult.status() == UploadTransactionResult.Status.SUCCESS) {
                        return txResult.response();
                    }
                } catch (DataIntegrityViolationException e) {
                    throw new BusinessException(ErrorCode.ERR_DUPLICATE_DOCUMENT, "Tài liệu đã tồn tại trong phòng ban.");
                }

                if (i < maxLockRetries - 1) {
                    sleepWithJitter(i);
                }
            }
            throw new ConcurrentUploadTimeoutException();
        } finally {
            if (!tempFileMoved.get()) {
                fileStorageService.deleteTempFileQuietly(tempFilePath);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listOriginalDocuments(int page, int size, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Document> documents = documentRepository.findByParentIdIsNullAndOwnerDepartmentId(
                currentUser.getDepartmentId(), pageRequest);
        return documents.map(documentMapper::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listSharedDocuments(int page, int size, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Document> documents = documentRepository.findByParentIdIsNotNullAndOwnerDepartmentId(
                currentUser.getDepartmentId(), pageRequest);
        return documents.map(documentMapper::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getOriginalDocumentDetail(UUID id, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
        Document document = findDocumentById(id);

        if (document.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        if (document.isOriginal()) {
            boolean isOwner = document.getOwnerDepartmentId().equals(currentUser.getDepartmentId());
            boolean hasAlias = isOwner || documentRepository.existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(
                    document.getId(), currentUser.getDepartmentId());
            if (!hasAlias) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
            }
        } else {
            boolean isOwner = document.getOwnerDepartmentId().equals(currentUser.getDepartmentId());
            boolean isCreator = document.getCreatorDepartmentId().equals(currentUser.getDepartmentId());
            if (!isOwner && !isCreator) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
            }
        }

        return documentMapper.mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocumentAliases(UUID id, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
        DocumentResponse doc = getOriginalDocumentDetail(id, currentUser);
        return documentRepository.findAllByParentIdAndDeletedAtIsNull(doc.getId()).stream()
                .map(documentMapper::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DocumentResponse updateOriginalDocument(UUID id, String title, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
        Document document = findDocumentById(id);

        if (document.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        if (!document.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
        }

        if (currentUser.getRole() != Role.ROLE_DEPT_MANAGER) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        if (title != null && !title.trim().isEmpty()) {
            if (title.trim().length() > 255) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề tài liệu không được vượt quá 255 ký tự.");
            }
            document.setTitle(title.trim());
        }
        document.setUpdatedAt(LocalDateTime.now());
        return documentMapper.mapToResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public void deleteOriginalDocument(UUID id, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
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
        documentRepository.saveAndFlush(originalDoc);
        documentRepository.softDeleteAliasesByOriginalId(id, now);
    }

    @Override
    @Transactional
    public DocumentResponse createAlias(CreateAliasRequest request, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
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

        uploadValidator.validateAliasTargetDepartment(request.aliasDepartmentId());

        if (!originalDoc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
        }

        if (uploadValidator.isBoardDepartment(originalDoc.getOwnerDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_BOARD_PROTECTION);
        }

        if (originalDoc.getOwnerDepartmentId().equals(request.aliasDepartmentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự chia sẻ tài liệu cho chính phòng ban của mình.");
        }

        boolean exists = documentRepository.existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(
                originalDoc.getId(), request.aliasDepartmentId());
        if (exists) {
            throw new BusinessException(ErrorCode.ERR_DUPLICATE_ALIAS);
        }

        UUID rawUuid = UUID.randomUUID();
        long aliasLsb = rawUuid.getLeastSignificantBits() | 1L;
        UUID aliasId = new UUID(rawUuid.getMostSignificantBits(), aliasLsb);

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
                .status(null)
                .workerId(null)
                .retryCount(null)
                .lastCompletedChunk(null)
                .totalChunks(null)
                .build();

        try {
            return documentMapper.mapToResponse(documentRepository.saveAndFlush(aliasDoc));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ERR_DUPLICATE_ALIAS);
        }
    }

    @Override
    @Transactional
    public void deleteAlias(UUID id, User currentUser) {
        uploadValidator.validateUserRole(currentUser);
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
        uploadValidator.validateUserRole(currentUser);
        Document doc = findDocumentById(id);

        if (doc.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
        }

        if (!doc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
        }

        if (doc.isAlias()) {
            doc = documentRepository.findById(doc.getParentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));
            if (doc.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND);
            }
        }

        try {
            return fileStorageService.loadFile(doc.getFileReference());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể đọc dữ liệu tệp.");
        }
    }

    private Document findDocumentById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));
    }

    private void sleepWithJitter(int retryCount) {
        try {
            long delayTemp = Math.min(maxDelayMs, baseDelayMs * (1L << retryCount));
            long jitter = new java.util.Random().nextInt(100) - 50;
            long sleepTime = Math.max(0L, delayTemp + jitter);
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // fastCheckDuplicate has been moved to DocumentDeduplicationManager

    private String resolveFileReference(DeduplicationQueryResult result, Path tempFile, String hash, AtomicBoolean moved) {
        String oldestFileRef = result.getOldestFileRef();
        if (oldestFileRef != null) {
            if (fileStorageService.exists(oldestFileRef)) {
                return oldestFileRef;
            }
        }
        String fileReference = fileStorageService.moveTempToPermanent(tempFile, hash);
        moved.set(true);
        return fileReference;
    }

    private Document createDocument(String title, String hash, String fileReference, long fileSize, UUID departmentId, User currentUser) {
        UUID rawUuid = UUID.randomUUID();
        UUID documentId = new UUID(rawUuid.getMostSignificantBits(),
                rawUuid.getLeastSignificantBits() & ~1L);

        String businessCode = businessCodeAllocator.allocate();

        return Document.builder()
                .id(documentId)
                .businessCode(businessCode)
                .title(title.trim())
                .fileReference(fileReference)
                .fileSize(fileSize)
                .hash(hash)
                .ownerDepartmentId(departmentId)
                .createdBy(currentUser.getId())
                .createdAt(LocalDateTime.now())
                .status("READY")
                .workerId(null)
                .retryCount(0)
                .lastCompletedChunk(0)
                .totalChunks(0)
                .build();
    }
}