package com.vccorp.eap.mcp.tools;

import com.vccorp.eap.dto.user.CreateUserRequest;
import com.vccorp.eap.dto.user.UpdateUserRequest;
import com.vccorp.eap.dto.user.UserResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.user.UserService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserToolsTest {

    @Mock
    private UserService userService;

    private UserTools userTools;
    private User adminUser;

    @BeforeEach
    void setUp() {
        userTools = new UserTools(userService);
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
    void testGetUserByName_DelegatesToService() {
        UUID id = UUID.randomUUID();
        UserResponse response = UserResponse.builder(id, "hoangnv", "hoang.nv@vccorp.vn", Role.ROLE_EMPLOYEE)
                .fullName("Nguyễn Văn Hoàng")
                .phone("0912345678")
                .build();
        when(userService.getUserByName("Nguyễn Văn Hoàng")).thenReturn(response);

        UserResponse result = userTools.getUserByName("Nguyễn Văn Hoàng");

        assertNotNull(result);
        assertEquals("hoangnv", result.username());
        assertEquals("Nguyễn Văn Hoàng", result.fullName());
        verify(userService, times(1)).getUserByName("Nguyễn Văn Hoàng");
    }

    @Test
    void testCreateUser_AutoFillConfirmPassword() {
        UUID deptId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserResponse response = UserResponse.builder(userId, "namnv", "nam.nv@vccorp.vn", Role.ROLE_EMPLOYEE)
                .fullName("Nguyễn Văn Nam")
                .phone("0987654321")
                .departmentId(deptId)
                .build();
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(response);

        UserResponse result = userTools.createUser("namnv", "nam.nv@vccorp.vn", "Secret123", "Nguyễn Văn Nam", "0987654321", deptId, Role.ROLE_EMPLOYEE, null);

        assertNotNull(result);
        assertEquals("namnv", result.username());

        ArgumentCaptor<CreateUserRequest> captor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService, times(1)).createUser(captor.capture());
        CreateUserRequest captured = captor.getValue();
        assertEquals("Secret123", captured.password());
        assertEquals("Secret123", captured.confirmPassword());
        assertEquals(deptId, captured.departmentId());
    }

    @Test
    void testListUsers_DelegatesToService() {
        UserResponse user = UserResponse.builder(UUID.randomUUID(), "test", "test@vccorp.vn", Role.ROLE_EMPLOYEE)
                .fullName("Test User")
                .phone("0912345678")
                .build();
        when(userService.listUsers()).thenReturn(List.of(user));

        List<UserResponse> result = userTools.listUsers();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(userService, times(1)).listUsers();
    }

    @Test
    void testListUsersByDepartment_DelegatesToService() {
        UUID deptId = UUID.randomUUID();
        UserResponse user = UserResponse.builder(UUID.randomUUID(), "test", "test@vccorp.vn", Role.ROLE_EMPLOYEE)
                .fullName("Test User")
                .phone("0912345678")
                .build();
        when(userService.listUsersByDepartment(deptId, adminUser)).thenReturn(List.of(user));

        List<UserResponse> result = userTools.listUsersByDepartment(deptId);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(userService, times(1)).listUsersByDepartment(deptId, adminUser);
    }

    @Test
    void testUpdateUser_DelegatesToService() {
        UUID id = UUID.randomUUID();
        UserResponse response = UserResponse.builder(id, "namnv_new", "nam.new@vccorp.vn", Role.ROLE_EMPLOYEE)
                .fullName("Nguyễn Văn Nam Mới")
                .phone("0987654321")
                .build();
        when(userService.updateUser(eq(id), any(UpdateUserRequest.class))).thenReturn(response);

        UserResponse result = userTools.updateUser(id, "namnv_new", "nam.new@vccorp.vn", "Nguyễn Văn Nam Mới", "0987654321");

        assertNotNull(result);
        assertEquals("namnv_new", result.username());
        verify(userService, times(1)).updateUser(eq(id), any(UpdateUserRequest.class));
    }

    @Test
    void testDeleteUser_DelegatesToService() {
        UUID id = UUID.randomUUID();
        doNothing().when(userService).deleteUser(id);

        String result = userTools.deleteUser(id);

        assertNotNull(result);
        assertTrue(result.contains("thành công"));
        verify(userService, times(1)).deleteUser(id);
    }

    @Test
    void testToolAnnotations_PresentOnMethods() throws NoSuchMethodException {
        Method getUserByName = UserTools.class.getMethod("getUserByName", String.class);
        assertTrue(getUserByName.isAnnotationPresent(McpTool.class));
        assertEquals("getUserByName", getUserByName.getAnnotation(McpTool.class).name());

        Method createUser = UserTools.class.getMethod("createUser", String.class, String.class, String.class, String.class, String.class, UUID.class, Role.class, String.class);
        assertTrue(createUser.isAnnotationPresent(McpTool.class));
        assertEquals("createUser", createUser.getAnnotation(McpTool.class).name());

        Method listUsers = UserTools.class.getMethod("listUsers");
        assertTrue(listUsers.isAnnotationPresent(McpTool.class));
        assertEquals("listUsers", listUsers.getAnnotation(McpTool.class).name());

        Method listUsersByDepartment = UserTools.class.getMethod("listUsersByDepartment", UUID.class);
        assertTrue(listUsersByDepartment.isAnnotationPresent(McpTool.class));
        assertEquals("listUsersByDepartment", listUsersByDepartment.getAnnotation(McpTool.class).name());

        Method updateUser = UserTools.class.getMethod("updateUser", UUID.class, String.class, String.class, String.class, String.class);
        assertTrue(updateUser.isAnnotationPresent(McpTool.class));
        assertEquals("updateUser", updateUser.getAnnotation(McpTool.class).name());

        Method deleteUser = UserTools.class.getMethod("deleteUser", UUID.class);
        assertTrue(deleteUser.isAnnotationPresent(McpTool.class));
        assertEquals("deleteUser", deleteUser.getAnnotation(McpTool.class).name());
    }
}
