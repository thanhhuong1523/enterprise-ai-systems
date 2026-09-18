package com.vccorp.eap.mcp.registry;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Cấu trúc nhãn hiển thị trạng thái công cụ được load từ file JSON cấu hình.
 */
public record ToolLabelEntry(
        @JsonAlias("startLabel") String start,
        @JsonAlias("endLabel") String end,
        @JsonAlias("notFoundLabel") String notFound,
        String errorCode
) {
}
