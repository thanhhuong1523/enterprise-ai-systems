package com.vccorp.eap.integration;

import com.vccorp.eap.enums.Role;
import com.vccorp.eap.service.auth.JwtService;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class McpEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}")
    private String mcpEndpoint;

    @Test
    void testCustomMcpEndpointConfigured() {
        assertFalse(mcpEndpoint.isBlank());
    }

    @Test
    void testMcpEndpoint_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get(mcpEndpoint))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(mcpEndpoint))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testMcpEndpoint_WithInvalidToken_Returns401() throws Exception {
        when(jwtService.validateToken("invalid-token")).thenReturn(false);

        mockMvc.perform(post(mcpEndpoint)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testMcpEndpoint_WithValidToken_Authenticates() throws Exception {
        String token = "valid-mcp-token";
        UUID userId = UUID.randomUUID();
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.parseToken(token)).thenReturn(new DefaultClaims(Map.of(
                "id", userId.toString(),
                "sub", "testuser",
                "role", Role.SYSTEM_ADMIN.name(),
                "email", "test@vccorp.vn"
        )));

        // Streamable HTTP endpoint handles request without 401
        mockMvc.perform(post(mcpEndpoint)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(not(401)));
    }

    @Test
    void testOldMcpEndpoint_WithValidToken_ReturnsNotFound_WhenEndpointIsCustomized() throws Exception {
        if ("/mcp".equals(mcpEndpoint)) {
            return; // Bỏ qua nếu đang dùng endpoint mặc định /mcp
        }

        String token = "valid-mcp-token";
        UUID userId = UUID.randomUUID();
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.parseToken(token)).thenReturn(new DefaultClaims(Map.of(
                "id", userId.toString(),
                "sub", "testuser",
                "role", Role.SYSTEM_ADMIN.name(),
                "email", "test@vccorp.vn"
        )));

        // Endpoint cũ /mcp không còn là MCP route nên sẽ trả về 404 Not Found
        mockMvc.perform(get("/mcp")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
