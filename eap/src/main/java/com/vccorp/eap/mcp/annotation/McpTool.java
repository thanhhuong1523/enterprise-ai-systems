package com.vccorp.eap.mcp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu một phương thức là MCP Tool để expose ra ngoài qua giao thức Model Context Protocol (MCP).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /**
     * Tên định danh của tool. Nếu để trống sẽ lấy theo tên phương thức Java.
     */
    String name() default "";

    /**
     * Mô tả chức năng và mục đích của tool cho LLM / MCP Client hiểu.
     */
    String description() default "";

    /**
     * Nhãn hiển thị trên giao diện SSE khi bắt đầu thực thi công cụ.
     * Hỗ trợ placeholder theo tham số đầu vào (ví dụ: "Đang tra cứu phòng ban '{{name}}'...").
     */
    String startLabel() default "";

    /**
     * Nhãn hiển thị trên giao diện SSE khi hoàn tất thực thi công cụ.
     * Hỗ trợ placeholder theo tham số đầu vào (ví dụ: "Đã tìm thấy thông tin phòng ban '{{name}}'").
     */
    String endLabel() default "";
}

