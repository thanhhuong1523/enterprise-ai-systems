package com.vccorp.eap.infrastructure.security;

import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Filter tự động giả lập quyền SYSTEM_ADMIN cho các kết nối tới MCP Server (/api/v1/mcp/**).
 * Đảm bảo các công cụ MCP quản trị (như listDepartments, createDepartment) có thể thực thi
 * thành công khi được gọi từ Antigravity Desktop hoặc các MCP clients bên ngoài.
 */
@Component
public class McpSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpSecurityFilter.class);

    public static final UUID MOCK_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static final String MOCK_ADMIN_USERNAME = "admin";
    public static final String MOCK_ADMIN_EMAIL = "admin@vccorp.vn";
    public static final String MOCK_ADMIN_PASSWORD_HASH = "$2a$10$JWvOb7f1lScBWmsRUrPtk..X24ZBG1DA6izRmjDEAsXkIA1E4oCju"; // BCrypt cho '123456'

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/mcp");
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

        // Nếu request chưa có Authentication hoặc là anonymousUser, tự động nạp Mock SYSTEM_ADMIN
        if (currentAuth == null || !currentAuth.isAuthenticated() || "anonymousUser".equals(currentAuth.getPrincipal())) {
            User mockAdmin = User.builder()
                    .id(MOCK_ADMIN_ID)
                    .username(MOCK_ADMIN_USERNAME)
                    .email(MOCK_ADMIN_EMAIL)
                    .passwordHash(MOCK_ADMIN_PASSWORD_HASH)
                    .role(Role.SYSTEM_ADMIN)
                    .fullName("Admin System")
                    .phone("0988888888")
                    .build();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    mockAdmin,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(Role.SYSTEM_ADMIN.name()))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("[McpSecurityFilter] Đã gán Mock SYSTEM_ADMIN cho MCP request: {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
