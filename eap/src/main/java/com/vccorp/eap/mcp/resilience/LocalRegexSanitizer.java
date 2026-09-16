package com.vccorp.eap.mcp.resilience;

import org.springframework.stereotype.Component;

/**
 * Tầng 1: Xử lý Regex làm sạch nhanh cú pháp JSON trong RAM (< 5ms).
 */
@Component
public class LocalRegexSanitizer {

    public String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();

        // Tách bỏ markdown code block nếu có
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Xóa dấu phẩy thừa trước ngoặc đóng: , } -> } và , ] -> ]
        cleaned = cleaned.replaceAll(",\\s*\\}", "}");
        cleaned = cleaned.replaceAll(",\\s*\\]", "]");

        return cleaned;
    }
}
