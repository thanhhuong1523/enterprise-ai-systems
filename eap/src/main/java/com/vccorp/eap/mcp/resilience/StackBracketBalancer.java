package com.vccorp.eap.mcp.resilience;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tầng 2: Cân bằng Stack & Khép chuỗi JSON bị cắt cụt giữa chừng do gián đoạn token.
 */
@Component
public class StackBracketBalancer {

    public String balance(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder(json);
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\' && inString) {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{' || c == '[') {
                    stack.push(c);
                } else if (c == '}') {
                    if (!stack.isEmpty() && stack.peek() == '{') {
                        stack.pop();
                    }
                } else if (c == ']') {
                    if (!stack.isEmpty() && stack.peek() == '[') {
                        stack.pop();
                    }
                }
            }
        }

        // Nếu chuỗi ký tự kết thúc khi chưa đóng nháy kép, tự bù dấu nháy kép
        if (inString) {
            sb.append('"');
        }

        // Khép các dấu ngoặc tồn đọng trong ngăn xếp
        while (!stack.isEmpty()) {
            char open = stack.pop();
            if (open == '{') {
                sb.append('}');
            } else if (open == '[') {
                sb.append(']');
            }
        }

        return sb.toString();
    }
}
