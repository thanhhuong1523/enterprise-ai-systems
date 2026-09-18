package com.vccorp.eap.mcp.registry;

/**
 * Thông tin hợp nhất của một công cụ (Metadata từ annotation + Labels từ JSON catalog).
 */
public record ToolInfo(
        String name,
        String description,
        String inputSchema,
        String startLabel,
        String endLabel,
        String notFoundLabel,
        String errorCode
) {
    public ToolInfo(String name, String description, String inputSchema, String startLabel, String endLabel) {
        this(name, description, inputSchema, startLabel, endLabel, null, null);
    }
}
