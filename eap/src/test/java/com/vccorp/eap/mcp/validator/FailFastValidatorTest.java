package com.vccorp.eap.mcp.validator;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.department.DepartmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FailFastValidatorTest {

    private FailFastValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FailFastValidator();
    }

    @Test
    void testValidate_GetDepartmentByName_EmptyOptional_ThrowsDepartmentNotFound() {
        Map<String, Object> args = Map.of("name", "Kinh doanh Quốc tế");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateToolExecutionResult("getDepartmentByName", Optional.empty(), args));

        assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Kinh doanh Quốc tế"));
    }

    @Test
    void testValidate_GetDepartmentByName_NullResult_ThrowsDepartmentNotFound() {
        Map<String, Object> args = Map.of("name", "Ban Chiến Lược");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateToolExecutionResult("getDepartmentByName", null, args));

        assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void testValidate_GetDepartmentByName_FoundResult_Success() {
        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "HR", "Nhân sự").build();
        Map<String, Object> args = Map.of("name", "Nhân sự");

        assertDoesNotThrow(() ->
                validator.validateToolExecutionResult("getDepartmentByName", Optional.of(dept), args));
    }
}
