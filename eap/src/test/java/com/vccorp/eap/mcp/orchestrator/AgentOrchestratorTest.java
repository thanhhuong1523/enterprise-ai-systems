package com.vccorp.eap.mcp.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.department.DepartmentResponse;
import com.vccorp.eap.dto.document.ChunkResultDto;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.mcp.resilience.ResilienceEngineFacade;
import com.vccorp.eap.mcp.tools.DepartmentTools;
import com.vccorp.eap.mcp.tools.DocumentTools;
import com.vccorp.eap.mcp.validator.FailFastValidator;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.helper.LlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private DepartmentTools departmentTools;

    @Mock
    private DocumentTools documentTools;

    @Mock
    private ResilienceEngineFacade resilienceEngine;

    @Mock
    private FailFastValidator failFastValidator;

    @Mock
    private ToolLoopGuard toolLoopGuard;

    @Mock
    private AsyncTaskExecutor mcpTaskExecutor;

    @Mock
    private com.vccorp.eap.mcp.registry.ToolCatalogRegistry toolCatalogRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentOrchestratorImpl orchestrator;

    private User adminUser;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestratorImpl(
                llmClient,
                departmentTools,
                documentTools,
                resilienceEngine,
                failFastValidator,
                toolLoopGuard,
                objectMapper,
                mcpTaskExecutor,
                toolCatalogRegistry
        );

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.SYSTEM_ADMIN)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null,
                        List.of(new SimpleGrantedAuthority(adminUser.getRole().name())))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testExecuteStream_ListDepartmentsFlow_Success() {
        SseEmitter emitter = mock(SseEmitter.class);

        // Turn 1: LLM calls listDepartments
        when(llmClient.callLlmText(anyString(), anyString(), anyLong()))
                .thenReturn("{\"action\": \"listDepartments\", \"action_input\": {}}")
                .thenReturn("{\"action\": \"FINAL_ANSWER\", \"final_answer\": \"Các phòng ban gồm: HR, RND\"}");

        Map<String, Object> turn1Map = Map.of("action", "listDepartments", "action_input", Map.of());
        Map<String, Object> turn2Map = Map.of("action", "FINAL_ANSWER", "final_answer", "Các phòng ban gồm: HR, RND");

        when(resilienceEngine.parseAndRecover(anyString()))
                .thenReturn(turn1Map)
                .thenReturn(turn2Map);

        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "HR", "Nhân sự").build();
        when(departmentTools.listDepartments()).thenReturn(List.of(dept));

        assertDoesNotThrow(() -> orchestrator.executeStream("Cho tôi xem danh sách phòng ban", emitter));

        verify(departmentTools, times(1)).listDepartments();
        verify(failFastValidator, times(1)).validateToolExecutionResult(eq("listDepartments"), any(), any());
        verify(emitter, times(1)).complete();
    }

    @Test
    void testExecuteStream_CreateDepartmentFlow_Success() {
        SseEmitter emitter = mock(SseEmitter.class);

        // Turn 1: LLM calls createDepartment
        Map<String, Object> input = Map.of("code", "KETOAN", "name", "Kế toán", "description", "Mô tả");
        Map<String, Object> turn1Map = Map.of("action", "createDepartment", "action_input", input);
        Map<String, Object> turn2Map = Map.of("action", "FINAL_ANSWER", "final_answer", "Đã tạo thành công");

        when(llmClient.callLlmText(anyString(), anyString(), anyLong()))
                .thenReturn("raw1")
                .thenReturn("raw2");

        when(resilienceEngine.parseAndRecover(anyString()))
                .thenReturn(turn1Map)
                .thenReturn(turn2Map);

        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "KETOAN", "Kế toán").build();
        when(departmentTools.createDepartment("KETOAN", "Kế toán", "Mô tả")).thenReturn(dept);

        assertDoesNotThrow(() -> orchestrator.executeStream("Tạo phòng Kế toán mã KETOAN", emitter));

        verify(departmentTools, times(1)).createDepartment("KETOAN", "Kế toán", "Mô tả");
        verify(emitter, times(1)).complete();
    }

    @Test
    void testExecuteStream_SearchDocumentsFlow_Success() {
        SseEmitter emitter = mock(SseEmitter.class);

        Map<String, Object> input = Map.of("query", "quy định nghỉ phép");
        Map<String, Object> turn1Map = Map.of("action", "searchDocuments", "action_input", input);
        Map<String, Object> turn2Map = Map.of("action", "FINAL_ANSWER", "final_answer", "Quy định nghỉ phép là 12 ngày/năm.");

        when(llmClient.callLlmText(anyString(), anyString(), anyLong()))
                .thenReturn("raw1")
                .thenReturn("raw2");

        when(resilienceEngine.parseAndRecover(anyString()))
                .thenReturn(turn1Map)
                .thenReturn(turn2Map);

        ChunkResultDto mockChunk = new ChunkResultDto("Quy định nghỉ phép 12 ngày", 0.95, "Quy chế lao động", "QC-01", Map.of(), 1, "[Quy chế lao động, Trang 1]");
        RagChatResponse ragResponse = new RagChatResponse("Quy định nghỉ phép là 12 ngày/năm.", List.of(mockChunk));
        when(documentTools.searchDocuments("quy định nghỉ phép")).thenReturn(ragResponse);

        assertDoesNotThrow(() -> orchestrator.executeStream("Hỏi về quy định nghỉ phép", emitter));

        verify(documentTools, times(1)).searchDocuments("quy định nghỉ phép");
        verify(emitter, times(1)).complete();
    }

    @Test
    void testExecuteStream_FailFastWhenDepartmentNotFound() {
        SseEmitter emitter = mock(SseEmitter.class);

        Map<String, Object> input = Map.of("name", "Kinh doanh");
        Map<String, Object> turn1Map = Map.of("action", "getDepartmentByName", "action_input", input);

        when(llmClient.callLlmText(anyString(), anyString(), anyLong())).thenReturn("raw");
        when(resilienceEngine.parseAndRecover(anyString())).thenReturn(turn1Map);
        when(departmentTools.getDepartmentByName("Kinh doanh")).thenReturn(Optional.empty());

        doThrow(new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND, "Không tìm thấy phòng ban"))
                .when(failFastValidator).validateToolExecutionResult(eq("getDepartmentByName"), any(), any());

        assertDoesNotThrow(() -> orchestrator.executeStream("Tìm phòng Kinh doanh", emitter));

        verify(emitter, times(1)).complete();
    }

    @Test
    void testStreamChat_DispatchesToTaskExecutor() {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mcpTaskExecutor).execute(any(Runnable.class));

        when(llmClient.callLlmText(anyString(), anyString(), anyLong()))
                .thenReturn("{\"action\": \"FINISH\", \"action_input\": {\"message\": \"Xin chào\"}}");
        when(resilienceEngine.parseAndRecover(anyString()))
                .thenReturn(Map.of("action", "FINISH", "action_input", Map.of("message", "Xin chào")));

        SseEmitter emitter = orchestrator.streamChat("Chào bạn");

        assertNotNull(emitter);
        verify(mcpTaskExecutor, times(1)).execute(any(Runnable.class));
    }
}
