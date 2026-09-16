package com.vccorp.eap.mcp.orchestrator;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.department.DepartmentResponse;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.mcp.tools.DepartmentTools;
import com.vccorp.eap.mcp.tools.DocumentTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolDispatcherTest {

    @Mock
    private DepartmentTools departmentTools;

    @Mock
    private DocumentTools documentTools;

    private ToolDispatcherImpl toolDispatcher;

    @BeforeEach
    void setUp() {
        toolDispatcher = new ToolDispatcherImpl(departmentTools, documentTools);
    }

    @Test
    void testExecuteTool_ListDepartments_Success() {
        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "HR", "Nhân sự").build();
        when(departmentTools.listDepartments()).thenReturn(List.of(dept));

        Object result = toolDispatcher.executeTool("listDepartments", Map.of());

        assertNotNull(result);
        assertTrue(result instanceof List);
        verify(departmentTools, times(1)).listDepartments();
    }

    @Test
    void testExecuteTool_CreateDepartment_Success() {
        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "TECH", "Kỹ thuật").build();
        when(departmentTools.createDepartment("TECH", "Kỹ thuật", "Mô tả")).thenReturn(dept);

        Object result = toolDispatcher.executeTool("createDepartment", Map.of(
                "code", "TECH",
                "name", "Kỹ thuật",
                "description", "Mô tả"
        ));

        assertEquals(dept, result);
        verify(departmentTools, times(1)).createDepartment("TECH", "Kỹ thuật", "Mô tả");
    }

    @Test
    void testExecuteTool_GetDepartmentByName_Success() {
        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "FIN", "Tài chính").build();
        when(departmentTools.getDepartmentByName("Tài chính")).thenReturn(Optional.of(dept));

        Object result = toolDispatcher.executeTool("getDepartmentByName", Map.of("name", "Tài chính"));

        assertEquals(Optional.of(dept), result);
        verify(departmentTools, times(1)).getDepartmentByName("Tài chính");
    }

    @Test
    void testExecuteTool_SearchDocuments_Success() {
        RagChatResponse response = new RagChatResponse("Trả lời tìm kiếm", List.of());
        when(documentTools.searchDocuments("nội quy")).thenReturn(response);

        Object result = toolDispatcher.executeTool("searchDocuments", Map.of("query", "nội quy"));

        assertEquals(response, result);
        verify(documentTools, times(1)).searchDocuments("nội quy");
    }

    @Test
    void testExecuteTool_UnsupportedTool_ThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                toolDispatcher.executeTool("unknownTool", Map.of()));

        assertEquals(ErrorCode.ERR_INVALID_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("unknownTool"));
    }

    @Test
    void testExecuteTool_NullToolName_ThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                toolDispatcher.executeTool(null, Map.of()));

        assertEquals(ErrorCode.ERR_INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void testRegisterNewTool_DynamicExtension_Success() {
        toolDispatcher.register("customTool", args -> "Custom Result: " + args.get("val"));

        Object result = toolDispatcher.executeTool("customTool", Map.of("val", 123));

        assertEquals("Custom Result: 123", result);
    }

    @Test
    void testExecuteTool_MissingRequiredParam_ThrowsBusinessException() {
        // getDepartmentByName yêu cầu tham số bắt buộc 'name'
        BusinessException ex = assertThrows(BusinessException.class, () ->
                toolDispatcher.executeTool("getDepartmentByName", Map.of()));

        assertEquals(ErrorCode.ERR_INVALID_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Thiếu tham số bắt buộc 'name'"));
    }

    @Test
    void testExecuteTool_CustomFacadeAutoDiscoveryAndExecution() {
        class SampleFacade implements com.vccorp.eap.mcp.tools.McpToolFacade {
            @org.springaicommunity.mcp.annotation.McpTool(name = "calculator")
            public int calculate(
                    @org.springaicommunity.mcp.annotation.McpToolParam(description = "Số a") int a,
                    @org.springaicommunity.mcp.annotation.McpToolParam(description = "Số b") int b
            ) {
                return a + b;
            }
        }

        ToolDispatcherImpl autoDispatcher = new ToolDispatcherImpl(
                List.of(new SampleFacade()),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        Object result = autoDispatcher.executeTool("calculator", Map.of("a", 15, "b", 27));
        assertEquals(42, result);
    }
}
