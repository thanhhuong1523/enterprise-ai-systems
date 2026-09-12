package com.vccorp.eap.mcp.orchestrator;

import java.util.Map;

/**
 * Giao diện điều phối và thực thi công cụ (Tool Dispatcher).
 * Tuân thủ các nguyên tắc Single Responsibility, Open/Closed và Interface Injection (Dependency Inversion).
 */
public interface ToolDispatcher {

    /**
     * Thực thi công cụ theo tên định danh và tập tham số đầu vào.
     *
     * @param toolName Tên công cụ cần gọi (không phân biệt chữ hoa thường)
     * @param arguments Danh sách tham số đầu vào của công cụ
     * @return Kết quả trả về sau khi thực thi công cụ
     */
    Object executeTool(String toolName, Map<String, Object> arguments);
}
