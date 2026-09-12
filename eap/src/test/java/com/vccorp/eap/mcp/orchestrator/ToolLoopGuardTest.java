package com.vccorp.eap.mcp.orchestrator;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolLoopGuardTest {

    private ToolLoopGuard loopGuard;

    @BeforeEach
    void setUp() {
        loopGuard = new ToolLoopGuard();
    }

    @Test
    void testValidateTurn_WithinLimit_Success() {
        assertDoesNotThrow(() -> loopGuard.validateTurn(1));
        assertDoesNotThrow(() -> loopGuard.validateTurn(5));
    }

    @Test
    void testValidateTurn_ExceedsLimit_ThrowsBusinessException() {
        BusinessException exception = assertThrows(BusinessException.class, () -> loopGuard.validateTurn(6));
        assertEquals(ErrorCode.ERR_INVALID_REQUEST, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("tối đa 5 bước"));
    }
}
