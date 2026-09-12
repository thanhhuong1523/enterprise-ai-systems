package com.vccorp.eap.mcp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu và mô tả tham số cho một MCP Tool theo chuẩn Model Context Protocol (MCP).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolParam {

    /**
     * Tên định danh của tham số nếu muốn khác với tên biến trong Java method. Mặc định để trống sẽ lấy theo tên biến.
     */
    String name() default "";

    /**
     * Mô tả ý nghĩa, định dạng hoặc giá trị ví dụ của tham số.
     */
    String description() default "";

    /**
     * Tham số này có bắt buộc hay không. Mặc định là true.
     */
    boolean required() default true;
}
