package com.vccorp.eap.mcp.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.mcp.resilience.impl.JsonSelfCorrectionServiceImpl;
import com.vccorp.eap.service.helper.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JsonSelfCorrectionServiceTest {

    @Mock
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocalRegexSanitizer localRegexSanitizer = new LocalRegexSanitizer();

    private JsonSelfCorrectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JsonSelfCorrectionServiceImpl(llmClient, objectMapper, localRegexSanitizer);
    }

    @Test
    void testCleanLocal_SanitizesMarkdownAndTrailingCommas() {
        String raw = "```json\n{\"action\": \"test\",}\n```";
        String cleaned = service.cleanLocal(raw);
        assertEquals("{\"action\": \"test\"}", cleaned);
    }

    @Test
    void testValidateAndCorrect_ValidJson_ReturnsMapDirectly() {
        String raw = "{\"action\":\"listDepartments\",\"action_input\":{}}";
        Map<String, Object> result = service.validateAndCorrect(raw);

        assertNotNull(result);
        assertEquals("listDepartments", result.get("action"));
        verifyNoInteractions(llmClient);
    }

    @Test
    void testValidateAndCorrect_MalformedJson_CallsLlmAndRecovers() {
        String malformed = "{action: 'test', unquoted: 123";
        when(llmClient.callLlmText(anyString(), anyString(), anyLong()))
                .thenReturn("{\"action\": \"test\", \"unquoted\": 123}");

        Map<String, Object> result = service.validateAndCorrect(malformed);

        assertNotNull(result);
        assertEquals("test", result.get("action"));
        assertEquals(123, result.get("unquoted"));
        verify(llmClient, times(1)).callLlmText(anyString(), anyString(), anyLong());
    }

    @Test
    void testValidateAndCorrect_LlmExceedsMaxRetries_ThrowsBusinessException() {
        String malformed = "{unrecoverable";
        when(llmClient.callLlmText(anyString(), anyString(), anyLong()))
                .thenReturn("still broken");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.validateAndCorrect(malformed));

        assertEquals(ErrorCode.ERR_INVALID_REQUEST, ex.getErrorCode());
    }
}
