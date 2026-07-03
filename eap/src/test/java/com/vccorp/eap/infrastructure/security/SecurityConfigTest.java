package com.vccorp.eap.infrastructure.security;

import com.vccorp.eap.controller.PingController;
import com.vccorp.eap.controller.UserController;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.JwtService;
import com.vccorp.eap.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, PingController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserService userService;

    @Test
    public void requestWithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Phiên đăng nhập hết hạn hoặc không hợp lệ."));
    }

    @Test
    public void requestWithInvalidToken_Returns401() throws Exception {
        when(jwtService.validateToken("invalid-token")).thenReturn(false);

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Phiên đăng nhập hết hạn hoặc không hợp lệ."));
    }

    @Test
    public void requestWithValidTokenButInsufficientRole_Returns403() throws Exception {
        String token = "employee-token";
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.get("id", String.class)).thenReturn(userId.toString());
        when(claims.get("role", String.class)).thenReturn(Role.ROLE_EMPLOYEE.name());
        when(claims.get("email", String.class)).thenReturn("employee@vccorp.vn");
        when(claims.getSubject()).thenReturn("employee_user");

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.parseToken(token)).thenReturn(claims);
        when(userRepository.existsById(userId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_FORBIDDEN_ROLE"))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện hành động này."));
    }

    @Test
    public void requestWithValidTokenAndRequiredRole_Succeeds() throws Exception {
        String token = "admin-token";
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.get("id", String.class)).thenReturn(userId.toString());
        when(claims.get("role", String.class)).thenReturn(Role.SYSTEM_ADMIN.name());
        when(claims.get("email", String.class)).thenReturn("admin@vccorp.vn");
        when(claims.getSubject()).thenReturn("admin_user");

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.parseToken(token)).thenReturn(claims);
        when(userRepository.existsById(userId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    public void requestWithInvalidIdFormat_Returns400() throws Exception {
        String token = "admin-token";
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.get("id", String.class)).thenReturn(userId.toString());
        when(claims.get("role", String.class)).thenReturn(Role.SYSTEM_ADMIN.name());
        when(claims.get("email", String.class)).thenReturn("admin@vccorp.vn");
        when(claims.getSubject()).thenReturn("admin_user");

        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.parseToken(token)).thenReturn(claims);
        when(userRepository.existsById(userId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/users/invalid-uuid")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Trường 'id' không đúng định dạng. Yêu cầu kiểu dữ liệu 'UUID'."));
    }

    @Test
    public void ping_SucceedsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").value("pong"));
    }
}
