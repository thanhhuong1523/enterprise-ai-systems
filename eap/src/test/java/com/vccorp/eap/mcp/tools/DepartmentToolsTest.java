package com.vccorp.eap.mcp.tools;

import com.vccorp.eap.dto.department.CreateDepartmentRequest;
import com.vccorp.eap.dto.department.DepartmentResponse;
import com.vccorp.eap.service.department.DepartmentService;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private User adminUser;

    @BeforeEach
    void setUp() {
        departmentTools = new DepartmentTools(departmentService);
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

        Object result = departmentTools.createDepartment("KETOAN", "Kế toán", "Phòng nghiệp vụ");

        assertNotNull(result);
        assertTrue(result instanceof DepartmentResponse);
        DepartmentResponse deptResp = (DepartmentResponse) result;
        assertEquals("KETOAN", deptResp.code());
        assertEquals("Kế toán", deptResp.name());

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
    void testGetDepartmentByName_WhenServiceReturnsEmpty_ReturnsEmptyOptional() {
        when(departmentService.getDepartmentByName("Unknown")).thenReturn(Optional.empty());

        Optional<DepartmentResponse> result = departmentTools.getDepartmentByName("Unknown");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(departmentService, times(1)).getDepartmentByName("Unknown");
    }

    @Test
    void testGetDepartmentByName_WhenServiceReturnsNull_ReturnsEmptyOptional() {
        when(departmentService.getDepartmentByName("Unknown")).thenReturn(null);

        Optional<DepartmentResponse> result = departmentTools.getDepartmentByName("Unknown");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(departmentService, times(1)).getDepartmentByName("Unknown");
    }

    @Test
    void testGetDepartmentByName_WhenServiceReturnsOptionalWithNull_ReturnsEmptyOptional() {
        when(departmentService.getDepartmentByName("Unknown")).thenReturn(Optional.ofNullable(null));

        Optional<DepartmentResponse> result = departmentTools.getDepartmentByName("Unknown");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(departmentService, times(1)).getDepartmentByName("Unknown");
    }

    @Test
    void testListDepartments_WhenServiceReturnsNull_ReturnsEmptyList() {
        when(departmentService.listDepartments()).thenReturn(null);

        List<DepartmentResponse> result = departmentTools.listDepartments();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(departmentService, times(1)).listDepartments();
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

        Object result = departmentTools.updateDepartment(id, "RND_NEW", "Phát triển mới", "Mô tả mới");

        assertNotNull(result);
        assertTrue(result instanceof DepartmentResponse);
        DepartmentResponse deptResp = (DepartmentResponse) result;
        assertEquals("RND_NEW", deptResp.code());
        assertEquals("Phát triển mới", deptResp.name());
        verify(departmentService, times(1)).updateDepartment(eq(id), any());
    }

    @Test
    void testDeleteDepartment_DelegatesToService() {
        UUID id = UUID.randomUUID();
        doNothing().when(departmentService).deleteDepartment(id);

        Object result = departmentTools.deleteDepartment(id);

        assertNotNull(result);
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("thành công"));
        verify(departmentService, times(1)).deleteDepartment(id);
    }

    @Test
    void testCreateDepartment_NonAdmin_ReturnsForbiddenMessage() {
        User employee = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employee, null,
                        List.of(new SimpleGrantedAuthority(employee.getRole().name())))
        );

        Object result = departmentTools.createDepartment("TEST", "Thử nghiệm", "Mô tả");
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("không có quyền tạo phòng ban"));
    }

    @Test
    void testUpdateDepartment_NonAdmin_ReturnsForbiddenMessage() {
        User employee = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employee, null,
                        List.of(new SimpleGrantedAuthority(employee.getRole().name())))
        );

        Object result = departmentTools.updateDepartment(UUID.randomUUID(), "TEST", "Thử nghiệm", "Mô tả");
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("không có quyền cập nhật"));
    }

    @Test
    void testDeleteDepartment_NonAdmin_ReturnsForbiddenMessage() {
        User employee = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employee, null,
                        List.of(new SimpleGrantedAuthority(employee.getRole().name())))
        );

        Object result = departmentTools.deleteDepartment(UUID.randomUUID());
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("không có quyền xóa phòng ban"));
    }
}
