package com.vccorp.eap.mcp.config;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filter ghi log chi tiết request/response cho MCP mà KHÔNG can thiệp
 * hay buffer dữ liệu của HttpServletResponse để tránh chặn luồng stream SSE.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpHttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpHttpLoggingFilter.class);

    @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}")
    private String mcpEndpoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!uri.startsWith(mcpEndpoint) && !uri.startsWith("/sse")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String query = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        String fullPath = uri + query;

        // Chỉ wrap request để log payload POST, KHÔNG wrap response để giữ nguyên stream
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        log.info("[MCP-REQ] >>> {} {}", method, fullPath);
        log.info("[MCP-REQ] Headers: Accept='{}', Mcp-Session-Id='{}', Authorization='{}'",
                request.getHeader("Accept"),
                request.getHeader("Mcp-Session-Id"),
                request.getHeader("Authorization") != null ? "Present" : "None");

        try {
            filterChain.doFilter(wrappedRequest, response);
        } catch (Exception e) {
            log.error("[MCP-ERR] !!! {} {} ném ngoại lệ: {}", method, fullPath, e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // In body request nếu là POST
            if ("POST".equalsIgnoreCase(method)) {
                byte[] content = wrappedRequest.getContentAsByteArray();
                if (content.length > 0) {
                    String body = new String(content, StandardCharsets.UTF_8);
                    log.info("[MCP-REQ-BODY] Payload: {}", body);
                }
            }

            // Nếu là async (SSE stream)
            if (request.isAsyncStarted()) {
                log.info("[MCP-ASYNC] Luồng SSE cho {} bắt đầu chạy async (Status: {})", fullPath, response.getStatus());

                // Chỉ với GET /mcp (luồng SSE server-to-client):
                // Tomcat cần được kích hoạt flush header + 1 comment để báo Client biết kết nối đã OPEN.
                // Tuyệt đối KHÔNG can thiệp vào POST request (như tools/list) để Spring AI tự stream dữ liệu.
                if ("GET".equalsIgnoreCase(method)) {
                    try {
                        response.getOutputStream().write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                        response.flushBuffer();
                        log.info("[MCP-ASYNC] Đã gửi initial SSE comment và flush headers thành công cho GET {}", fullPath);
                    } catch (Exception ex) {
                        log.debug("[MCP-ASYNC] Bỏ qua lỗi flush GET: {}", ex.getMessage());
                    }
                }
                try {
                    request.getAsyncContext().addListener(new AsyncListener() {
                        @Override
                        public void onComplete(AsyncEvent event) {
                            log.info("[MCP-SSE-COMPLETE] Luồng SSE cho '{}' ĐÃ HOÀN TẤT sau {} ms.", fullPath,
                                    System.currentTimeMillis() - startTime);
                        }

                        @Override
                        public void onTimeout(AsyncEvent event) {
                            log.warn("[MCP-SSE-TIMEOUT] Luồng SSE cho '{}' BỊ TIMEOUT sau {} ms!", fullPath,
                                    System.currentTimeMillis() - startTime);
                        }

                        @Override
                        public void onError(AsyncEvent event) {
                            log.error("[MCP-SSE-ERROR] Luồng SSE cho '{}' GẶP LỖI: {}", fullPath,
                                    event.getThrowable() != null ? event.getThrowable().getMessage() : "Unknown");
                        }

                        @Override
                        public void onStartAsync(AsyncEvent event) {
                        }
                    });
                } catch (Exception ignored) {
                }
            } else {
                log.info("[MCP-RES] <<< {} {} -> HTTP {} (thời gian: {} ms)", method, fullPath, response.getStatus(), duration);
            }
        }
    }
}
