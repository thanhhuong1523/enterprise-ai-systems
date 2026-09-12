package com.vccorp.eap.mcp.provider;

import com.vccorp.eap.mcp.annotation.McpTool;
import com.vccorp.eap.mcp.annotation.McpToolParam;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.*;

class McpMethodToolCallbackProviderTest {

    static class SampleToolService {
        @McpTool(name = "sampleTool", description = "Mô tả công cụ mẫu")
        public String sampleMethod(
                @McpToolParam(description = "Tham số bắt buộc", required = true) String requiredParam,
                @McpToolParam(description = "Tham số tùy chọn", required = false) String optionalParam
        ) {
            return "Executed: " + requiredParam + ", " + optionalParam;
        }
    }

    @Test
    void testGetToolCallbacks_ExtractsAnnotationsAndGeneratesSchema() {
        SampleToolService service = new SampleToolService();
        McpMethodToolCallbackProvider provider = McpMethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertNotNull(callbacks);
        assertEquals(1, callbacks.length);

        ToolCallback callback = callbacks[0];
        assertEquals("sampleTool", callback.getToolDefinition().name());
        assertEquals("Mô tả công cụ mẫu", callback.getToolDefinition().description());

        String schema = callback.getToolDefinition().inputSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("\"requiredParam\""));
        assertTrue(schema.contains("\"optionalParam\""));
        assertTrue(schema.contains("\"required\":[\"requiredParam\"]"));

        String result = callback.call("{\"requiredParam\":\"hello\",\"optionalParam\":\"world\"}");
        assertEquals("\"Executed: hello, world\"", result);
    }
}
