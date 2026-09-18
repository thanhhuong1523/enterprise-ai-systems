package com.vccorp.eap.mcp.tools;

import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.search.RetrievalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentToolsTest {

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private com.vccorp.eap.service.document.DocumentService documentService;

    private DocumentTools documentTools;
    private User testUser;
    private UUID deptId;

    @BeforeEach
    void setUp() {
        documentTools = new DocumentTools(retrievalService, documentService);
        deptId = UUID.randomUUID();
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
                .departmentId(deptId)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser, null,
                        List.of(new SimpleGrantedAuthority(testUser.getRole().name())))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testSearchDocuments_DelegatesToRetrievalService() {
        String query = "Quy định nghỉ phép";
        RagChatResponse mockResponse = new RagChatResponse("Nội dung quy định", Collections.emptyList());

        when(retrievalService.search(any(RagChatRequest.class), eq(testUser))).thenReturn(mockResponse);

        RagChatResponse result = documentTools.searchDocuments(query);

        assertNotNull(result);
        assertEquals("Nội dung quy định", result.response());
        verify(retrievalService, times(1)).search(argThat(req -> req.message().equals(query)), eq(testUser));
    }

    @Test
    void testGetDocumentByTitle_DelegatesToDocumentService() {
        String title = "Quy chế lương";
        com.vccorp.eap.dto.document.DocumentResponse doc = com.vccorp.eap.dto.document.DocumentResponse.builder(
                UUID.randomUUID(), "DOC-001", title, deptId, java.time.LocalDateTime.now()
        ).fileSize(1024L).build();
        when(documentService.getDocumentByTitle(title, testUser)).thenReturn(doc);

        Object result = documentTools.getDocumentByTitle(title);

        assertNotNull(result);
        assertTrue(result instanceof com.vccorp.eap.dto.document.DocumentResponse);
        com.vccorp.eap.dto.document.DocumentResponse docResp = (com.vccorp.eap.dto.document.DocumentResponse) result;
        assertEquals(title, docResp.getTitle());
        verify(documentService, times(1)).getDocumentByTitle(title, testUser);
    }

    @Test
    void testGetDocumentByTitle_BlankTitle_ReturnsValidationMessage() {
        Object result = documentTools.getDocumentByTitle("   ");
        assertEquals("Tiêu đề tài liệu không được để trống.", result);
    }

    @Test
    void testGetDocumentByTitle_NotFound_ReturnsDepartmentScopedMessage() {
        when(documentService.getDocumentByTitle("Không Có", testUser)).thenThrow(
                new com.vccorp.eap.common.exception.BusinessException(com.vccorp.eap.common.error.ErrorCode.ERR_DOCUMENT_NOT_FOUND)
        );

        Object result = documentTools.getDocumentByTitle("Không Có");
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("Không tìm thấy tài liệu"));
    }

    @Test
    void testGetDocumentByTitle_Admin_ReturnsForbiddenMessage() {
        User adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.SYSTEM_ADMIN)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null,
                        List.of(new SimpleGrantedAuthority(adminUser.getRole().name())))
        );

        Object result = documentTools.getDocumentByTitle("Quy chế");
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("Quản trị viên không có quyền truy cập"));
    }

    @Test
    void testListOriginalDocuments_DelegatesToDocumentService() {
        com.vccorp.eap.dto.document.DocumentResponse doc = com.vccorp.eap.dto.document.DocumentResponse.builder(
                UUID.randomUUID(), "DOC-001", "Doc 1", deptId, java.time.LocalDateTime.now()
        ).fileSize(1024L).build();
        org.springframework.data.domain.Page<com.vccorp.eap.dto.document.DocumentResponse> page = new org.springframework.data.domain.PageImpl<>(List.of(doc));
        when(documentService.listOriginalDocuments(0, 10, testUser)).thenReturn(page);

        List<com.vccorp.eap.dto.document.DocumentResponse> result = documentTools.listOriginalDocuments(0, 10);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(documentService, times(1)).listOriginalDocuments(0, 10, testUser);
    }

    @Test
    void testUpdateOriginalDocument_DelegatesToDocumentService() {
        User managerUser = User.builder()
                .id(UUID.randomUUID())
                .username("manager")
                .role(Role.ROLE_DEPT_MANAGER)
                .departmentId(deptId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(managerUser, null,
                        List.of(new SimpleGrantedAuthority(managerUser.getRole().name())))
        );

        UUID id = UUID.randomUUID();
        String newTitle = "Quy chế mới";
        com.vccorp.eap.dto.document.DocumentResponse doc = com.vccorp.eap.dto.document.DocumentResponse.builder(
                id, "DOC-001", newTitle, deptId, java.time.LocalDateTime.now()
        ).fileSize(1024L).build();
        when(documentService.updateOriginalDocument(id, newTitle, managerUser)).thenReturn(doc);

        Object result = documentTools.updateOriginalDocument(id, newTitle);

        assertNotNull(result);
        assertTrue(result instanceof com.vccorp.eap.dto.document.DocumentResponse);
        com.vccorp.eap.dto.document.DocumentResponse docResp = (com.vccorp.eap.dto.document.DocumentResponse) result;
        assertEquals(newTitle, docResp.getTitle());
        verify(documentService, times(1)).updateOriginalDocument(id, newTitle, managerUser);
    }

    @Test
    void testUpdateOriginalDocument_EmployeeRole_ReturnsForbiddenMessage() {
        UUID id = UUID.randomUUID();
        Object result = documentTools.updateOriginalDocument(id, "Tiêu đề mới");
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("không có quyền cập nhật tài liệu"));
    }

    @Test
    void testDeleteOriginalDocument_DelegatesToDocumentService() {
        User managerUser = User.builder()
                .id(UUID.randomUUID())
                .username("manager")
                .role(Role.ROLE_DEPT_MANAGER)
                .departmentId(deptId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(managerUser, null,
                        List.of(new SimpleGrantedAuthority(managerUser.getRole().name())))
        );

        UUID id = UUID.randomUUID();
        doNothing().when(documentService).deleteOriginalDocument(id, managerUser);

        Object result = documentTools.deleteOriginalDocument(id);

        assertNotNull(result);
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("thành công"));
        verify(documentService, times(1)).deleteOriginalDocument(id, managerUser);
    }

    @Test
    void testDeleteOriginalDocument_EmployeeRole_ReturnsForbiddenMessage() {
        UUID id = UUID.randomUUID();
        Object result = documentTools.deleteOriginalDocument(id);
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("không có quyền xóa tài liệu"));
    }

    @Test
    void testCreateDocumentAlias_DelegatesToDocumentService() {
        UUID originalDocId = UUID.randomUUID();
        UUID targetDeptId = UUID.randomUUID();
        com.vccorp.eap.dto.document.DocumentResponse aliasDoc = com.vccorp.eap.dto.document.DocumentResponse.builder(
                UUID.randomUUID(), "ALIAS-001", "Alias Doc", targetDeptId, java.time.LocalDateTime.now()
        ).parentId(originalDocId).build();
        when(documentService.createAlias(any(com.vccorp.eap.dto.document.CreateAliasRequest.class), eq(testUser))).thenReturn(aliasDoc);

        com.vccorp.eap.dto.document.DocumentResponse result = documentTools.createDocumentAlias(originalDocId, targetDeptId);

        assertNotNull(result);
        assertEquals("Alias Doc", result.getTitle());
        verify(documentService, times(1)).createAlias(any(com.vccorp.eap.dto.document.CreateAliasRequest.class), eq(testUser));
    }

    @Test
    void testListSharedDocuments_DelegatesToDocumentService() {
        com.vccorp.eap.dto.document.DocumentResponse aliasDoc = com.vccorp.eap.dto.document.DocumentResponse.builder(
                UUID.randomUUID(), "ALIAS-001", "Shared Doc", deptId, java.time.LocalDateTime.now()
        ).build();
        org.springframework.data.domain.Page<com.vccorp.eap.dto.document.DocumentResponse> page = new org.springframework.data.domain.PageImpl<>(List.of(aliasDoc));
        when(documentService.listSharedDocuments(0, 10, testUser)).thenReturn(page);

        List<com.vccorp.eap.dto.document.DocumentResponse> result = documentTools.listSharedDocuments(0, 10);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(documentService, times(1)).listSharedDocuments(0, 10, testUser);
    }

    @Test
    void testListDocumentAliases_DelegatesToDocumentService() {
        UUID originalDocId = UUID.randomUUID();
        com.vccorp.eap.dto.document.DocumentResponse aliasDoc = com.vccorp.eap.dto.document.DocumentResponse.builder(
                UUID.randomUUID(), "ALIAS-001", "Alias Doc", deptId, java.time.LocalDateTime.now()
        ).parentId(originalDocId).build();
        when(documentService.listDocumentAliases(originalDocId, testUser)).thenReturn(List.of(aliasDoc));

        List<com.vccorp.eap.dto.document.DocumentResponse> result = documentTools.listDocumentAliases(originalDocId);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(documentService, times(1)).listDocumentAliases(originalDocId, testUser);
    }

    @Test
    void testDeleteDocumentAlias_DelegatesToDocumentService() {
        UUID aliasId = UUID.randomUUID();
        doNothing().when(documentService).deleteAlias(aliasId, testUser);

        String result = documentTools.deleteDocumentAlias(aliasId);

        assertNotNull(result);
        assertTrue(result.contains("thành công"));
        verify(documentService, times(1)).deleteAlias(aliasId, testUser);
    }

    @Test
    void testListOriginalDocuments_WhenPageIsNull_ReturnsEmptyList() {
        when(documentService.listOriginalDocuments(0, 10, testUser)).thenReturn(null);

        List<com.vccorp.eap.dto.document.DocumentResponse> result = documentTools.listOriginalDocuments(0, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(documentService, times(1)).listOriginalDocuments(0, 10, testUser);
    }

    @Test
    void testListSharedDocuments_WhenPageIsNull_ReturnsEmptyList() {
        when(documentService.listSharedDocuments(0, 10, testUser)).thenReturn(null);

        List<com.vccorp.eap.dto.document.DocumentResponse> result = documentTools.listSharedDocuments(0, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(documentService, times(1)).listSharedDocuments(0, 10, testUser);
    }

    @Test
    void testListDocumentAliases_WhenServiceReturnsNull_ReturnsEmptyList() {
        UUID originalDocId = UUID.randomUUID();
        when(documentService.listDocumentAliases(originalDocId, testUser)).thenReturn(null);

        List<com.vccorp.eap.dto.document.DocumentResponse> result = documentTools.listDocumentAliases(originalDocId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(documentService, times(1)).listDocumentAliases(originalDocId, testUser);
    }

    @Test
    void testSearchDocuments_WhenServiceReturnsNull_ReturnsFallbackResponse() {
        when(retrievalService.search(any(), eq(testUser))).thenReturn(null);

        RagChatResponse result = documentTools.searchDocuments("test query");

        assertNotNull(result);
        assertNotNull(result.chunks());
        assertTrue(result.chunks().isEmpty());
        assertTrue(result.response().contains("Không tìm thấy"));
    }
}
