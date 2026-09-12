package com.vccorp.eap.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.mcp.provider.McpMethodToolCallbackProvider;
import com.vccorp.eap.mcp.tools.McpToolFacade;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransport;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

/**
 * Đăng ký các AI Tool Facade vào hệ thống Spring AI MCP Server qua McpMethodToolCallbackProvider
 * và cấu hình tùy biến WebMvcSseServerTransport cho các endpoint /api/v1/mcp/**.
 */
@Configuration
public class McpToolConfig {

    /**
     * Cấu hình WebMvcSseServerTransport theo chuẩn MCP SSE Transport:
     * - SSE stream endpoint (GET): /api/v1/mcp/sse
     * - Message endpoint (POST): /api/v1/mcp/message
     */
    @Bean
    public WebMvcSseServerTransport webMvcSseServerTransport(
            ObjectMapper objectMapper,
            @Value("${spring.ai.mcp.server.sse-message-endpoint:${spring.ai.mcp.server.webmvc.message-endpoint:/api/v1/mcp/message}}") String messageEndpoint,
            @Value("${spring.ai.mcp.server.sse-endpoint:${spring.ai.mcp.server.webmvc.sse-endpoint:/api/v1/mcp/sse}}") String sseEndpoint) {
        return new WebMvcSseServerTransport(objectMapper, messageEndpoint, sseEndpoint);
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcSseServerTransport transport) {
        return transport.getRouterFunction();
    }

    @Bean
    public ToolCallbackProvider allToolCallbackProvider(
            List<McpToolFacade> toolFacades,
            ObjectMapper objectMapper) {
        return McpMethodToolCallbackProvider.builder()
                .toolObjects(toolFacades.toArray())
                .objectMapper(objectMapper)
                .build();
    }
}


