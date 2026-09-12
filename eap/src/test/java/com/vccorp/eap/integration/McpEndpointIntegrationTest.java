package com.vccorp.eap.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
public class McpEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testMcpSseEndpoint_ReturnsOkAndSseHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/mcp/sse"))
                .andReturn();

        System.out.println("MCP SSE Status: " + result.getResponse().getStatus());
        System.out.println("MCP SSE ContentType: " + result.getResponse().getContentType());
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentType() != null &&
                result.getResponse().getContentType().contains("text/event-stream"));
    }
}
