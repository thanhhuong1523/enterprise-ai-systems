package com.vccorp.eap.mcp.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.department.DepartmentResponse;
import com.vccorp.eap.mcp.registry.ToolCatalogRegistry;
import com.vccorp.eap.mcp.tools.DepartmentTools;
import com.vccorp.eap.mcp.tools.DocumentTools;
import com.vccorp.eap.mcp.tools.UserTools;
import com.vccorp.eap.service.department.DepartmentService;
import com.vccorp.eap.service.document.DocumentService;
import com.vccorp.eap.service.search.RetrievalService;
import com.vccorp.eap.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FailFastValidatorTest {

    private FailFastValidator validator;
    private ToolCatalogRegistry registry;

    @BeforeEach
    void setUp() {
        DepartmentService deptService = mock(DepartmentService.class);
        UserService userService = mock(UserService.class);
        DocumentService docService = mock(DocumentService.class);
        RetrievalService retrievalService = mock(RetrievalService.class);

        DepartmentTools departmentTools = new DepartmentTools(deptService);
        UserTools userTools = new UserTools(userService);
        DocumentTools documentTools = new DocumentTools(retrievalService, docService);

        registry = new ToolCatalogRegistry(null, List.of(departmentTools, userTools, documentTools), new ObjectMapper());
        registry.init();

        validator = new FailFastValidator(registry);
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
        assertTrue(ex.getMessage().contains("Ban Chiến Lược"));
    }

    @Test
    void testValidate_GetDepartmentByName_OptionalWithNull_ThrowsDepartmentNotFound() {
        Map<String, Object> args = Map.of("name", "Phòng R&D");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateToolExecutionResult("getDepartmentByName", Optional.ofNullable(null), args));

        assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Phòng R&D"));
    }

    @Test
    void testValidate_GetUserByName_NullResult_ThrowsUserNotFound() {
        Map<String, Object> args = Map.of("name", "hoangnv");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateToolExecutionResult("getUserByName", null, args));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("hoangnv"));
    }

    @Test
    void testValidate_GetDocumentByTitle_NullResult_ThrowsDocumentNotFound() {
        Map<String, Object> args = Map.of("title", "Quy chế lương thưởng 2026");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateToolExecutionResult("getDocumentByTitle", null, args));

        assertEquals(ErrorCode.ERR_DOCUMENT_NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Quy chế lương thưởng 2026"));
    }

    @Test
    void testValidate_GenericTool_NullResult_ThrowsSystemError() {
        Map<String, Object> args = Map.of();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validateToolExecutionResult("unknownCustomTool", null, args));

        assertEquals(ErrorCode.ERR_SYSTEM_ERROR, ex.getErrorCode());
    }

    @Test
    void testValidate_FoundResult_Success() {
        DepartmentResponse dept = DepartmentResponse.builder(UUID.randomUUID(), "HR", "Nhân sự").build();
        Map<String, Object> args = Map.of("name", "Nhân sự");

        assertDoesNotThrow(() ->
                validator.validateToolExecutionResult("getDepartmentByName", Optional.of(dept), args));
    }
}
