package com.vccorp.eap.infrastructure.security;

import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpSecurityFilterTest {

    private McpSecurityFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new McpSecurityFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_McpSseEndpoint_SetsMockAdminAuthentication() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/mcp/sse");

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication phải được tự động thiết lập cho MCP endpoint");
        assertTrue(auth.isAuthenticated());

        User user = SecurityContextHelper.getCurrentUser();
        assertNotNull(user);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000000"), user.getId());
        assertEquals("admin", user.getUsername());
        assertEquals("admin@vccorp.vn", user.getEmail());
        assertEquals(McpSecurityFilter.MOCK_ADMIN_PASSWORD_HASH, user.getPasswordHash());
        assertEquals(Role.SYSTEM_ADMIN, user.getRole());

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilter_McpMessageEndpoint_SetsMockAdminAuthentication() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/mcp/message");

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        User user = (User) auth.getPrincipal();
        assertEquals(Role.SYSTEM_ADMIN, user.getRole());

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilter_NonMcpEndpoint_DoesNotSetAuthentication() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/departments");

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Endpoint không thuộc MCP không được tự động gán Mock Admin");

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilter_McpEndpointWithExistingValidAuth_PreservesExistingAuthentication() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/mcp/message");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .username("custom_user")
                .role(Role.ROLE_EMPLOYEE)
                .build();

        UsernamePasswordAuthenticationToken existingAuth = new UsernamePasswordAuthenticationToken(
                existingUser, null, Collections.singletonList(new SimpleGrantedAuthority(Role.ROLE_EMPLOYEE.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(existingAuth, auth, "Xác thực hiện có từ JWT không được bị ghi đè");
        User user = (User) auth.getPrincipal();
        assertEquals("custom_user", user.getUsername());

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
