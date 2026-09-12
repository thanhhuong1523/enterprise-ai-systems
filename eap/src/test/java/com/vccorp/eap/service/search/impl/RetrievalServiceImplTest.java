package com.vccorp.eap.service.search.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.ChunkRepository;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.service.document.LlmMetadataExtractorService;
import com.vccorp.eap.service.embedding.EmbeddingService;
import com.vccorp.eap.service.helper.LlmClient;
import com.vccorp.eap.service.search.RagAnswerGeneratorService;
import com.vccorp.eap.service.search.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class RetrievalServiceImplTest {

    @Mock
    private LlmMetadataExtractorService llmMetadataExtractorService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private TaskExecutor taskExecutor;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private RagAnswerGeneratorService ragAnswerGeneratorService;

    @Mock
    private LlmClient llmClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RetrievalServiceImpl retrievalService;

    @Test
    void search_NullCurrentUser_ThrowsUnauthenticated() {
        RagChatRequest request = new RagChatRequest("Hello");
        BusinessException ex = assertThrows(BusinessException.class, () -> retrievalService.search(request, null));
        assertEquals(ErrorCode.ERR_UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void search_AdminUser_ThrowsForbiddenRole() {
        RagChatRequest request = new RagChatRequest("Hello");
        User adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.SYSTEM_ADMIN)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> retrievalService.search(request, adminUser));
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
        assertEquals("Quản trị viên hệ thống không được phép tìm kiếm tài liệu.", ex.getMessage());
    }

    @Test
    void search_BlankMessage_ThrowsInvalidRequest() {
        RagChatRequest request = new RagChatRequest("   ");
        User employee = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> retrievalService.search(request, employee));
        assertEquals(ErrorCode.ERR_INVALID_REQUEST, ex.getErrorCode());
    }
}
