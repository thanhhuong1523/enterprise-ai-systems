package com.vccorp.eap.service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.exception.ConcurrentUploadTimeoutException;
import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.dto.DocumentResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.service.allocator.BusinessCodeAllocator;
import com.vccorp.eap.service.coordinator.DocumentUploadCoordinator;
import com.vccorp.eap.service.helper.DeduplicationQueryResult;
import com.vccorp.eap.service.helper.DocumentDeduplicationHelper;
import com.vccorp.eap.service.impl.DocumentServiceImpl;
import com.vccorp.eap.service.lock.DocumentAdvisoryLockHandler;
import com.vccorp.eap.service.storage.FileStorageService;
import com.vccorp.eap.service.storage.SinglePassStorageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private com.vccorp.eap.service.helper.DocumentDeduplicationManager deduplicationManager;

    @Mock
    private DocumentUploadCoordinator uploadCoordinator;

    @Mock
    private BusinessCodeAllocator businessCodeAllocator;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    private com.vccorp.eap.service.validation.UploadValidator uploadValidator;
    private com.vccorp.eap.service.mapper.DocumentMapper documentMapper;
    private DocumentServiceImpl documentService;

    @TempDir
    File tempDir;

    private User employeeUser;
    private User managerUser;
    private UUID deptId;
    private UUID boardDeptId;

    @BeforeEach
    void setUp() {
        uploadValidator = new com.vccorp.eap.service.validation.impl.UploadValidatorImpl(departmentRepository);
        documentMapper = new com.vccorp.eap.service.mapper.DocumentMapper();
        documentService = new DocumentServiceImpl(
                documentRepository,
                departmentRepository,
                fileStorageService,
                deduplicationManager,
                uploadCoordinator,
                businessCodeAllocator,
                transactionManager,
                uploadValidator,
                documentMapper
        );

        deptId = UUID.randomUUID();
        boardDeptId = UUID.randomUUID();
        employeeUser = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
                .departmentId(deptId)
                .build();
        managerUser = User.builder()
                .id(UUID.randomUUID())
                .username("manager")
                .role(Role.ROLE_DEPT_MANAGER)
                .departmentId(deptId)
                .build();
    }

    private void mockTransactionAndConnection() throws SQLException {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void uploadOriginalDocument_Success() throws Exception {

        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "%PDF-1.4 mock pdf content".getBytes()
        );

        Path tempPath = tempDir.toPath().resolve("temp_file");

        SinglePassStorageResult storageResult =
                new SinglePassStorageResult(
                        "dummyhash123",
                        100L,
                        tempPath
                );

        when(uploadCoordinator.coordinate(any()))
                .thenReturn(storageResult);

        // Fast check & double check
        when(deduplicationManager.doubleCheckDuplicate(
                eq("dummyhash123"),
                eq(deptId)))
                .thenReturn(new DeduplicationQueryResult(
                        false,
                        null,
                        null
                ));

        // Advisory lock thành công
        when(deduplicationManager.tryAcquireLock(
                deptId,
                "dummyhash123"))
                .thenReturn(true);

        // Move file
        when(fileStorageService.moveTempToPermanent(
                tempPath,
                "dummyhash123"))
                .thenReturn("/storage/dummyhash123");

        // Business code
        when(businessCodeAllocator.allocate())
                .thenReturn("ORIG_00000001");

        // Save document
        when(documentRepository.saveAndFlush(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        DocumentResponse response =
                documentService.uploadOriginalDocument(
                        "Test Doc",
                        file,
                        employeeUser
                );

        // Assert
        assertNotNull(response);
        assertEquals("Test Doc", response.getTitle());
        assertEquals(deptId, response.getOwnerDepartmentId());
        assertEquals("dummyhash123", response.getHash());
        assertEquals("ORIG_00000001", response.getBusinessCode());
        assertTrue(response.isOriginal());
        assertFalse(response.isAlias());

        verify(uploadCoordinator).coordinate(any());
        verify(deduplicationManager)
                .tryAcquireLock(deptId, "dummyhash123");
        verify(fileStorageService)
                .moveTempToPermanent(tempPath, "dummyhash123");
        verify(documentRepository)
                .saveAndFlush(any(Document.class));

        // Vì move thành công nên không xóa temp
        verify(fileStorageService, never())
                .deleteTempFileQuietly(any());
    }

    @Test
    void uploadOriginalDocument_DuplicateFastCheck_ThrowsException() throws IOException {

        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "%PDF-1.4 mock pdf content".getBytes()
        );

        Path tempPath = tempDir.toPath().resolve("temp_file");

        SinglePassStorageResult storageResult =
                new SinglePassStorageResult(
                        "dummyhash123",
                        100L,
                        tempPath
                );

        when(uploadCoordinator.coordinate(file))
                .thenReturn(storageResult);

        doThrow(new BusinessException(ErrorCode.ERR_DUPLICATE_DOCUMENT))
                .when(deduplicationManager).fastCheckDuplicate(eq("dummyhash123"), eq(deptId));

        // Act
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> documentService.uploadOriginalDocument(
                        "Test Doc",
                        file,
                        employeeUser
                )
        );

        // Assert
        assertEquals(ErrorCode.ERR_DUPLICATE_DOCUMENT, ex.getErrorCode());

        verify(uploadCoordinator).coordinate(file);

        verify(deduplicationManager).fastCheckDuplicate(
                eq("dummyhash123"),
                eq(deptId)
        );

        // Fast check fail nên không vào transaction
        verify(deduplicationManager, never()).tryAcquireLock(any(), any());
        verify(deduplicationManager, never()).doubleCheckDuplicate(any(), any());

        verify(documentRepository, never()).save(any());
        verify(documentRepository, never()).saveAndFlush(any());

        // File tạm vẫn phải được dọn
        verify(fileStorageService).deleteTempFileQuietly(tempPath);
    }

    @Test
    void uploadOriginalDocument_ValidationFailure_ThrowsException() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "dummy content".getBytes()
        );
        when(uploadCoordinator.coordinate(any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng file không được hỗ trợ."));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentService.uploadOriginalDocument("Test Doc", file, employeeUser)
        );
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void createAlias_Success() {
        UUID origId = new UUID(12345L, 2L);

        Document original = Document.builder()
                .id(origId)
                .businessCode("ORIG_00000001")
                .title("Test Original")
                .ownerDepartmentId(deptId)
                .build();

        UUID targetDeptId = UUID.randomUUID();

        CreateAliasRequest request = new CreateAliasRequest(origId, targetDeptId);

        when(documentRepository.findByIdForUpdate(origId)).thenReturn(Optional.of(original));
        when(documentRepository.existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(origId, targetDeptId)).thenReturn(false);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(new Department(deptId, "DEV", "Development")));
        when(departmentRepository.existsById(targetDeptId)).thenReturn(true);
        when(departmentRepository.findById(targetDeptId)).thenReturn(Optional.of(new Department(targetDeptId, "HR", "Human Resources")));
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse alias = documentService.createAlias(request, managerUser);

        assertNotNull(alias);
        assertTrue(alias.isAlias());
        assertEquals(origId, alias.getParentId());
        assertEquals(targetDeptId, alias.getOwnerDepartmentId());
        assertEquals(original.getOwnerDepartmentId(), alias.getCreatorDepartmentId());
        assertTrue(alias.getBusinessCode().startsWith("ALIA_"));
        assertNull(alias.getFileSize());
        assertNull(alias.getHash());
    }

    @Test
    void createAlias_BOARDProtection_ThrowsException() {
        UUID origId = new UUID(12345L, 2L);
        managerUser.setDepartmentId(boardDeptId);

        Document original = Document.builder()
                .id(origId)
                .ownerDepartmentId(boardDeptId)
                .title("Original")
                .build();

        CreateAliasRequest request = new CreateAliasRequest(origId, deptId);

        when(documentRepository.findByIdForUpdate(origId)).thenReturn(Optional.of(original));
        when(departmentRepository.findById(boardDeptId)).thenReturn(Optional.of(new Department(boardDeptId, "BOARD", "Board")));
        when(departmentRepository.existsById(deptId)).thenReturn(true);
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(new Department(deptId, "DEV", "Development")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentService.createAlias(request, managerUser)
        );

        assertEquals(ErrorCode.ERR_BOARD_PROTECTION, ex.getErrorCode());
    }

    @Test
    void createAlias_TargetIsBOARD_ThrowsException() {
        UUID origId = new UUID(12345L, 2L);

        Document original = Document.builder()
                .id(origId)
                .ownerDepartmentId(deptId)
                .title("Original")
                .build();

        CreateAliasRequest request = new CreateAliasRequest(origId, boardDeptId);

        when(documentRepository.findByIdForUpdate(origId)).thenReturn(Optional.of(original));
        when(departmentRepository.existsById(boardDeptId)).thenReturn(true);
        when(departmentRepository.findById(boardDeptId)).thenReturn(Optional.of(new Department(boardDeptId, "BOARD", "Board")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentService.createAlias(request, managerUser)
        );

        assertEquals(ErrorCode.ERR_BOARD_PROTECTION, ex.getErrorCode());
    }

    @Test
    void createAlias_AntiChaining_ThrowsException() {
        UUID aliasId = new UUID(12345L, 3L); // LSB = 1
        Document alias = Document.builder()
                .id(aliasId)
                .parentId(UUID.randomUUID())
                .ownerDepartmentId(deptId)
                .build();

        CreateAliasRequest request = new CreateAliasRequest(aliasId, UUID.randomUUID());
        when(documentRepository.findByIdForUpdate(aliasId)).thenReturn(Optional.of(alias));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentService.createAlias(request, managerUser)
        );
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void deleteOriginalDocument_Success() {
        UUID origId = new UUID(12345L, 2L);
        Document original = Document.builder()
                .id(origId)
                .ownerDepartmentId(deptId)
                .build();

        when(documentRepository.findByIdForUpdate(origId)).thenReturn(Optional.of(original));

        documentService.deleteOriginalDocument(origId, managerUser);

        assertNotNull(original.getDeletedAt());
        verify(documentRepository, times(1)).softDeleteAliasesByOriginalId(eq(origId), any(LocalDateTime.class));
    }

    @Test
    void uploadOriginalDocument_ReusePhysicalFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "%PDF-1.4 mock content".getBytes()
        );
        Path tempPath = tempDir.toPath().resolve("temp_file");
        SinglePassStorageResult storageResult = new SinglePassStorageResult("hash_reuse", 100L, tempPath);

        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(uploadCoordinator.coordinate(any())).thenReturn(storageResult);
        when(deduplicationManager.doubleCheckDuplicate(eq("hash_reuse"), eq(deptId)))
                .thenReturn(new DeduplicationQueryResult(false, null, "/storage/old_file"));
        when(deduplicationManager.tryAcquireLock(deptId, "hash_reuse")).thenReturn(true);
        when(fileStorageService.exists("/storage/old_file")).thenReturn(true);
        when(businessCodeAllocator.allocate()).thenReturn("ORIG_00000002");
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = documentService.uploadOriginalDocument("Test Reuse", file, employeeUser);

        assertNotNull(response);
        verify(fileStorageService, never()).moveTempToPermanent(any(), any());
        verify(fileStorageService).deleteTempFileQuietly(tempPath);
    }

    @Test
    void uploadOriginalDocument_PhysicalFileMissing_UploadsNewFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "%PDF-1.4 mock content".getBytes()
        );
        Path tempPath = tempDir.toPath().resolve("temp_file");
        SinglePassStorageResult storageResult = new SinglePassStorageResult("hash_missing", 100L, tempPath);

        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(uploadCoordinator.coordinate(any())).thenReturn(storageResult);
        when(deduplicationManager.doubleCheckDuplicate(eq("hash_missing"), eq(deptId)))
                .thenReturn(new DeduplicationQueryResult(false, null, "/storage/old_file"));
        when(deduplicationManager.tryAcquireLock(deptId, "hash_missing")).thenReturn(true);
        when(fileStorageService.exists("/storage/old_file")).thenReturn(false);
        when(fileStorageService.moveTempToPermanent(tempPath, "hash_missing")).thenReturn("/storage/new_file");
        when(businessCodeAllocator.allocate()).thenReturn("ORIG_00000003");
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = documentService.uploadOriginalDocument("Test Missing", file, employeeUser);

        assertNotNull(response);
        verify(fileStorageService).moveTempToPermanent(tempPath, "hash_missing");
    }

    @Test
    void uploadOriginalDocument_LockBusyRetryTimeout_ThrowsException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "%PDF-1.4 mock content".getBytes()
        );
        Path tempPath = tempDir.toPath().resolve("temp_file");
        SinglePassStorageResult storageResult = new SinglePassStorageResult("hash_busy", 100L, tempPath);

        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(uploadCoordinator.coordinate(any())).thenReturn(storageResult);
        when(deduplicationManager.tryAcquireLock(deptId, "hash_busy")).thenReturn(false);
        when(deduplicationManager.doubleCheckDuplicate(eq("hash_busy"), eq(deptId)))
                .thenReturn(new DeduplicationQueryResult(false, null, null));
        when(fileStorageService.moveTempToPermanent(tempPath, "hash_busy")).thenReturn("/storage/hash_busy");

        assertThrows(ConcurrentUploadTimeoutException.class, () ->
                documentService.uploadOriginalDocument("Test Busy", file, employeeUser)
        );

        verify(deduplicationManager, times(5)).tryAcquireLock(deptId, "hash_busy");
        verify(fileStorageService).moveTempToPermanent(tempPath, "hash_busy");
    }

    @Test
    void uploadOriginalDocument_UniqueConstraintViolation_ThrowsDuplicateDocumentException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "%PDF-1.4 mock content".getBytes()
        );
        Path tempPath = tempDir.toPath().resolve("temp_file");
        SinglePassStorageResult storageResult = new SinglePassStorageResult("hash_violation", 100L, tempPath);

        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(uploadCoordinator.coordinate(any())).thenReturn(storageResult);
        when(deduplicationManager.doubleCheckDuplicate(eq("hash_violation"), eq(deptId)))
                .thenReturn(new DeduplicationQueryResult(false, null, null));
        when(deduplicationManager.tryAcquireLock(deptId, "hash_violation")).thenReturn(true);
        when(fileStorageService.moveTempToPermanent(tempPath, "hash_violation")).thenReturn("/storage/new_file");
        when(businessCodeAllocator.allocate()).thenReturn("ORIG_00000004");
        
        when(documentRepository.saveAndFlush(any(Document.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key violation"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentService.uploadOriginalDocument("Test Violation", file, employeeUser)
        );

        assertEquals(ErrorCode.ERR_DUPLICATE_DOCUMENT, ex.getErrorCode());
        assertEquals("Tài liệu đã tồn tại trong phòng ban.", ex.getMessage());
        verify(fileStorageService, never()).deleteTempFileQuietly(tempPath);
    }
}
