package com.vccorp.eap.mcp.orchestrator;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Bộ đếm bước & chốt chặn tối đa 5 turns để bảo vệ tài nguyên hệ thống (NFR-4, Loop Guard).
 */
@Component
public class ToolLoopGuard {

    public static final int MAX_TOOL_TURNS = 5;

    public void validateTurn(int currentTurn) {
        if (currentTurn > MAX_TOOL_TURNS) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST,
                    "Vượt quá giới hạn an toàn số lần gọi công cụ (tối đa 5 bước liên tiếp).");
        }
    }
}
