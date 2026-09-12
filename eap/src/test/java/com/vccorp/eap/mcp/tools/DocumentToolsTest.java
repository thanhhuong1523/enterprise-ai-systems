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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentToolsTest {

    @Mock
    private RetrievalService retrievalService;

    private DocumentTools documentTools;
    private User testUser;

    @BeforeEach
    void setUp() {
        documentTools = new DocumentTools(retrievalService);
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
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
}
