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

        com.vccorp.eap.dto.document.DocumentResponse result = documentTools.getDocumentByTitle(title);

        assertNotNull(result);
        assertEquals(title, result.getTitle());
        verify(documentService, times(1)).getDocumentByTitle(title, testUser);
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
        UUID id = UUID.randomUUID();
        String newTitle = "Quy chế mới";
        com.vccorp.eap.dto.document.DocumentResponse doc = com.vccorp.eap.dto.document.DocumentResponse.builder(
                id, "DOC-001", newTitle, deptId, java.time.LocalDateTime.now()
        ).fileSize(1024L).build();
        when(documentService.updateOriginalDocument(id, newTitle, testUser)).thenReturn(doc);

        com.vccorp.eap.dto.document.DocumentResponse result = documentTools.updateOriginalDocument(id, newTitle);

        assertNotNull(result);
        assertEquals(newTitle, result.getTitle());
        verify(documentService, times(1)).updateOriginalDocument(id, newTitle, testUser);
    }

    @Test
    void testDeleteOriginalDocument_DelegatesToDocumentService() {
        UUID id = UUID.randomUUID();
        doNothing().when(documentService).deleteOriginalDocument(id, testUser);

        String result = documentTools.deleteOriginalDocument(id);

        assertNotNull(result);
        assertTrue(result.contains("thành công"));
        verify(documentService, times(1)).deleteOriginalDocument(id, testUser);
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
}
