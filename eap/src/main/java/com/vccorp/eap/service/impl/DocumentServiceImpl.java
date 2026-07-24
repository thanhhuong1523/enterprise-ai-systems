package com.vccorp.eap.service.impl;

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
import com.vccorp.eap.service.helper.DocumentDeduplicationHelper;
import com.vccorp.eap.service.helper.UploadTransactionResult;
import com.vccorp.eap.service.lock.DocumentAdvisoryLockHandler;
import com.vccorp.eap.service.storage.FileStorageService;
import com.vccorp.eap.service.storage.SinglePassStorageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Orchestrator chính điều phối toàn bộ luồng tải lên tài liệu (§1.4, §4.2, §8.2).
 * Trách nhiệm:
 * - Kiểm tra quyền (RBAC)
 * - Điều phối Pha 1 (ngoài giao dịch): Fast-Check
 * - Điều phối Pha 2 (trong giao dịch): Advisory Lock → Double-Check → Lưu tệp → INSERT
 * - Quản lý vòng đời tệp tạm qua try-finally
 * - Xử lý DataIntegrityViolationException (lá chắn cuối §6.2)
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    // Retry config cho advisory lock (§4.2, ADR-008)
    private static final int MAX_LOCK_RETRIES = 5;

    private final DocumentRepository documentRepository;
    private final DepartmentRepository departmentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentAdvisoryLockHandler advisoryLockHandler;
    private final DocumentDeduplicationHelper deduplicationHelper;
    private final DocumentUploadCoordinator uploadCoordinator;
    private final BusinessCodeAllocator businessCodeAllocator;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DepartmentRepository departmentRepository,
                               FileStorageService fileStorageService,
                               DocumentAdvisoryLockHandler advisoryLockHandler,
                               DocumentDeduplicationHelper deduplicationHelper,
                               DocumentUploadCoordinator uploadCoordinator,
                               BusinessCodeAllocator businessCodeAllocator,
                               PlatformTransactionManager transactionManager,
                               JdbcTemplate jdbcTemplate) {
        this.documentRepository = documentRepository;
        this.departmentRepository = departmentRepository;
        this.fileStorageService = fileStorageService;
        this.advisoryLockHandler = advisoryLockHandler;
        this.deduplicationHelper = deduplicationHelper;
        this.uploadCoordinator = uploadCoordinator;
        this.businessCodeAllocator = businessCodeAllocator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DocumentResponse uploadOriginalDocument(String title, MultipartFile file, User currentUser) {
        // Bước 1: Validate vai trò và dữ liệu đầu vào
        validateUserRole(currentUser);
        validateTitle(title);

        if (currentUser.getDepartmentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Người dùng chưa được gán vào phòng ban. Vui lòng liên hệ quản trị viên.");
        }

        // Bước 2: Pha 1 — Validation + Ghi tệp tạm + Tính hash (§4.2 Phương thức chính bước 3)
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

        // Bước 3: Bảo vệ tài nguyên bằng try-finally
        AtomicBoolean tempFileMoved = new AtomicBoolean(false);
        try {
            // Bước 4: Fast-Check ngoài giao dịch
            fastCheckDuplicate(hash, departmentId);

            // Bước 5: Pha 2 — Single-Connection TransactionTemplate với retry loop ngoài giao dịch
            for (int i = 0; i < MAX_LOCK_RETRIES; i++) {
                try {
                    UploadTransactionResult txResult = transactionTemplate.execute(status -> {
                        // Thử lấy khóa cố vấn (nếu không lấy được, PostgreSQL sẽ trả về false hoặc ném exception nếu DB lỗi)
                        boolean acquired = advisoryLockHandler.tryAcquireLock(departmentId, hash);
                        if (!acquired) {
                            // Không lấy được khóa -> Rollback ngay giao dịch hiện tại để giải phóng Connection về Pool
                            status.setRollbackOnly();
                            return UploadTransactionResult.lockBusy();
                        }

                        // Double-Check trong giao dịch
                        DeduplicationQueryResult doubleCheck = deduplicationHelper.executeAggregateCheck(jdbcTemplate, hash, departmentId);

                        if (doubleCheck.isHasActiveInDept()) {
                            // Luồng khác vừa commit thành công trong khi chờ khóa
                            log.debug("Double-Check: duplicate detected for hash={}, dept={}", hash, departmentId);
                            status.setRollbackOnly();

                            throw new BusinessException(ErrorCode.ERR_DUPLICATE_DOCUMENT, "Tài liệu đã tồn tại trong phòng ban.");
                        }

                        // Bước lưu trữ vật lý (§4.2 bước 4)
                        String fileReference = resolveFileReference(doubleCheck, tempFilePath, hash, tempFileMoved);

                        // INSERT metadata (§4.2 bước 5, Query 3) - sử dụng saveAndFlush để kích hoạt lỗi Unique constraint ngay trong transaction block
                        Document document = createDocument(title, hash, fileReference, fileSize, departmentId, currentUser);
                        Document saved = documentRepository.saveAndFlush(document);
                        return UploadTransactionResult.success(mapToResponse(saved));
                    });

                    if (txResult != null) {
                        if (txResult.status() == UploadTransactionResult.Status.SUCCESS) {
                            return txResult.response();
                        }
                    }
                } catch (DataIntegrityViolationException e) {
                    // §6.2: Lá chắn cuối — vi phạm UNIQUE constraint bất thường
                    log.warn("UNIQUE constraint violation (last-resort) for hash={}, dept={}", hash, departmentId, e);

                    throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Lỗi hệ thống khi xử lý tải lên tài liệu.");
                }

                // Nếu khóa bị bận (Status.LOCK_BUSY), ngủ ngoài giao dịch (không giữ Connection) rồi thử lại
                if (i < MAX_LOCK_RETRIES - 1) {
                    sleepWithJitter(i);
                }
            }
            throw new ConcurrentUploadTimeoutException();
        } finally {
            // §4.2 Khối Dọn dẹp: xóa tệp tạm nếu chưa được atomic rename
            if (!tempFileMoved.get()) {
                fileStorageService.deleteTempFileQuietly(tempFilePath);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listOriginalDocuments(int page, int size, User currentUser) {
        validateUserRole(currentUser);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Document> documents = documentRepository.findByParentIdIsNullAndOwnerDepartmentId(
                currentUser.getDepartmentId(), pageRequest);
        return documents.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listSharedDocuments(int page, int size, User currentUser) {
        validateUserRole(currentUser);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Document> documents = documentRepository.findByParentIdIsNotNullAndOwnerDepartmentId(
                currentUser.getDepartmentId(), pageRequest);
        return documents.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getOriginalDocumentDetail(UUID id, User currentUser) {
        validateUserRole(currentUser);
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

        return mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocumentAliases(UUID id, User currentUser) {
        validateUserRole(currentUser);
        DocumentResponse doc = getOriginalDocumentDetail(id, currentUser);
        return documentRepository.findAllByParentIdAndDeletedAtIsNull(doc.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DocumentResponse updateOriginalDocument(UUID id, String title, User currentUser) {
        validateUserRole(currentUser);
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
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Tiêu đề tài liệu không được vượt quá 255 ký tự.");
            }
            document.setTitle(title.trim());
        }
        document.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public void deleteOriginalDocument(UUID id, User currentUser) {
        validateUserRole(currentUser);
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
        validateUserRole(currentUser);
        if (currentUser.getRole() == Role.ROLE_BOARD) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE,
                    "Ban Giám Đốc không được phép tạo liên kết Alias.");
        }

        if (request.originalDocumentId() == null || request.aliasDepartmentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "ID tài liệu gốc và ID phòng ban nhận không được trống.");
        }

        Document originalDoc = documentRepository.findByIdForUpdate(request.originalDocumentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));

        if (!originalDoc.isOriginal()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Không thể tạo liên kết cho một tài liệu Alias khác.");
        }

        if (!departmentRepository.existsById(request.aliasDepartmentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban nhận không tồn tại.");
        }

        validateAliasTargetDepartment(request.aliasDepartmentId());

        if (!originalDoc.getOwnerDepartmentId().equals(currentUser.getDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
        }

        if (isBoardDepartment(originalDoc.getOwnerDepartmentId())) {
            throw new BusinessException(ErrorCode.ERR_BOARD_PROTECTION);
        }

        if (originalDoc.getOwnerDepartmentId().equals(request.aliasDepartmentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Không thể tự chia sẻ tài liệu cho chính phòng ban của mình.");
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
                .build();

        return mapToResponse(documentRepository.save(aliasDoc));
    }

    @Override
    @Transactional
    public void deleteAlias(UUID id, User currentUser) {
        validateUserRole(currentUser);
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
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE,
                    "Chỉ Trưởng phòng hoặc Nhân viên mới được phép thu hồi liên kết Alias.");
        }

        alias.setDeletedAt(LocalDateTime.now());
        documentRepository.save(alias);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] resolveAlias(UUID id, User currentUser) {
        validateUserRole(currentUser);
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

    private void validateUserRole(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED);
        }
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE, "Quản trị viên hệ thống không được phép thao tác tài liệu.");
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề và tệp đính kèm không được trống.");
        }
        if (title.trim().length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề tài liệu không được vượt quá 255 ký tự.");
        }
    }

    private Document findDocumentById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND));
    }

    /**
     * Map Document entity → DocumentResponse DTO.
     * @param doc        entity
     */
    private DocumentResponse mapToResponse(Document doc) {
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

    private void validateAliasTargetDepartment(UUID aliasDepartmentId) {
        if (isBoardDepartment(aliasDepartmentId)) {
            throw new BusinessException(ErrorCode.ERR_BOARD_PROTECTION,
                    "Không thể chia sẻ tài liệu đến phòng Ban Giám Đốc.");
        }
    }

    private boolean isBoardDepartment(UUID deptId) {
        if (deptId == null) return false;
        return departmentRepository.findById(deptId)
                .map(d -> "BOARD".equalsIgnoreCase(d.getCode()))
                .orElse(false);
    }

    private void sleepWithJitter(int retryCount) {
        try {
            int baseSleep = 350; // ms
            int jitter = new java.util.Random().nextInt(100);
            Thread.sleep(((long) baseSleep * (retryCount + 1)) + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Fast-Check ngoài transaction.
     * Nếu đã tồn tại tài liệu hoạt động cùng hash trong cùng phòng ban
     * thì dừng ngay và trả về HTTP 409 Conflict.
     */
    private void fastCheckDuplicate(String hash, UUID departmentId) {
        DeduplicationQueryResult result = deduplicationHelper.executeAggregateCheck(jdbcTemplate, hash, departmentId);

        if (result.isHasActiveInDept()) {
            throw new BusinessException(ErrorCode.ERR_DUPLICATE_DOCUMENT);
        }
    }

    private String resolveFileReference(DeduplicationQueryResult result, Path tempFile, String hash, AtomicBoolean moved) {
        String oldestFileRef = result.getOldestFileRef();

        if (oldestFileRef != null) {
            log.debug("SIS: reusing physical file={} for hash={}", oldestFileRef, hash);
            return oldestFileRef;
        }
        log.debug("Moving temporary file to permanent storage. hash={}", hash);

        String fileReference = fileStorageService.moveTempToPermanent(tempFile, hash);
        moved.set(true);

        return fileReference;
    }

    private Document createDocument(String title, String hash, String fileReference, long fileSize, UUID departmentId, User currentUser) {
        // Sinh ID với LSB chẵn → tài liệu gốc (không phải alias)
        UUID rawUuid = UUID.randomUUID();
        UUID documentId = new UUID(rawUuid.getMostSignificantBits(),
                rawUuid.getLeastSignificantBits() & ~1L);

        // Phân bổ business_code từ sequence (§4.2 bước 5, ADR-010)
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
                .build();
    }
}