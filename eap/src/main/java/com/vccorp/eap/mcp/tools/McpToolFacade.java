package com.vccorp.eap.mcp.tools;

/**
 * Marker interface cho tất cả các AI Tool Facade.
 * Cho phép Spring Boot tự động thu thập (autowire) toàn bộ các Bean công cụ vào List&lt;McpToolFacade&gt;
 * mà không cần phải inject thủ công từng Bean khi mở rộng hệ thống lên hàng trăm công cụ.
 */
public interface McpToolFacade {
}
