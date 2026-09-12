package com.vccorp.eap.mcp.registry;

/**
 * Cấu trúc nhãn hiển thị trạng thái công cụ được load từ file JSON cấu hình.
 */
public record ToolLabelEntry(
        String name,
        String startLabel,
        String endLabel
) {
}
