package com.vccorp.eap.mcp.resilience.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.mcp.resilience.JsonSelfCorrectionService;
import com.vccorp.eap.mcp.resilience.LocalRegexSanitizer;
import com.vccorp.eap.service.helper.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Triển khai dịch vụ tự phục hồi JSON (Tầng 3 Resilience).
 * Tuân thủ Single Responsibility & Dependency Injection:
 * - Uỷ quyền việc làm sạch cú pháp Regex cục bộ cho LocalRegexSanitizer.
 * - Nhận ObjectMapper cấu hình sẵn từ Spring container thay vì tự khởi tạo.
 */
@Service
public class JsonSelfCorrectionServiceImpl implements JsonSelfCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(JsonSelfCorrectionServiceImpl.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final LocalRegexSanitizer localRegexSanitizer;

    @Autowired
    public JsonSelfCorrectionServiceImpl(LlmClient llmClient,
                                         ObjectMapper objectMapper,
                                         LocalRegexSanitizer localRegexSanitizer) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.localRegexSanitizer = localRegexSanitizer;
    }

    public JsonSelfCorrectionServiceImpl(LlmClient llmClient) {
        this(llmClient, new ObjectMapper(), new LocalRegexSanitizer());
    }

    @Override
    public Map<String, Object> validateAndCorrect(String rawJson) {
        String cleaned = cleanLocal(rawJson);
        try {
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.warn("Local JSON cleanup failed: {}. Retrying with LLM...", e.getMessage());
            return correctWithLlm(rawJson, e.getMessage(), 1);
        }
    }

    @Override
    public String cleanLocal(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "{}";
        }
        String sanitized = localRegexSanitizer.sanitize(raw);
        return sanitized.isEmpty() ? "{}" : sanitized;
    }

    private Map<String, Object> correctWithLlm(String malformedJson, String errorMessage, int attempt) {
        if (attempt > 2) {
            log.error("Exceeded maximum LLM self-correction attempts (2). Triggering fallback.");
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST,
                    "Không thể tự sửa lỗi định dạng JSON sau 2 lần thử lại.");
        }

        String systemPrompt = "Bạn là trợ lý lập trình chuyên sửa lỗi cú pháp JSON. " +
                "Nhiệm vụ của bạn là nhận vào chuỗi JSON bị lỗi cú pháp và thông báo lỗi tương ứng, " +
                "sau đó trả về duy nhất chuỗi JSON đã được sửa hoàn chỉnh. " +
                "Tuyệt đối không giải thích gì thêm, không bọc trong thẻ ```json.";

        String userContent = String.format("Chuỗi JSON bị lỗi:\n%s\n\nThông báo lỗi:\n%s\n\nHãy sửa lại chuỗi JSON trên.",
                malformedJson, errorMessage);

        try {
            log.info("Sending self-correction request to LLM (Attempt {})...", attempt);
            String corrected = llmClient.callLlmText(userContent, systemPrompt, 5000);
            if (corrected == null || corrected.isBlank()) {
                throw new Exception("LLM returned empty or null response");
            }
            String cleaned = cleanLocal(corrected);
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.warn("LLM correction attempt {} failed: {}", attempt, e.getMessage());
            return correctWithLlm(malformedJson, e.getMessage(), attempt + 1);
        }
    }
}
