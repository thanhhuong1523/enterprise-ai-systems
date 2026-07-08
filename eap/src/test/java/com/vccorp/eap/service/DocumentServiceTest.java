package com.vccorp.eap.service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.dto.DocumentResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.vccorp.eap.service.impl.DocumentServiceImpl;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @TempDir
    File tempDir;

    private User employeeUser;
    private User managerUser;
    private UUID deptId;
    private UUID boardDeptId;

    @BeforeEach
    void setUp() {
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

    @Test
    void uploadOriginalDocument_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "%PDF-1.4 mock pdf content".getBytes()
        );
        when(storageService.storeFile(any(), anyString())).thenReturn("mock/file/path");
        when(storageService.loadFile(anyString())).thenReturn("dummy content".getBytes());
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse savedDoc = documentService.uploadOriginalDocument("Test Doc", file, employeeUser);

        assertNotNull(savedDoc);
        assertTrue(savedDoc.isOriginal());
        assertFalse(savedDoc.isAlias());
        assertEquals("Test Doc", savedDoc.getTitle());
        assertEquals(deptId, savedDoc.getOwnerDepartmentId());
        assertTrue(savedDoc.getBusinessCode().startsWith("ORIG_"));
        assertNotNull(savedDoc.getHash());
    }

    @Test
    void uploadOriginalDocument_InvalidExtension_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "dummy content".getBytes()
        );
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
                .businessCode("ORIG_123")
                .title("Test Original")
                .ownerDepartmentId(deptId)
                .build();

        UUID targetDeptId = UUID.randomUUID();

        CreateAliasRequest request =
                new CreateAliasRequest(origId, targetDeptId);

        when(documentRepository.findByIdForUpdate(origId))
                .thenReturn(Optional.of(original));

        when(documentRepository.existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(
                origId,
                targetDeptId))
                .thenReturn(false);

        when(departmentRepository.findById(deptId))
                .thenReturn(Optional.of(
                        new Department(deptId, "DEV", "Development")
                ));

        when(departmentRepository.existsById(targetDeptId)).thenReturn(true);

        when(departmentRepository.findById(targetDeptId))
                .thenReturn(Optional.of(
                        new Department(targetDeptId, "HR", "Human Resources")
                ));

        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse alias =
                documentService.createAlias(request, managerUser);

        assertNotNull(alias);

        assertTrue(alias.isAlias());

        assertEquals(origId, alias.getParentId());

        assertEquals(targetDeptId, alias.getOwnerDepartmentId());

        assertEquals(
                original.getOwnerDepartmentId(),
                alias.getCreatorDepartmentId()
        );

        assertTrue(alias.getBusinessCode().startsWith("ALIA_"));

        assertNull(alias.getFileSize());
        assertNull(alias.getHash());
    }

    @Test
    void createAlias_BOARDProtection_ThrowsException() {
        UUID origId = new UUID(12345L, 2L);   // original (LSB chẵn)

        // manager phải thuộc BOARD
        managerUser.setDepartmentId(boardDeptId);

        Document original = Document.builder()
                .id(origId)
                .ownerDepartmentId(boardDeptId)
                .title("Original")
                .build();

        CreateAliasRequest request =
                new CreateAliasRequest(origId, deptId);

        when(documentRepository.findByIdForUpdate(origId))
                .thenReturn(Optional.of(original));

        when(departmentRepository.findById(boardDeptId))
                .thenReturn(Optional.of(
                        new Department(boardDeptId, "BOARD", "Board")
                ));

        when(departmentRepository.existsById(deptId)).thenReturn(true);

        when(departmentRepository.findById(deptId))
                .thenReturn(Optional.of(
                        new Department(deptId, "DEV", "Development")
                ));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> documentService.createAlias(request, managerUser)
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

        CreateAliasRequest request =
                new CreateAliasRequest(origId, boardDeptId);

        when(documentRepository.findByIdForUpdate(origId))
                .thenReturn(Optional.of(original));

        when(departmentRepository.existsById(boardDeptId)).thenReturn(true);

        when(departmentRepository.findById(boardDeptId))
                .thenReturn(Optional.of(
                        new Department(boardDeptId, "BOARD", "Board")
                ));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> documentService.createAlias(request, managerUser)
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
    void uploadOriginalDocument_DocxSuccess() throws IOException {
        byte[] docxBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes
        );
        when(storageService.storeFile(any(), anyString())).thenReturn("mock/file/path");
        when(storageService.loadFile(anyString())).thenReturn(docxBytes);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse savedDoc = documentService.uploadOriginalDocument("Test Docx", file, employeeUser);

        assertNotNull(savedDoc);
        assertTrue(savedDoc.isOriginal());
        assertEquals("Test Docx", savedDoc.getTitle());
    }

    @Test
    void uploadOriginalDocument_XlsxSuccess() throws IOException {
        byte[] xlsxBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes
        );
        when(storageService.storeFile(any(), anyString())).thenReturn("mock/file/path");
        when(storageService.loadFile(anyString())).thenReturn(xlsxBytes);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse savedDoc = documentService.uploadOriginalDocument("Test Xlsx", file, employeeUser);

        assertNotNull(savedDoc);
        assertTrue(savedDoc.isOriginal());
        assertEquals("Test Xlsx", savedDoc.getTitle());
    }

    @Test
    void uploadOriginalDocument_SpoofedExtension_ThrowsException() throws IOException {
        byte[] maliciousBytes = "MZ\u0000\u0000 mock executable content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", maliciousBytes
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentService.uploadOriginalDocument("Malicious Spoof", file, employeeUser)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Định dạng file thực tế không được hỗ trợ."));
    }
}
