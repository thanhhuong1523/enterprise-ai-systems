package com.vccorp.eap.infrastructure.security;

import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthenticationFilter(jwtService);
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
    void doFilter_ValidToken_SetsAuthentication() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.validateToken(token)).thenReturn(true);

        Claims claims = mock(Claims.class);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(claims.get("id", String.class)).thenReturn(userId.toString());
        when(claims.getSubject()).thenReturn("testuser");
        when(claims.get("role", String.class)).thenReturn(Role.ROLE_EMPLOYEE.name());
        when(claims.get("email", String.class)).thenReturn("test@vccorp.vn");
        when(claims.get("departmentId", String.class)).thenReturn(deptId.toString());

        when(jwtService.parseToken(token)).thenReturn(claims);

        filter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(userId, principal.getId());
        assertEquals("testuser", principal.getUsername());
        assertEquals(Role.ROLE_EMPLOYEE, principal.getRole());
        assertEquals(deptId, principal.getDepartmentId());

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_InvalidToken_DoesNotSetAuthentication() throws Exception {
        String token = "invalid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.validateToken(token)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_NoToken_DoesNotSetAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
