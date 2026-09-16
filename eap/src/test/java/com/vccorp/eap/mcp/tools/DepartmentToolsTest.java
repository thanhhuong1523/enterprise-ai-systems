package com.vccorp.eap.mcp.tools;

import com.vccorp.eap.dto.department.CreateDepartmentRequest;
import com.vccorp.eap.dto.department.DepartmentResponse;
import com.vccorp.eap.service.department.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentToolsTest {

    @Mock
    private DepartmentService departmentService;

    private DepartmentTools departmentTools;

    @BeforeEach
    void setUp() {
        departmentTools = new DepartmentTools(departmentService);
    }

    @Test
    void testListDepartments_DelegatesToService() {
        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "HR", "Nhân sự").build();
        when(departmentService.listDepartments()).thenReturn(List.of(dept));

        List<DepartmentResponse> result = departmentTools.listDepartments();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("HR", result.get(0).code());
        assertEquals("Nhân sự", result.get(0).name());
        verify(departmentService, times(1)).listDepartments();
    }

    @Test
    void testCreateDepartment_DelegatesToService() {
        UUID id = UUID.randomUUID();
        DepartmentResponse response = DepartmentResponse.builder(id, "KETOAN", "Kế toán")
                .description("Phòng nghiệp vụ")
                .build();

        when(departmentService.createDepartment(any(CreateDepartmentRequest.class))).thenReturn(response);

        DepartmentResponse result = departmentTools.createDepartment("KETOAN", "Kế toán", "Phòng nghiệp vụ");

        assertNotNull(result);
        assertEquals("KETOAN", result.code());
        assertEquals("Kế toán", result.name());

        ArgumentCaptor<CreateDepartmentRequest> captor = ArgumentCaptor.forClass(CreateDepartmentRequest.class);
        verify(departmentService, times(1)).createDepartment(captor.capture());

        CreateDepartmentRequest captured = captor.getValue();
        assertEquals("KETOAN", captured.code());
        assertEquals("Kế toán", captured.name());
        assertEquals("Phòng nghiệp vụ", captured.description());
    }

    @Test
    void testGetDepartmentByName_DelegatesToService() {
        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "RND", "Phát triển").build();
        when(departmentService.getDepartmentByName("Phát triển")).thenReturn(Optional.of(dept));

        Optional<DepartmentResponse> result = departmentTools.getDepartmentByName("Phát triển");

        assertTrue(result.isPresent());
        assertEquals("RND", result.get().code());
        assertEquals("Phát triển", result.get().name());
        verify(departmentService, times(1)).getDepartmentByName("Phát triển");
    }

    @Test
    void testToolAnnotations_PresentOnMethods() throws NoSuchMethodException {
        Method listMethod = DepartmentTools.class.getMethod("listDepartments");
        assertTrue(listMethod.isAnnotationPresent(McpTool.class));
        assertEquals("listDepartments", listMethod.getAnnotation(McpTool.class).name());

        Method createMethod = DepartmentTools.class.getMethod("createDepartment", String.class, String.class, String.class);
        assertTrue(createMethod.isAnnotationPresent(McpTool.class));
        assertEquals("createDepartment", createMethod.getAnnotation(McpTool.class).name());

        Method getMethod = DepartmentTools.class.getMethod("getDepartmentByName", String.class);
        assertTrue(getMethod.isAnnotationPresent(McpTool.class));
        assertEquals("getDepartmentByName", getMethod.getAnnotation(McpTool.class).name());

        Method updateMethod = DepartmentTools.class.getMethod("updateDepartment", UUID.class, String.class, String.class, String.class);
        assertTrue(updateMethod.isAnnotationPresent(McpTool.class));
        assertEquals("updateDepartment", updateMethod.getAnnotation(McpTool.class).name());

        Method deleteMethod = DepartmentTools.class.getMethod("deleteDepartment", UUID.class);
        assertTrue(deleteMethod.isAnnotationPresent(McpTool.class));
        assertEquals("deleteDepartment", deleteMethod.getAnnotation(McpTool.class).name());
    }

    @Test
    void testUpdateDepartment_DelegatesToService() {
        UUID id = UUID.randomUUID();
        DepartmentResponse response = DepartmentResponse.builder(id, "RND_NEW", "Phát triển mới").build();
        when(departmentService.updateDepartment(eq(id), any())).thenReturn(response);

        DepartmentResponse result = departmentTools.updateDepartment(id, "RND_NEW", "Phát triển mới", "Mô tả mới");

        assertNotNull(result);
        assertEquals("RND_NEW", result.code());
        assertEquals("Phát triển mới", result.name());
        verify(departmentService, times(1)).updateDepartment(eq(id), any());
    }

    @Test
    void testDeleteDepartment_DelegatesToService() {
        UUID id = UUID.randomUUID();
        doNothing().when(departmentService).deleteDepartment(id);

        String result = departmentTools.deleteDepartment(id);

        assertNotNull(result);
        assertTrue(result.contains("thành công"));
        verify(departmentService, times(1)).deleteDepartment(id);
    }
}
