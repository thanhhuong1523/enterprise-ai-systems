package com.vccorp.eap.mcp.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolCatalogRegistryTest {

    @Mock
    private ToolCallbackProvider toolCallbackProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ToolCatalogRegistry registry;

    @BeforeEach
    void setUp() {
        FunctionCallback mockCallback1 = mock(FunctionCallback.class);
        when(mockCallback1.getName()).thenReturn("listDepartments");
        when(mockCallback1.getDescription()).thenReturn("Lấy toàn bộ danh sách các phòng ban.");
        when(mockCallback1.getInputTypeSchema()).thenReturn("{\"type\":\"object\",\"properties\":{},\"required\":[]}");

        FunctionCallback mockCallback2 = mock(FunctionCallback.class);
        when(mockCallback2.getName()).thenReturn("createDepartment");
        when(mockCallback2.getDescription()).thenReturn("Đăng ký một phòng ban mới.");
        when(mockCallback2.getInputTypeSchema()).thenReturn("{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\",\"description\":\"Mã phòng ban\"},\"name\":{\"type\":\"string\",\"description\":\"Tên phòng ban\"},\"description\":{\"type\":\"string\",\"description\":\"Mô tả phòng ban\"}},\"required\":[\"code\",\"name\"]}");

        when(toolCallbackProvider.getToolCallbacks()).thenReturn(new FunctionCallback[]{mockCallback1, mockCallback2});

        com.vccorp.eap.service.department.DepartmentService departmentService = mock(com.vccorp.eap.service.department.DepartmentService.class);
        com.vccorp.eap.mcp.tools.DepartmentTools departmentTools = new com.vccorp.eap.mcp.tools.DepartmentTools(departmentService);

        registry = new ToolCatalogRegistry(toolCallbackProvider, java.util.List.of(departmentTools), objectMapper);
        registry.init();
    }

    @Test
    void testFindByName_FoundAndMergedWithCatalogLabels() {
        Optional<ToolInfo> listDept = registry.findByName("listDepartments");
        assertTrue(listDept.isPresent());
        assertEquals("listDepartments", listDept.get().name());
        assertEquals("Đang tra cứu danh sách các phòng ban...", listDept.get().startLabel());
        assertEquals("Đã lấy danh sách phòng ban thành công", listDept.get().endLabel());

        Optional<ToolInfo> createDept = registry.findByName("createDepartment");
        assertTrue(createDept.isPresent());
        assertEquals("createDepartment", createDept.get().name());
        assertEquals("Đang đăng ký phòng ban '{{name}}'...", createDept.get().startLabel());
        assertEquals("Đã đăng ký phòng ban '{{name}}' thành công", createDept.get().endLabel());
    }

    @Test
    void testRenderLabel_StaticString() {
        String rendered = registry.renderLabel("Đang tra cứu danh sách các phòng ban...", Map.of());
        assertEquals("Đang tra cứu danh sách các phòng ban...", rendered);
    }

    @Test
    void testRenderLabel_DynamicTemplate() {
        String template = "Đang đăng ký phòng ban '{{name}}'...";
        String rendered = registry.renderLabel(template, Map.of("name", "Kế toán", "code", "KT"));
        assertEquals("Đang đăng ký phòng ban 'Kế toán'...", rendered);
    }

    @Test
    void testBuildToolListForPrompt_ContainsRequiredAndOptionalTags() {
        String prompt = registry.buildToolListForPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.contains("listDepartments"));
        assertTrue(prompt.contains("createDepartment"));
        assertTrue(prompt.contains("(bắt buộc)"));
        assertTrue(prompt.contains("(tùy chọn)"));
    }
}
