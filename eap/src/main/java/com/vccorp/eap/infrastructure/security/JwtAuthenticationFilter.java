package com.vccorp.eap.infrastructure.security;

import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final com.vccorp.eap.repository.UserRepository userRepository;
    private final com.vccorp.eap.service.RedisService redisService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = getJwtFromRequest(request);

        if (StringUtils.hasText(token) && jwtService.validateToken(token)) {
            Claims claims = jwtService.parseToken(token);
            String idStr = claims.get("id", String.class);

            if (idStr != null) {
                try {
                    UUID userId = UUID.fromString(idStr);
                    String cacheKey = "user_exists:" + userId;
                    boolean exists = false;
                    String cachedValue = null;
                    try {
                        cachedValue = redisService.get(cacheKey);
                    } catch (Exception e) {
                        // Suppress Redis connectivity issues
                    }
                    if (cachedValue != null) {
                        exists = Boolean.parseBoolean(cachedValue);
                    } else {
                        exists = userRepository.existsById(userId);
                        try {
                            redisService.set(cacheKey, String.valueOf(exists), 300000); // 5 mins
                        } catch (Exception e) {
                            // Suppress Redis write issues
                        }
                    }
                    if (!exists) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                } catch (Exception e) {
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            String username = claims.getSubject();
            String roleStr = claims.get("role", String.class);
            String email = claims.get("email", String.class);
            String departmentIdStr = claims.get("departmentId", String.class);

            UUID id = idStr != null ? UUID.fromString(idStr) : null;
            Role role = Role.valueOf(roleStr);
            UUID departmentId = departmentIdStr != null ? UUID.fromString(departmentIdStr) : null;

            User principal = User.builder()
                    .id(id)
                    .username(username)
                    .email(email)
                    .role(role)
                    .departmentId(departmentId)
                    .build();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, Collections.singletonList(new SimpleGrantedAuthority(role.name()))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
